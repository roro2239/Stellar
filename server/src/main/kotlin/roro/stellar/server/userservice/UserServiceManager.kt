package roro.stellar.server.userservice

import android.os.Bundle
import android.os.IBinder
import com.stellar.server.IUserServiceCallback
import rikka.hidden.compat.PackageManagerApis
import roro.stellar.server.ApkChangedObservers
import roro.stellar.server.ServerConstants
import roro.stellar.server.util.Logger
import roro.stellar.server.util.UserHandleCompat
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class UserServiceManager {
    companion object {
        private val LOGGER = Logger("UserServiceManager")
    }

    private val servicesByToken = ConcurrentHashMap<String, UserServiceRecord>()

    private val servicesByPackage = ConcurrentHashMap<String, MutableList<UserServiceRecord>>()

    private val apkListeners = ConcurrentHashMap<UserServiceRecord, () -> Unit>()

    fun startUserService(
        callingUid: Int,
        callingPid: Int,
        args: Bundle,
        callback: IUserServiceCallback?
    ): String? {
        val packageName = args.getString(UserServiceConstants.ARG_PACKAGE_NAME)
        val className = args.getString(UserServiceConstants.ARG_CLASS_NAME)
        val processNameSuffix = args.getString(UserServiceConstants.ARG_PROCESS_NAME_SUFFIX)
            ?: "userservice"
        val debug = args.getBoolean(UserServiceConstants.ARG_DEBUG, false)
        val use32Bit = args.getBoolean(UserServiceConstants.ARG_USE_32_BIT, false)
        val versionCode = args.getLong(UserServiceConstants.ARG_VERSION_CODE, 0)
        val serviceMode = args.getInt(UserServiceConstants.ARG_SERVICE_MODE, UserServiceConstants.MODE_ONE_TIME)
        val useStandaloneDex = args.getBoolean(UserServiceConstants.ARG_USE_STANDALONE_DEX, false)
        val verificationToken = args.getString(UserServiceConstants.ARG_VERIFICATION_TOKEN) ?: ""
        val userId = UserHandleCompat.getUserId(callingUid)

        if (packageName.isNullOrEmpty() || className.isNullOrEmpty()) {
            LOGGER.w("参数无效: packageName=%s, className=%s", packageName, className)
            notifyStartFailed(callback, UserServiceConstants.ERROR_INVALID_ARGS,
                "Invalid arguments: packageName or className is empty")
            return null
        }

        val packageInfo = PackageManagerApis.getPackageInfoNoThrow(packageName, 0, userId)
        if (packageInfo == null) {
            LOGGER.w("包未找到: %s", packageName)
            notifyStartFailed(callback, UserServiceConstants.ERROR_PACKAGE_NOT_FOUND,
                "Package not found: $packageName")
            return null
        }

        val applicationInfo = packageInfo.applicationInfo
        if (applicationInfo == null) {
            LOGGER.w("应用信息为空: %s", packageName)
            notifyStartFailed(callback, UserServiceConstants.ERROR_PACKAGE_NOT_FOUND,
                "Application info is null: $packageName")
            return null
        }

        val apkPath = applicationInfo.sourceDir

        val token = UUID.randomUUID().toString()

        val key = "$packageName:$className:$processNameSuffix"
        val existingRecord = findRecordByKey(key, callingUid)
        if (existingRecord != null) {
            if (existingRecord.versionCode == versionCode && existingRecord.isConnected) {
                LOGGER.i("复用已存在的服务: %s", key)
                try {
                    callback?.onServiceConnected(existingRecord.serviceBinder!!, existingRecord.verificationToken)
                } catch (e: Exception) {
                    LOGGER.w(e, "通知复用服务失败")
                }
                return existingRecord.token
            } else {
                LOGGER.i("停止旧服务以升级: %s", key)
                stopUserService(existingRecord.token)
            }
        }

        val record = UserServiceRecord(
            token = token,
            callingUid = callingUid,
            callingPid = callingPid,
            packageName = packageName,
            className = className,
            processNameSuffix = processNameSuffix,
            versionCode = versionCode,
            serviceMode = serviceMode,
            verificationToken = verificationToken,
            callback = callback,
            onRemove = { removeRecord(it) }
        )

        servicesByToken[token] = record
        servicesByPackage.getOrPut(packageName) {
            Collections.synchronizedList(mutableListOf())
        }.add(record)

        setupApkObserver(record, apkPath)

        val cmd = generateStartCommand(record, apkPath, debug, use32Bit, useStandaloneDex)
        LOGGER.i("启动 UserService: %s", record.className)

        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            record.started = true
        } catch (e: Exception) {
            LOGGER.e(e, "启动 UserService 进程失败")
            removeRecord(record)
            notifyStartFailed(callback, UserServiceConstants.ERROR_PROCESS_START_FAILED,
                "Failed to start process: ${e.message}")
            return null
        }

        return token
    }

    fun stopUserService(token: String): Boolean {
        val record = servicesByToken[token] ?: return false

        LOGGER.i("停止 UserService: %s", record.className)
        record.removeSelf()
        return true
    }

    fun attachUserService(binder: IBinder, options: Bundle): Boolean {
        val token = options.getString(UserServiceConstants.OPT_TOKEN)
        if (token == null) {
            LOGGER.w("attachUserService: 缺少 token")
            return false
        }

        val record = servicesByToken[token]
        if (record == null) {
            LOGGER.w("未找到 token 对应的记录: %s (未授权的启动)", token)
            return false
        }

        val pid = options.getInt(UserServiceConstants.OPT_PID, -1)
        LOGGER.i("UserService 已附加: token=%s, package=%s, pid=%d",
            token, record.packageName, pid)

        record.onServiceAttached(binder, pid)
        return true
    }

    fun getUserServiceCount(callingUid: Int): Int {
        return servicesByToken.values.count { it.callingUid == callingUid }
    }

    fun removeUserServicesForPackage(packageName: String) {
        val records = servicesByPackage[packageName]?.toList() ?: return
        for (record in records) {
            record.removeSelf()
        }
    }

    fun onClientDisconnected(callingUid: Int, callingPid: Int) {
        LOGGER.i("客户端断开连接: uid=%d, pid=%d", callingUid, callingPid)

        val recordsToRemove = servicesByToken.values.filter { record ->
            record.callingUid == callingUid &&
            record.serviceMode == UserServiceConstants.MODE_ONE_TIME
        }

        for (record in recordsToRemove) {
            LOGGER.i("停止一次性服务: package=%s, class=%s",
                record.packageName, record.className)
            record.removeSelf()
        }
    }

    fun getDaemonServices(packageName: String, callingUid: Int): List<UserServiceRecord> {
        return servicesByToken.values.filter { record ->
            record.packageName == packageName &&
            record.callingUid == callingUid &&
            record.serviceMode == UserServiceConstants.MODE_DAEMON &&
            record.isConnected
        }
    }

    private fun findRecordByKey(key: String, callingUid: Int): UserServiceRecord? {
        return servicesByToken.values.find {
            it.getKey() == key && it.callingUid == callingUid
        }
    }

    private fun removeRecord(record: UserServiceRecord) {
        servicesByToken.remove(record.token)
        servicesByPackage[record.packageName]?.remove(record)

        apkListeners.remove(record)?.let { listener ->
        }
    }

    private fun setupApkObserver(record: UserServiceRecord, apkPath: String) {
        val listener: () -> Unit = {
            val userId = UserHandleCompat.getUserId(record.callingUid)
            val newPi = PackageManagerApis.getPackageInfoNoThrow(record.packageName, 0, userId)

            if (newPi == null) {
                LOGGER.i("包已移除，停止服务: %s", record.packageName)
                record.removeSelf()
            } else {
                LOGGER.i("包已更新，服务将在下次请求时重启")
            }
        }

        apkListeners[record] = listener
        ApkChangedObservers.start(apkPath, listener)
    }

    private fun generateStartCommand(
        record: UserServiceRecord,
        apkPath: String,
        debug: Boolean,
        use32Bit: Boolean,
        useStandaloneDex: Boolean
    ): String {
        val appProcess = if (use32Bit && File("/system/bin/app_process32").exists()) {
            "/system/bin/app_process32"
        } else {
            "/system/bin/app_process"
        }

        val managerApkPath = PackageManagerApis.getApplicationInfoNoThrow(
            ServerConstants.MANAGER_APPLICATION_ID, 0, 0
        )?.sourceDir ?: ""

        val processName = "${record.packageName}:${record.processNameSuffix}"
        val debugArgs = if (debug) getDebugArgs() else ""
        val debugName = if (debug) " --debug-name=$processName" else ""

        return String.format(
            Locale.ENGLISH,
            UserServiceConstants.USER_SERVICE_CMD_FORMAT,
            managerApkPath,
            appProcess,
            debugArgs,
            processName,
            record.token,
            record.packageName,
            record.className,
            record.callingUid,
            record.serviceMode,
            useStandaloneDex,
            record.verificationToken,
            debugName
        )
    }

    private fun getDebugArgs(): String {
        return " -Xcompiler-option --debuggable" +
               " -XjdwpProvider:adbconnection" +
               " -XjdwpOptions:suspend=n,server=y"
    }

    private fun notifyStartFailed(callback: IUserServiceCallback?, errorCode: Int, message: String) {
        try {
            callback?.onServiceStartFailed(errorCode, message)
        } catch (e: Exception) {
            LOGGER.w(e, "通知启动失败失败")
        }
    }
}
