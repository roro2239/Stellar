package roro.stellar.shizuku.server

/**
 * Shizuku 服务回调接口
 * 用于解耦 ShizukuServiceIntercept 与 StellarService
 */
interface ShizukuServiceCallback {
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

    /**
     * 获取管理器应用 ID
     */
    fun getManagerAppId(): Int

    /**
     * 获取服务 UID
     */
    fun getServiceUid(): Int

    /**
     * 获取服务 PID
     */
    fun getServicePid(): Int

    /**
     * 获取 SELinux 上下文
     */
    fun getSELinuxContext(): String?
}
