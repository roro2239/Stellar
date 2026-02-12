package roro.stellar.server.shizuku

import android.os.Binder
import android.util.Log
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuApplication
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IShizukuServiceConnection
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
     * 检查 Shizuku 权限（持久权限）
     */
    private fun checkPermission(uid: Int): Int {
        return configManager.getPermissionFlag(uid, ShizukuApiConstants.PERMISSION_NAME)
    }

    /**
     * 检查一次性权限
     */
    private fun checkOnetimePermission(uid: Int, pid: Int): Boolean {
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

    override fun getVersion(): Int {
        enforceCallingPermission("getVersion")
        return withClearedIdentity { callback.stellarService.version }
    }

    override fun getUid(): Int {
        enforceCallingPermission("getUid")
        return withClearedIdentity { callback.stellarService.uid }
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
        return withClearedIdentity {
            callback.stellarService.seLinuxContext ?: throw IllegalStateException("无法获取 SELinux 上下文")
        }
    }

    override fun getSystemProperty(name: String?, defaultValue: String?): String {
        enforceCallingPermission("getSystemProperty")
        return withClearedIdentity { callback.stellarService.getSystemProperty(name, defaultValue) }
    }

    override fun setSystemProperty(name: String?, value: String?) {
        enforceCallingPermission("setSystemProperty")
        withClearedIdentity { callback.stellarService.setSystemProperty(name, value) }
    }

    override fun newProcess(cmd: Array<String?>?, env: Array<String?>?, dir: String?): IRemoteProcess {
        enforceCallingPermission("newProcess")
        Log.d(TAG, "newProcess: uid=${Binder.getCallingUid()}, cmd=${cmd?.contentToString()}")
        val stellarProcess = withClearedIdentity { callback.stellarService.newProcess(cmd, env, dir) }
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
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        Log.d(TAG, "attachApplication: uid=$callingUid, pid=$callingPid")

        if (application != null) {
            val packages = callback.getPackagesForUid(callingUid)
            val packageName = packages.firstOrNull() ?: "unknown"
            clientManager.attachShizukuApplication(callingUid, callingPid, application, packageName)
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
        // 暂不需要处理
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
        record.dispatchShizukuPermissionResult(requestCode, allowed)
    }
}
