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

/**
 * Shizuku 服务拦截器
 * 将 Shizuku API 调用转发到 Stellar 服务
 * 复用 Stellar 的权限管理和客户端管理
 */
class ShizukuServiceIntercept(
    private val callback: ShizukuServiceCallback
) : IShizukuService.Stub() {

    companion object {
        private const val TAG = "ShizukuServiceIntercept"
        // Shizuku Manager 特征权限 (signature 级别，只有 Manager 会请求)
        private const val SHIZUKU_MANAGER_PERMISSION = "moe.shizuku.manager.permission.MANAGER"
    }

    private val clientManager get() = callback.clientManager
    private val configManager get() = callback.configManager

    // Shizuku 用户服务适配器
    private val userServiceAdapter by lazy {
        ShizukuUserServiceAdapter(callback.userServiceManager)
    }

    private inline fun <T> withClearedIdentity(block: () -> T): T {
        val id = Binder.clearCallingIdentity()
        try {
            return block()
        } finally {
            Binder.restoreCallingIdentity(id)
        }
    }

    private fun getAppId(uid: Int): Int = uid % 100000

    private fun checkCallerManagerPermission(callingUid: Int): Boolean {
        return getAppId(callingUid) == callback.managerAppId
    }

    /**
     * 检查 UID 是否属于 Shizuku 管理器
     * Shizuku 管理器不应获得 Shizuku 权限
     */
    private fun isShizukuManager(uid: Int): Boolean {
        val userId = uid / 100000
        val packages = callback.getPackagesForUid(uid)
        for (packageName in packages) {
            val pi = PackageManagerApis.getPackageInfoNoThrow(packageName, 0x00001000, userId) // GET_PERMISSIONS
            if (pi?.requestedPermissions?.contains(SHIZUKU_MANAGER_PERMISSION) == true) {
                return true
            }
        }
        return false
    }

    /**
     * 检查 Shizuku 权限（持久权限）
     * Shizuku 管理器始终返回 DENIED
     */
    private fun checkPermission(uid: Int): Int {
        if (isShizukuManager(uid)) return ConfigManager.FLAG_DENIED
        return configManager.getPermissionFlag(uid, ShizukuApiConstants.PERMISSION_NAME)
    }

    /**
     * 检查一次性权限
     * Shizuku 管理器始终返回 false
     */
    private fun checkOnetimePermission(uid: Int, pid: Int): Boolean {
        if (isShizukuManager(uid)) return false
        return clientManager.findClient(uid, pid)?.onetimeMap?.get(ShizukuApiConstants.PERMISSION_NAME) ?: false
    }

    /**
     * 获取上次拒绝时间
     */
    private fun getLastDenyTime(uid: Int, pid: Int): Long {
        return clientManager.findClient(uid, pid)?.lastDenyTimeMap?.get(ShizukuApiConstants.PERMISSION_NAME) ?: 0
    }

    /**
     * 检查调用者是否有权限
     */
    private fun enforceCallingPermission(method: String) {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        if (callingPid == callback.servicePid) return
        if (checkCallerManagerPermission(callingUid)) return

        // 检查持久权限
        if (checkPermission(callingUid) == ConfigManager.FLAG_GRANTED) return

        // 检查一次性权限
        if (checkOnetimePermission(callingUid, callingPid)) return

        throw SecurityException("Permission denied for $method")
    }

    /**
     * transactRemote - Shizuku 核心功能
     * 允许客户端通过 Shizuku 代理调用任意系统 Binder
     */
    private fun transactRemote(data: Parcel, reply: Parcel?, flags: Int) {
        enforceCallingPermission("transactRemote")

        val targetBinder = data.readStrongBinder()
        val targetCode = data.readInt()

        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        val clientRecord = clientManager.findClient(callingUid, callingPid)

        // API >= 13 会传递 targetFlags
        val targetFlags = if (clientRecord != null && clientRecord.apiVersion >= 13) {
            data.readInt()
        } else {
            flags
        }

        Log.d(TAG, "transactRemote: uid=$callingUid, code=$targetCode")

        val newData = Parcel.obtain()
        try {
            newData.appendFrom(data, data.dataPosition(), data.dataAvail())
        } catch (e: Throwable) {
            Log.w(TAG, "transactRemote appendFrom failed", e)
            newData.recycle()
            return
        }

        try {
            withClearedIdentity {
                targetBinder.transact(targetCode, newData, reply, targetFlags)
            }
        } finally {
            newData.recycle()
        }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        // transactRemote (Transaction Code = 1)
        if (code == ShizukuApiConstants.BINDER_TRANSACTION_transact) {
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR)
            transactRemote(data, reply, flags)
            return true
        }

        // 旧版 attachApplication (Transaction Code = 14, API <= v12)
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
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        Log.d(TAG, "requestPermission: uid=$callingUid, pid=$callingPid, code=$requestCode")
        callback.requestPermission(callingUid, callingPid, requestCode)
    }

    override fun checkSelfPermission(): Boolean {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        if (checkPermission(callingUid) == ConfigManager.FLAG_GRANTED) return true
        return checkOnetimePermission(callingUid, callingPid)
    }

    override fun shouldShowRequestPermissionRationale(): Boolean {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        if (checkPermission(callingUid) == ConfigManager.FLAG_DENIED) return true

        val lastDenyTime = getLastDenyTime(callingUid, callingPid)
        return lastDenyTime > 0 && (System.currentTimeMillis() - lastDenyTime) <= 10000
    }

    override fun attachApplication(application: IShizukuApplication?, args: android.os.Bundle?) {
        if (application == null) return

        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        val apiVersion = args?.getInt(ShizukuApiConstants.AttachApplication.API_VERSION, -1) ?: -1

        Log.d(TAG, "attachApplication: uid=$callingUid, pid=$callingPid, apiVersion=$apiVersion")

        val packages = callback.getPackagesForUid(callingUid)
        val packageName = args?.getString(ShizukuApiConstants.AttachApplication.PACKAGE_NAME)
            ?: packages.firstOrNull()
            ?: "unknown"

        clientManager.attachShizukuApplication(callingUid, callingPid, application, packageName, apiVersion)

        // 检查权限状态
        val hasPermission = checkPermission(callingUid) == ConfigManager.FLAG_GRANTED ||
                checkOnetimePermission(callingUid, callingPid)

        // 兼容旧版客户端 (API <= v12)
        val replyServerVersion = if (apiVersion == -1) 12 else ShizukuApiConstants.SERVER_VERSION

        // 构建回复 Bundle
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
        // Sui only
    }

    override fun isHidden(uid: Int): Boolean = false

    override fun dispatchPermissionConfirmationResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: android.os.Bundle?
    ) {
        // 权限结果通过 StellarService.dispatchPermissionConfirmationResult 统一处理
    }

    override fun getFlagsForUid(uid: Int, mask: Int): Int {
        return ShizukuApiConstants.stellarToShizukuFlag(checkPermission(uid))
    }

    override fun updateFlagsForUid(uid: Int, mask: Int, value: Int) {
        val stellarFlag = ShizukuApiConstants.shizukuToStellarFlag(value)
        configManager.updatePermission(uid, ShizukuApiConstants.PERMISSION_NAME, stellarFlag)
        // 清除一次性权限
        clientManager.findClients(uid).forEach { it.onetimeMap.remove(ShizukuApiConstants.PERMISSION_NAME) }
    }

    /**
     * 通知客户端权限结果
     */
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
