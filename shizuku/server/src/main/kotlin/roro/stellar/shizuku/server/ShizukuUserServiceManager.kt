package roro.stellar.shizuku.server

import android.content.ComponentName
import android.content.pm.PackageInfo
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.ArrayMap
import android.util.Log
import moe.shizuku.server.IShizukuServiceConnection
import java.io.File
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class ShizukuUserServiceManager(
    private val managerApkPathProvider: () -> String,
    private val packageInfoProvider: (packageName: String, flags: Long, userId: Int) -> PackageInfo?
) {
    companion object {
        private const val TAG = "ShizukuUserServiceMgr"
        private const val TIMEOUT_MILLIS = 30_000L

        private const val USER_SERVICE_CMD_FORMAT =
            "(CLASSPATH='%s' %s%s /system/bin " +
            "--nice-name='%s' moe.shizuku.starter.ServiceStarter " +
            "--token='%s' --package='%s' --class='%s' --uid=%d%s)&"

        private const val DEBUG_ARGS =
            "-Xcompiler-option --debuggable " +
            "-XjdwpProvider:adbconnection " +
            "-XjdwpOptions:suspend=n,server=y"
    }

    private val executor: Executor = Executors.newSingleThreadExecutor()
    private val userServiceRecords = Collections.synchronizedMap(
        ArrayMap<String, ShizukuUserServiceRecord>()
    )
    private val packageUserServiceRecords = Collections.synchronizedMap(
        ArrayMap<String, MutableList<ShizukuUserServiceRecord>>()
    )

    private fun getAppId(uid: Int): Int = uid % 100000
    private fun getUserId(uid: Int): Int = uid / 100000

    private fun ensureCallingPackageForUserService(
        packageName: String,
        appId: Int,
        userId: Int
    ): PackageInfo {
        val packageInfo = packageInfoProvider(packageName, 0x00002000, userId)
        if (packageInfo?.applicationInfo == null) {
            throw SecurityException("unable to find package $packageName")
        }
        if (getAppId(packageInfo.applicationInfo!!.uid) != appId) {
            throw SecurityException("package $packageName is not owned by $appId")
        }
        return packageInfo
    }

    fun removeUserService(conn: IShizukuServiceConnection, options: Bundle): Int {
        val componentName = options.getParcelable<ComponentName>(
            ShizukuApiConstants.USER_SERVICE_ARG_COMPONENT
        ) ?: return 1

        val uid = Binder.getCallingUid()
        val appId = getAppId(uid)
        val userId = getUserId(uid)

        val packageName = componentName.packageName
        ensureCallingPackageForUserService(packageName, appId, userId)

        val className = componentName.className ?: return 1
        val tag = options.getString(ShizukuApiConstants.USER_SERVICE_ARG_TAG)
        val key = "$packageName:${tag ?: className}"

        val remove = if (options.containsKey(ShizukuApiConstants.USER_SERVICE_ARG_REMOVE)) {
            options.getBoolean(ShizukuApiConstants.USER_SERVICE_ARG_REMOVE)
        } else true

        synchronized(this) {
            val record = userServiceRecords[key] ?: return 1
            if (remove) {
                removeUserServiceLocked(record)
            } else {
                record.callbacks.unregister(conn)
            }
        }
        return 0
    }

    private fun removeUserServiceLocked(record: ShizukuUserServiceRecord) {
        if (userServiceRecords.values.remove(record)) {
            record.destroy()
            Log.i(TAG, "Removed user service record: ${record.token}")
        }
    }

    fun addUserService(
        conn: IShizukuServiceConnection,
        options: Bundle,
        callingApiVersion: Int
    ): Int {
        val uid = Binder.getCallingUid()
        val appId = getAppId(uid)
        val userId = getUserId(uid)

        val componentName = options.getParcelable<ComponentName>(
            ShizukuApiConstants.USER_SERVICE_ARG_COMPONENT
        ) ?: throw IllegalArgumentException("component is null")

        val packageName = componentName.packageName
            ?: throw IllegalArgumentException("package is null")
        val packageInfo = ensureCallingPackageForUserService(packageName, appId, userId)

        val className = componentName.className
            ?: throw IllegalArgumentException("class is null")
        val sourceDir = packageInfo.applicationInfo?.sourceDir
            ?: throw IllegalArgumentException("apk path is null")

        val versionCode = options.getInt(ShizukuApiConstants.USER_SERVICE_ARG_VERSION_CODE, 1)
        val tag = options.getString(ShizukuApiConstants.USER_SERVICE_ARG_TAG)
        val processNameSuffix = options.getString(ShizukuApiConstants.USER_SERVICE_ARG_PROCESS_NAME)
        val debug = options.getBoolean(ShizukuApiConstants.USER_SERVICE_ARG_DEBUGGABLE, false)
        val noCreate = options.getBoolean(ShizukuApiConstants.USER_SERVICE_ARG_NO_CREATE, false)
        val daemon = options.getBoolean(ShizukuApiConstants.USER_SERVICE_ARG_DAEMON, true)
        val use32Bits = options.getBoolean(
            ShizukuApiConstants.USER_SERVICE_ARG_USE_32_BIT_APP_PROCESS, false
        )
        val key = "$packageName:${tag ?: className}"

        synchronized(this) {
            val record = userServiceRecords[key]
            return handleAddUserService(
                conn, record, key, versionCode, daemon, noCreate,
                packageInfo, className, processNameSuffix, uid,
                use32Bits, debug, callingApiVersion
            )
        }
    }

    private fun handleAddUserService(
        conn: IShizukuServiceConnection,
        existingRecord: ShizukuUserServiceRecord?,
        key: String,
        versionCode: Int,
        daemon: Boolean,
        noCreate: Boolean,
        packageInfo: PackageInfo,
        className: String,
        processNameSuffix: String?,
        callingUid: Int,
        use32Bits: Boolean,
        debug: Boolean,
        callingApiVersion: Int
    ): Int {
        if (noCreate) {
            return handleNoCreate(conn, existingRecord, callingApiVersion)
        }

        val newRecord = createUserServiceRecordIfNeeded(
            existingRecord, key, versionCode, daemon, packageInfo
        )
        newRecord.callbacks.register(conn)

        if (newRecord.service != null && newRecord.service!!.pingBinder()) {
            newRecord.broadcastBinderReceived()
        } else if (!newRecord.starting) {
            newRecord.setStartingTimeout(TIMEOUT_MILLIS)

            val runnable = Runnable {
                startUserService(
                    newRecord, key, newRecord.token,
                    packageInfo.packageName, className,
                    processNameSuffix, callingUid, use32Bits, debug
                )
            }
            executor.execute(runnable)
        }
        return 0
    }

    private fun handleNoCreate(
        conn: IShizukuServiceConnection,
        record: ShizukuUserServiceRecord?,
        callingApiVersion: Int
    ): Int {
        if (record != null) {
            record.callbacks.register(conn)
            if (record.service != null && record.service!!.pingBinder()) {
                record.broadcastBinderReceived()
                return if (callingApiVersion >= 13) record.versionCode else 0
            }
        }
        return if (callingApiVersion >= 13) -1 else 1
    }

    private fun createUserServiceRecordIfNeeded(
        record: ShizukuUserServiceRecord?,
        key: String,
        versionCode: Int,
        daemon: Boolean,
        packageInfo: PackageInfo
    ): ShizukuUserServiceRecord {
        if (record != null) {
            if (record.versionCode != versionCode) {
                Log.v(TAG, "Remove record $key (${record.token}) - version mismatch")
            } else if (!record.starting && (record.service == null || !record.service!!.pingBinder())) {
                Log.v(TAG, "Service in record $key (${record.token}) is dead")
            } else {
                Log.i(TAG, "Found existing service record $key (${record.token})")
                if (record.daemon != daemon) {
                    record.setDaemon(daemon)
                }
                return record
            }
            removeUserServiceLocked(record)
        }

        val newRecord = object : ShizukuUserServiceRecord(versionCode, daemon) {
            override fun removeSelf() {
                synchronized(this@ShizukuUserServiceManager) {
                    removeUserServiceLocked(this)
                }
            }
        }

        val packageName = packageInfo.packageName
        val list = packageUserServiceRecords.getOrPut(packageName) {
            Collections.synchronizedList(mutableListOf())
        }
        list.add(newRecord)

        userServiceRecords[key] = newRecord
        Log.i(TAG, "New service record $key (${newRecord.token})")
        return newRecord
    }

    private fun startUserService(
        record: ShizukuUserServiceRecord,
        key: String,
        token: String,
        packageName: String,
        className: String,
        processNameSuffix: String?,
        callingUid: Int,
        use32Bits: Boolean,
        debug: Boolean
    ) {
        Log.v(TAG, "Starting process for service record $key ($token)...")

        val cmd = getUserServiceStartCmd(
            token, packageName, className,
            processNameSuffix ?: "p", callingUid, use32Bits, debug
        )

        try {
            val process = Runtime.getRuntime().exec("sh")
            val os: OutputStream = process.outputStream
            os.write(cmd.toByteArray())
            os.flush()
            os.close()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw IllegalStateException("sh exited with $exitCode")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start user service", e)
            throw IllegalStateException(e.message)
        }
    }

    private fun getUserServiceStartCmd(
        token: String,
        packageName: String,
        className: String,
        processNameSuffix: String,
        callingUid: Int,
        use32Bits: Boolean,
        debug: Boolean
    ): String {
        val appProcess = if (use32Bits && File("/system/bin/app_process32").exists()) {
            "/system/bin/app_process32"
        } else {
            "/system/bin/app_process"
        }

        val managerApkPath = managerApkPathProvider()
        val processName = "$packageName:$processNameSuffix"
        val debugArgs = if (debug) " $DEBUG_ARGS" else ""
        val debugName = if (debug) " --debug-name=$processName" else ""

        return String.format(
            java.util.Locale.ENGLISH,
            USER_SERVICE_CMD_FORMAT,
            managerApkPath, appProcess, debugArgs,
            processName, token, packageName, className, callingUid, debugName
        )
    }

    fun attachUserService(binder: IBinder, options: Bundle) {
        val token = options.getString(ShizukuApiConstants.USER_SERVICE_ARG_TOKEN)
            ?: throw IllegalArgumentException("token is null")

        synchronized(this) {
            val record = userServiceRecords.values.find { it.token == token }
                ?: throw IllegalArgumentException("unable to find token $token")

            Log.v(TAG, "Received binder for service record $token")
            record.setBinder(binder)
        }
    }

    fun removeUserServicesForPackage(packageName: String) {
        val list = packageUserServiceRecords[packageName]?.toList() ?: return
        for (record in list) {
            record.removeSelf()
            Log.i(TAG, "Remove user service ${record.token} for package $packageName")
        }
        packageUserServiceRecords.remove(packageName)
    }
}