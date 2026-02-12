package roro.stellar.server.shizuku

import android.os.Binder
import android.util.Log
import com.stellar.server.IStellarService
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuApplication
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IShizukuServiceConnection
import roro.stellar.server.ConfigManager

/**
 * Shizuku 服务拦截器
 * 将 Shizuku API 调用转发到 Stellar 服务
 * 复用 Stellar 的权限管理逻辑
 */
class ShizukuServiceIntercept(
    private val callback: ShizukuServiceCallback
) : IShizukuService.Stub() {

    companion object {
        private const val TAG = "ShizukuServiceIntercept"
    }

    // 保存客户端应用的 IShizukuApplication 引用，用于权限结果回调
    private val applications = mutableMapOf<Int, MutableMap<Int, IShizukuApplication>>()

    private val stellarService: IStellarService
        get() = callback.getStellarService()

    private val managerAppId: Int
        get() = callback.getManagerAppId()

    private inline fun <T> withClearedIdentity(block: () -> T): T {
        val id = Binder.clearCallingIdentity()
        try {
            return block()
        } finally {
            Binder.restoreCallingIdentity(id)
        }
    }

    private fun getAppId(uid: Int): Int = uid % 100000
    private fun getUserId(uid: Int): Int = uid / 100000

    private fun checkCallerManagerPermission(callingUid: Int): Boolean {
        return getAppId(callingUid) == managerAppId
    }

    /**
     * 检查调用者是否有权限
     * 复用 Stellar 的权限逻辑：持久权限 + 一次性权限
     */
    private fun enforceCallingPermission(method: String) {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        if (callingPid == callback.getServicePid()) {
            return
        }

        if (checkCallerManagerPermission(callingUid)) {
            return
        }

        // 检查持久权限
        val permission = callback.checkPermission(callingUid)
        if (permission == ConfigManager.FLAG_GRANTED) {
            return
        }

        // 检查一次性权限
        if (callback.checkOnetimePermission(callingUid, callingPid)) {
            return
        }

        throw SecurityException("Permission denied for $method")
    }

    override fun getVersion(): Int {
        enforceCallingPermission("getVersion")
        return withClearedIdentity { stellarService.version }
    }

    override fun getUid(): Int {
        enforceCallingPermission("getUid")
        return withClearedIdentity { stellarService.uid }
    }

    override fun checkPermission(permission: String?): Int {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        val stellarPermission = callback.checkPermission(callingUid)

        // 检查持久权限或一次性权限
        val hasPermission = stellarPermission == ConfigManager.FLAG_GRANTED ||
                callback.checkOnetimePermission(callingUid, callingPid)

        // 返回 Shizuku 权限状态：0=GRANTED, -1=DENIED
        return if (hasPermission) 0 else -1
    }

    override fun getSELinuxContext(): String? {
        enforceCallingPermission("getSELinuxContext")
        return withClearedIdentity {
            stellarService.seLinuxContext ?: throw IllegalStateException("无法获取 SELinux 上下文")
        }
    }

    override fun getSystemProperty(name: String?, defaultValue: String?): String {
        enforceCallingPermission("getSystemProperty")
        return withClearedIdentity { stellarService.getSystemProperty(name, defaultValue) }
    }

    override fun setSystemProperty(name: String?, value: String?) {
        enforceCallingPermission("setSystemProperty")
        withClearedIdentity { stellarService.setSystemProperty(name, value) }
    }

    override fun newProcess(cmd: Array<String?>?, env: Array<String?>?, dir: String?): IRemoteProcess {
        enforceCallingPermission("newProcess")
        Log.d(TAG, "newProcess: uid=${Binder.getCallingUid()}, cmd=${cmd?.contentToString()}")
        val stellarProcess = withClearedIdentity { stellarService.newProcess(cmd, env, dir) }
        return StellarRemoteProcessAdapter(stellarProcess)
    }

    override fun addUserService(conn: IShizukuServiceConnection?, args: android.os.Bundle?): Int {
        throw UnsupportedOperationException("User service not supported")
    }

    override fun removeUserService(conn: IShizukuServiceConnection?, args: android.os.Bundle?): Int {
        throw UnsupportedOperationException("User service not supported")
    }

    override fun requestPermission(requestCode: Int) {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        Log.d(TAG, "requestPermission: uid=$callingUid, pid=$callingPid, code=$requestCode")

        // 复用 Stellar 的权限请求流程
        callback.requestPermission(callingUid, callingPid, requestCode)
    }

    override fun checkSelfPermission(): Boolean {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        // 检查持久权限
        val permission = callback.checkPermission(callingUid)
        if (permission == ConfigManager.FLAG_GRANTED) {
            return true
        }

        // 检查一次性权限
        return callback.checkOnetimePermission(callingUid, callingPid)
    }

    override fun shouldShowRequestPermissionRationale(): Boolean {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        val permission = callback.checkPermission(callingUid)

        // 和 Stellar 一样：被拒绝过才显示 rationale
        // 这样用户可以选择"拒绝且不再询问"
        if (permission == ConfigManager.FLAG_DENIED) {
            return true
        }

        // 检查是否在短时间内被拒绝过（10秒内）
        val lastDenyTime = callback.getLastDenyTime(callingUid, callingPid)
        return lastDenyTime > 0 && (System.currentTimeMillis() - lastDenyTime) <= 10000
    }

    override fun attachApplication(application: IShizukuApplication?, args: android.os.Bundle?) {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        Log.d(TAG, "attachApplication: uid=$callingUid, pid=$callingPid")

        if (application != null) {
            synchronized(applications) {
                val pidMap = applications.getOrPut(callingUid) { mutableMapOf() }
                pidMap[callingPid] = application
            }
            Log.d(TAG, "保存客户端应用引用: uid=$callingUid, pid=$callingPid")
        }
    }

    override fun exit() {
        throw SecurityException("exit() not allowed")
    }

    override fun attachUserService(binder: android.os.IBinder?, options: android.os.Bundle?) {
        // 暂不支持用户服务
    }

    override fun dispatchPackageChanged(intent: android.content.Intent?) {
        // 暂不需要处理
    }

    override fun isHidden(uid: Int): Boolean {
        return false
    }

    override fun dispatchPermissionConfirmationResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: android.os.Bundle?
    ) {
        // 这个方法由管理器调用，不需要在这里处理
        // 权限结果通过 StellarService.dispatchPermissionConfirmationResult 统一处理
    }

    override fun getFlagsForUid(uid: Int, mask: Int): Int {
        val stellarFlag = callback.checkPermission(uid)
        return ShizukuApiConstants.stellarToShizukuFlag(stellarFlag)
    }

    override fun updateFlagsForUid(uid: Int, mask: Int, value: Int) {
        val stellarFlag = ShizukuApiConstants.shizukuToStellarFlag(value)
        callback.updatePermission(uid, stellarFlag)
    }

    /**
     * 通知客户端应用权限授权结果
     */
    fun notifyPermissionResult(uid: Int, pid: Int, requestCode: Int, allowed: Boolean) {
        val application = synchronized(applications) {
            applications[uid]?.get(pid)
        }

        if (application == null) {
            Log.w(TAG, "notifyPermissionResult: 未找到 uid=$uid, pid=$pid 的客户端应用")
            return
        }

        try {
            val data = android.os.Bundle()
            data.putBoolean("moe.shizuku.privileged.api.intent.extra.ALLOWED", allowed)
            application.dispatchRequestPermissionResult(requestCode, data)
            Log.i(TAG, "已通知客户端权限结果: uid=$uid, pid=$pid, code=$requestCode, allowed=$allowed")
        } catch (e: Exception) {
            Log.e(TAG, "通知客户端权限结果失败: uid=$uid, pid=$pid", e)
        }
    }

    /**
     * 清理断开连接的客户端
     */
    fun removeClient(uid: Int, pid: Int) {
        synchronized(applications) {
            applications[uid]?.remove(pid)
            if (applications[uid]?.isEmpty() == true) {
                applications.remove(uid)
            }
        }
    }
}
