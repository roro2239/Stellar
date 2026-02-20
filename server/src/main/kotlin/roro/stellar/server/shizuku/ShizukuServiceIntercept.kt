package roro.stellar.server.shizuku

import android.os.Binder
import android.os.Parcel
import android.util.Log
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuApplication
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IShizukuServiceConnection
import rikka.hidden.compat.PackageManagerApis
import roro.stellar.server.ConfigManager

class ShizukuServiceIntercept(
    private val callback: ShizukuServiceCallback
) : IShizukuService.Stub() {

    companion object {
        private const val TAG = "ShizukuServiceIntercept"

        private const val SHIZUKU_MANAGER_PERMISSION = "moe.shizuku.manager.permission.MANAGER"

        private const val PER_USER_RANGE = 100000

        private fun getAppId(uid: Int): Int = uid % PER_USER_RANGE
        private fun getUserId(uid: Int): Int = uid / PER_USER_RANGE
    }

    private val clientManager get() = callback.clientManager
    private val configManager get() = callback.configManager

    private fun isEnabled(): Boolean = configManager.isShizukuCompatEnabled()

    private fun enforceEnabled(method: String) {
        if (!isEnabled()) {
            throw SecurityException("Shizuku compat layer is disabled")
        }
    }

    private val userServiceAdapter by lazy {
        ShizukuUserServiceAdapter(callback.userServiceManager)
    }

    private val rishService by lazy { RishServiceImpl() }

    private val shizukuManagerCache = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    private inline fun <T> withClearedIdentity(block: () -> T): T {
        val id = Binder.clearCallingIdentity()
        try {
            return block()
        } finally {
            Binder.restoreCallingIdentity(id)
        }
    }

    private fun checkCallerManagerPermission(callingUid: Int): Boolean =
        getAppId(callingUid) == callback.managerAppId

    private fun isShizukuManager(uid: Int): Boolean {
        return shizukuManagerCache.getOrPut(uid) {
            val userId = getUserId(uid)
            val packages = callback.getPackagesForUid(uid)
            packages.any { packageName ->
                PackageManagerApis.getPackageInfoNoThrow(packageName, 0x00001000, userId)
                    ?.requestedPermissions?.contains(SHIZUKU_MANAGER_PERMISSION) == true
            }
        }
    }

    private fun checkPermission(uid: Int): Int {
        if (isShizukuManager(uid)) return ConfigManager.FLAG_DENIED
        return configManager.getPermissionFlag(uid, ShizukuApiConstants.PERMISSION_NAME)
    }

    private fun checkOnetimePermission(uid: Int, pid: Int): Boolean {
        if (isShizukuManager(uid)) return false
        return clientManager.findClient(uid, pid)?.onetimeMap?.get(ShizukuApiConstants.PERMISSION_NAME) == true
    }

    private fun getLastDenyTime(uid: Int, pid: Int): Long =
        clientManager.findClient(uid, pid)?.lastDenyTimeMap?.get(ShizukuApiConstants.PERMISSION_NAME) ?: 0

    private fun enforceCallingPermission(method: String) {
        enforceEnabled(method)

        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        if (callingPid == callback.servicePid) return
        if (checkCallerManagerPermission(callingUid)) return

        if (checkPermission(callingUid) == ConfigManager.FLAG_GRANTED) return

        if (checkOnetimePermission(callingUid, callingPid)) return

        throw SecurityException("Permission denied for $method")
    }

    private fun transactRemote(data: Parcel, reply: Parcel?, flags: Int) {
        enforceCallingPermission("transactRemote")

        val targetBinder = data.readStrongBinder()
        val targetCode = data.readInt()

        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        val clientRecord = clientManager.findClient(callingUid, callingPid)

        val targetFlags = if (clientRecord != null && clientRecord.apiVersion >= 13) {
            data.readInt()
        } else {
            flags
        }

        Log.d(TAG, "transactRemote: uid=$callingUid, code=$targetCode")

        val newData = Parcel.obtain()
        try {
            newData.appendFrom(data, data.dataPosition(), data.dataAvail())
            withClearedIdentity {
                targetBinder.transact(targetCode, newData, reply, targetFlags)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "transactRemote failed", e)
        } finally {
            newData.recycle()
        }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == ShizukuApiConstants.BINDER_TRANSACTION_transact) {
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR)
            transactRemote(data, reply, flags)
            return true
        }

        if (code == 14) {
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR)
            val binder = data.readStrongBinder()
            val packageName = data.readString()

            val args = android.os.Bundle().apply {
                putString(ShizukuApiConstants.AttachApplication.PACKAGE_NAME, packageName)
                putInt(ShizukuApiConstants.AttachApplication.API_VERSION, -1)
            }
            attachApplication(IShizukuApplication.Stub.asInterface(binder), args)
            reply?.writeNoException()
            return true
        }

        if (code in 30000..30002) {
            enforceCallingPermission("rish")
            return withClearedIdentity { rishService.onTransact(code, data, reply, flags) }
        }

        return super.onTransact(code, data, reply, flags)
    }

    override fun getVersion(): Int {
        enforceCallingPermission("getVersion")
        return callback.serviceVersion
    }

    override fun getUid(): Int {
        enforceCallingPermission("getUid")
        return callback.serviceUid
    }

    override fun checkPermission(permission: String?): Int {
        if (!isEnabled()) return -1

        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        val hasPermission = checkPermission(callingUid) == ConfigManager.FLAG_GRANTED ||
                checkOnetimePermission(callingUid, callingPid)

        return if (hasPermission) 0 else -1
    }

    override fun getSELinuxContext(): String? {
        enforceCallingPermission("getSELinuxContext")
        return callback.serviceSeLinuxContext ?: throw IllegalStateException("无法获取 SELinux 上下文")
    }

    override fun getSystemProperty(name: String?, defaultValue: String?): String {
        enforceCallingPermission("getSystemProperty")
        return callback.getSystemProperty(name, defaultValue)
    }

    override fun setSystemProperty(name: String?, value: String?) {
        enforceCallingPermission("setSystemProperty")
        callback.setSystemProperty(name, value)
    }

    override fun newProcess(cmd: Array<String?>?, env: Array<String?>?, dir: String?): IRemoteProcess {
        enforceCallingPermission("newProcess")
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        Log.d(TAG, "newProcess: uid=$callingUid, cmd=${cmd?.contentToString()}")
        val stellarProcess = withClearedIdentity { callback.newProcess(callingUid, callingPid, cmd, env, dir) }
        return StellarRemoteProcessAdapter(stellarProcess)
    }

    override fun addUserService(conn: IShizukuServiceConnection?, args: android.os.Bundle?): Int {
        if (conn == null || args == null) {
            throw IllegalArgumentException("conn or args is null")
        }

        enforceCallingPermission("addUserService")

        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        Log.d(TAG, "addUserService: uid=$callingUid, pid=$callingPid")

        return withClearedIdentity {
            userServiceAdapter.addUserService(conn, args, callingUid, callingPid)
        }
    }

    override fun removeUserService(conn: IShizukuServiceConnection?, args: android.os.Bundle?): Int {
        if (conn == null || args == null) {
            throw IllegalArgumentException("conn or args is null")
        }

        enforceCallingPermission("removeUserService")

        Log.d(TAG, "removeUserService: uid=${Binder.getCallingUid()}")

        return withClearedIdentity {
            userServiceAdapter.removeUserService(conn, args)
        }
    }

    override fun requestPermission(requestCode: Int) {
        if (!isEnabled()) return

        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        Log.d(TAG, "requestPermission: uid=$callingUid, pid=$callingPid, code=$requestCode")
        callback.requestPermission(callingUid, callingPid, requestCode)
    }

    override fun checkSelfPermission(): Boolean {
        if (!isEnabled()) return false

        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        if (checkPermission(callingUid) == ConfigManager.FLAG_GRANTED) return true
        return checkOnetimePermission(callingUid, callingPid)
    }

    override fun shouldShowRequestPermissionRationale(): Boolean {
        if (!isEnabled()) return false

        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        if (checkPermission(callingUid) == ConfigManager.FLAG_DENIED) return true

        val lastDenyTime = getLastDenyTime(callingUid, callingPid)
        return lastDenyTime > 0 && (System.currentTimeMillis() - lastDenyTime) <= 10000
    }

    override fun attachApplication(application: IShizukuApplication?, args: android.os.Bundle?) {
        if (application == null) return
        if (!isEnabled()) {
            Log.w(TAG, "attachApplication: Shizuku compat layer is disabled")
            return
        }

        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        val apiVersion = args?.getInt(ShizukuApiConstants.AttachApplication.API_VERSION, -1) ?: -1

        Log.d(TAG, "attachApplication: uid=$callingUid, pid=$callingPid, apiVersion=$apiVersion")

        val packages = callback.getPackagesForUid(callingUid)
        val packageName = args?.getString(ShizukuApiConstants.AttachApplication.PACKAGE_NAME)
            ?: packages.firstOrNull()
            ?: "unknown"

        clientManager.attachShizukuApplication(callingUid, callingPid, application, packageName, apiVersion)

        val hasPermission = checkPermission(callingUid) == ConfigManager.FLAG_GRANTED ||
                checkOnetimePermission(callingUid, callingPid)

        val replyServerVersion = if (apiVersion == -1) 12 else ShizukuApiConstants.SERVER_VERSION

        val reply = android.os.Bundle().apply {
            putInt(ShizukuApiConstants.BindApplication.SERVER_UID, callback.serviceUid)
            putInt(ShizukuApiConstants.BindApplication.SERVER_VERSION, replyServerVersion)
            putInt(ShizukuApiConstants.BindApplication.SERVER_PATCH_VERSION, ShizukuApiConstants.SERVER_PATCH_VERSION)
            putString(ShizukuApiConstants.BindApplication.SERVER_SECONTEXT, callback.serviceSeLinuxContext)
            putBoolean(ShizukuApiConstants.BindApplication.PERMISSION_GRANTED, hasPermission)
            putBoolean(ShizukuApiConstants.BindApplication.SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, false)
        }

        try {
            application.bindApplication(reply)
            Log.i(TAG, "bindApplication 成功: uid=$callingUid, pid=$callingPid, granted=$hasPermission")
        } catch (e: Throwable) {
            Log.w(TAG, "bindApplication 失败", e)
        }
    }

    override fun exit() {
        throw SecurityException("exit() not allowed")
    }

    override fun attachUserService(binder: android.os.IBinder?, options: android.os.Bundle?) {
        if (binder == null || options == null) {
            Log.w(TAG, "attachUserService: binder or options is null")
            return
        }

        val token = options.getString(ShizukuApiConstants.UserServiceArgs.TOKEN)
        if (token != null) {
            Log.d(TAG, "attachUserService: token=$token")
            withClearedIdentity {
                userServiceAdapter.onStellarServiceAttached(token, binder)
            }
        }
    }

    override fun dispatchPackageChanged(intent: android.content.Intent?) {
    }

    override fun isHidden(uid: Int): Boolean = false

    override fun dispatchPermissionConfirmationResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: android.os.Bundle?
    ) {
    }

    override fun getFlagsForUid(uid: Int, mask: Int): Int {
        if (!isEnabled()) return 0
        return ShizukuApiConstants.stellarToShizukuFlag(checkPermission(uid))
    }

    override fun updateFlagsForUid(uid: Int, mask: Int, value: Int) {
        if (!isEnabled()) return
        val stellarFlag = ShizukuApiConstants.shizukuToStellarFlag(value)
        configManager.updatePermission(uid, ShizukuApiConstants.PERMISSION_NAME, stellarFlag)
        clientManager.findClients(uid).forEach { it.onetimeMap.remove(ShizukuApiConstants.PERMISSION_NAME) }
    }

    fun notifyPermissionResult(uid: Int, pid: Int, requestCode: Int, allowed: Boolean) {
        val record = clientManager.findClient(uid, pid)
        if (record?.shizukuApplication == null) {
            Log.w(TAG, "notifyPermissionResult: 未找到 uid=$uid, pid=$pid 的客户端")
            return
        }
        record.dispatchShizukuPermissionResult(
            requestCode,
            allowed,
            callback.serviceUid,
            callback.serviceVersion,
            callback.serviceSeLinuxContext
        )
    }
}
