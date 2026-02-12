package roro.stellar.server.shizuku

import com.stellar.server.IStellarService

/**
 * Shizuku 服务回调接口
 * 用于 ShizukuServiceIntercept 与 StellarService 通信
 */
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
     * 检查 Shizuku 权限 (返回 Stellar 标志: ASK=0, GRANTED=1, DENIED=2)
     */
    fun checkPermission(uid: Int): Int

    /**
     * 检查是否有一次性权限
     */
    fun checkOnetimePermission(uid: Int, pid: Int): Boolean

    /**
     * 更新 Shizuku 权限 (使用 Stellar 标志)
     */
    fun updatePermission(uid: Int, flag: Int)

    /**
     * 请求权限 (复用 Stellar 权限请求流程)
     */
    fun requestPermission(uid: Int, pid: Int, requestCode: Int)

    /**
     * 分发权限结果给客户端
     */
    fun dispatchPermissionResult(uid: Int, pid: Int, requestCode: Int, allowed: Boolean, onetime: Boolean)

    /**
     * 记录拒绝时间 (用于判断是否显示"不再询问"选项)
     */
    fun recordDenyTime(uid: Int, pid: Int)

    /**
     * 获取上次拒绝时间
     */
    fun getLastDenyTime(uid: Int, pid: Int): Long
}
