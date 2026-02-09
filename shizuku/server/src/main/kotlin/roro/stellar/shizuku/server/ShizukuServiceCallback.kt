package roro.stellar.shizuku.server

import com.stellar.server.IStellarService

interface ShizukuServiceCallback {
    /**
     * 获取 Stellar 服务实例
     */
    fun getStellarService(): IStellarService

    /**
     * 获取管理器应用 ID
     */
    fun getManagerAppId(): Int

    /**
     * 获取服务 PID
     */
    fun getServicePid(): Int

    /**
     * 获取指定 UID 的包名列表
     */
    fun getPackagesForUid(uid: Int): List<String>

    /**
     * 检查 Shizuku 权限
     * @return FLAG_ASK, FLAG_GRANTED, FLAG_DENIED
     */
    fun checkShizukuPermission(uid: Int): Int

    /**
     * 更新 Shizuku 权限
     */
    fun updateShizukuPermission(uid: Int, flag: Int)

    /**
     * 显示权限确认对话框
     */
    fun showPermissionConfirmation(
        requestCode: Int,
        uid: Int,
        pid: Int,
        userId: Int,
        packageName: String
    )

    fun dispatchPermissionResult(uid: Int, requestCode: Int, allowed: Boolean)
}
