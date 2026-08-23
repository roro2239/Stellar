package roro.stellar.server.userservice

import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.stellar.server.IUserServiceCallback
import roro.stellar.server.ApkChangedObservers
import roro.stellar.server.ServerConstants
import roro.stellar.server.util.Logger
import roro.stellar.server.util.PackageManagerCompat
import roro.stellar.server.util.UserHandleCompat
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class UserServiceManager {
    companion object {
        private val LOGGER = Logger("UserServiceManager")
        private const val SAFE_APK_DIR = "/data/local/tmp/stellar"
        private const val SAFE_APK_PATH = "$SAFE_APK_DIR/manager_safe.apk"
        private const val LEGACY_SAFE_APK_PATH = "/data/local/tmp/stellar_manager_safe.apk"
        private const val DIRECTORY_MODE = 0x1C0 // 0700
        private const val FILE_MODE = 0x100 // 0400
        private const val PERMISSION_MASK = 0x1FF // 0777
        private val safeApkLock = Any()
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
        val verificationToken = args.getString(UserServiceConstants.ARG_VERIFICATION_TOKEN) ?: ""
        val userId = UserHandleCompat.getUserId(callingUid)

        if (packageName.isNullOrEmpty() || className.isNullOrEmpty()) {
            LOGGER.w("参数无效: packageName=%s, className=%s", packageName, className)
            notifyStartFailed(callback, UserServiceConstants.ERROR_INVALID_ARGS,
                "Invalid arguments: packageName or className is empty")
            return null
        }

        val packageInfo = PackageManagerCompat.getPackageInfo(packageName, 0, userId)
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
                existingRecord.removeSelf(silent = true)
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

        val cmd = generateStartCommand(record, apkPath, debug, use32Bit)
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

    fun getUserServiceCount(callingUid: Int): Int =
        servicesByToken.values.count { it.callingUid == callingUid }

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

    private fun findRecordByKey(key: String, callingUid: Int): UserServiceRecord? =
        servicesByToken.values.find { it.getKey() == key && it.callingUid == callingUid }

    private fun removeRecord(record: UserServiceRecord) {
        servicesByToken.remove(record.token)
        servicesByPackage[record.packageName]?.remove(record)
        apkListeners.remove(record)
    }

    private fun setupApkObserver(record: UserServiceRecord, apkPath: String) {
        val listener: () -> Unit = {
            val userId = UserHandleCompat.getUserId(record.callingUid)
            val newPi = PackageManagerCompat.getPackageInfo(record.packageName, 0, userId)

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
        use32Bit: Boolean
    ): String {
        val appProcess = if (use32Bit && File("/system/bin/app_process32").exists()) {
            "/system/bin/app_process32"
        } else {
            "/system/bin/app_process"
        }

        val managerApkPath = prepareSafeManagerApk(PackageManagerCompat.getApplicationInfo(
            ServerConstants.MANAGER_APPLICATION_ID, 0, 0
        )?.sourceDir ?: "")

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
            record.verificationToken,
            debugName
        )
    }

    private fun prepareSafeManagerApk(sourcePath: String): String = synchronized(safeApkLock) {
        if (sourcePath.isBlank()) return@synchronized sourcePath

        try {
            val sourceFile = File(sourcePath)
            checkSafePath(sourceFile, directory = false, expectedMode = null, checkOwner = false)

            val safeDir = File(SAFE_APK_DIR)
            if (lstatOrNull(safeDir) == null) {
                check(safeDir.mkdirs()) { "无法创建安全 APK 目录" }
            } else {
                checkSafePath(safeDir, directory = true, expectedMode = null)
            }
            Os.chmod(safeDir.absolutePath, DIRECTORY_MODE)
            checkSafePath(safeDir, directory = true, expectedMode = DIRECTORY_MODE)

            deleteOwnedFile(File(LEGACY_SAFE_APK_PATH))

            val destFile = File(SAFE_APK_PATH)
            if (lstatOrNull(destFile) != null) {
                checkSafePath(destFile, directory = false, expectedMode = null)
            }

            val needsUpdate = lstatOrNull(destFile) == null ||
                destFile.length() != sourceFile.length() ||
                destFile.lastModified() != sourceFile.lastModified()
            if (needsUpdate) {
                val sourceDigest = sha256(sourceFile)
                val tempFile = File(safeDir, ".manager_safe.apk.tmp-${Process.myPid()}")
                try {
                    if (lstatOrNull(tempFile) != null) {
                        checkSafePath(tempFile, directory = false, expectedMode = null)
                        check(tempFile.delete()) { "无法删除旧临时 APK" }
                    }
                    FileOutputStream(tempFile).use { output ->
                        sourceFile.inputStream().use { it.copyTo(output) }
                        output.fd.sync()
                    }
                    check(tempFile.setLastModified(sourceFile.lastModified())) {
                        "无法同步临时 APK 修改时间"
                    }
                    Os.chmod(tempFile.absolutePath, FILE_MODE)
                    checkSafePath(tempFile, directory = false, expectedMode = FILE_MODE)
                    check(MessageDigest.isEqual(sourceDigest, sha256(tempFile))) {
                        "临时 APK SHA-256 校验失败"
                    }
                    Os.rename(tempFile.absolutePath, destFile.absolutePath)
                } finally {
                    deleteOwnedFile(tempFile)
                }
            }

            Os.chmod(destFile.absolutePath, FILE_MODE)
            checkSafePath(destFile, directory = false, expectedMode = FILE_MODE)
            destFile.absolutePath
        } catch (e: Throwable) {
            LOGGER.e(e, "准备安全 APK 失败，回退到原路径")
            sourcePath
        }
    }

    fun cleanupManagerApkCache() = synchronized(safeApkLock) {
        deleteOwnedFile(File(LEGACY_SAFE_APK_PATH))
        val safeDir = File(SAFE_APK_DIR)
        val stat = lstatOrNull(safeDir)
        if (stat != null && !OsConstants.S_ISLNK(stat.st_mode) &&
            OsConstants.S_ISDIR(stat.st_mode) && stat.st_uid == Process.myUid()) {
            deleteOwnedFile(File(SAFE_APK_PATH))
            safeDir.listFiles()
                ?.filter { it.name.startsWith(".manager_safe.apk.tmp-") }
                ?.forEach(::deleteOwnedFile)
            safeDir.delete()
        }
    }

    private fun checkSafePath(
        file: File,
        directory: Boolean,
        expectedMode: Int?,
        checkOwner: Boolean = true
    ) {
        val stat = lstatOrNull(file) ?: error("路径不存在: ${file.absolutePath}")
        check(!OsConstants.S_ISLNK(stat.st_mode)) { "拒绝符号链接: ${file.absolutePath}" }
        check(if (directory) OsConstants.S_ISDIR(stat.st_mode) else OsConstants.S_ISREG(stat.st_mode)) {
            "路径类型错误: ${file.absolutePath}"
        }
        if (checkOwner) check(stat.st_uid == Process.myUid()) { "路径所有者不匹配: ${file.absolutePath}" }
        if (expectedMode != null) {
            check(stat.st_mode and PERMISSION_MASK == expectedMode) { "路径权限不安全: ${file.absolutePath}" }
        }
    }

    private fun deleteOwnedFile(file: File) {
        val stat = lstatOrNull(file) ?: return
        if (stat.st_uid == Process.myUid() &&
            (OsConstants.S_ISREG(stat.st_mode) || OsConstants.S_ISLNK(stat.st_mode))) {
            file.delete()
        }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    private fun lstatOrNull(file: File) = try {
        Os.lstat(file.absolutePath)
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) null else throw e
    }

    private fun getDebugArgs(): String =
        " -Xcompiler-option --debuggable" +
        " -XjdwpProvider:adbconnection" +
        " -XjdwpOptions:suspend=n,server=y"

    private fun notifyStartFailed(callback: IUserServiceCallback?, errorCode: Int, message: String) {
        try {
            callback?.onServiceStartFailed(errorCode, message)
        } catch (e: Exception) {
            LOGGER.w(e, "通知启动失败失败")
        }
    }
}
