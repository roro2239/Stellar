package roro.stellar.shizuku.server

import android.os.Binder
import android.util.Log
import com.stellar.server.IStellarService
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuApplication
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IShizukuServiceConnection

/**
 * Shizuku 服务拦截器
 * 将 Shizuku API 调用转发到 Stellar 服务
 */
class ShizukuServiceIntercept(
    private val callback: ShizukuServiceCallback
) : IShizukuService.Stub() {

    companion object {
        private const val TAG = "ShizukuServiceIntercept"
    }

    // 保存客户端应用的 IShizukuApplication 引用，用于权限结果回调
    private val applications = mutableMapOf<Int, IShizukuApplication>()

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

    private fun enforceCallingPermission(method: String) {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()

        if (callingPid == callback.getServicePid()) {
            return
        }

        if (checkCallerManagerPermission(callingUid)) {
            return
        }

        val permission = callback.checkShizukuPermission(callingUid)
        if (permission != ShizukuApiConstants.FLAG_GRANTED) {
            throw SecurityException("Permission denied for $method")
        }
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
        // Shizuku 的 checkPermission 返回权限状态，不需要 enforceCallingPermission
        val callingUid = Binder.getCallingUid()
        val shizukuPermission = callback.checkShizukuPermission(callingUid)

        // 返回 Shizuku 权限状态：0=GRANTED, -1=DENIED
        return if (shizukuPermission == ShizukuApiConstants.FLAG_GRANTED) {
            0  // PERMISSION_GRANTED
        } else {
            -1  // PERMISSION_DENIED
        }
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
        // 暂不支持用户服务
        throw UnsupportedOperationException("User service not supported")
    }

    override fun removeUserService(conn: IShizukuServiceConnection?, args: android.os.Bundle?): Int {
        // 暂不支持用户服务
        throw UnsupportedOperationException("User service not supported")
    }

    override fun requestPermission(requestCode: Int) {
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        val userId = getUserId(callingUid)
        val packages = callback.getPackagesForUid(callingUid)
        val packageName = packages.firstOrNull() ?: "unknown"

        Log.d(TAG, "requestPermission: uid=$callingUid, pid=$callingPid, pkg=$packageName, code=$requestCode")
        callback.showPermissionConfirmation(requestCode, callingUid, callingPid, userId, packageName)
    }

    override fun checkSelfPermission(): Boolean {
        val callingUid = Binder.getCallingUid()
        val permission = callback.checkShizukuPermission(callingUid)
        return permission == ShizukuApiConstants.FLAG_GRANTED
    }

    override fun shouldShowRequestPermissionRationale(): Boolean {
        val callingUid = Binder.getCallingUid()
        val permission = callback.checkShizukuPermission(callingUid)
        return permission == ShizukuApiConstants.FLAG_DENIED
    }

    override fun attachApplication(application: IShizukuApplication?, args: android.os.Bundle?) {
        val callingUid = Binder.getCallingUid()
        Log.d(TAG, "attachApplication: uid=$callingUid")

        if (application != null) {
            applications[callingUid] = application
            Log.d(TAG, "保存客户端应用引用: uid=$callingUid")
        }
    }

    override fun exit() {
        // 不允许客户端退出服务
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
        val allowed = data?.getBoolean("moe.shizuku.privileged.api.intent.extra.ALLOWED", false) ?: false
        callback.dispatchPermissionResult(requestUid, requestCode, allowed)
    }

    override fun getFlagsForUid(uid: Int, mask: Int): Int {
        return callback.checkShizukuPermission(uid)
    }

    override fun updateFlagsForUid(uid: Int, mask: Int, value: Int) {
        callback.updateShizukuPermission(uid, value)
    }

    /**
     * 通知客户端应用权限授权结果
     */
    fun notifyPermissionResult(uid: Int, requestCode: Int, allowed: Boolean) {
        val application = applications[uid]
        if (application == null) {
            Log.w(TAG, "notifyPermissionResult: 未找到 uid $uid 的客户端应用")
            return
        }

        try {
            val data = android.os.Bundle()
            data.putBoolean("moe.shizuku.privileged.api.intent.extra.ALLOWED", allowed)
            application.dispatchRequestPermissionResult(requestCode, data)
            Log.i(TAG, "已通知客户端权限结果: uid=$uid, code=$requestCode, allowed=$allowed")
        } catch (e: Exception) {
            Log.e(TAG, "通知客户端权限结果失败: uid=$uid", e)
        }
    }
}
