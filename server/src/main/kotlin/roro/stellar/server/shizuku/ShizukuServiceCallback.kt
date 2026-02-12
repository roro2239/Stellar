package roro.stellar.server.shizuku

import com.stellar.server.IStellarService
import roro.stellar.server.ClientManager
import roro.stellar.server.ConfigManager

/**
 * Shizuku 服务回调接口
 * 简化版：提供必要的服务引用
 */
interface ShizukuServiceCallback {
    val stellarService: IStellarService
    val clientManager: ClientManager
    val configManager: ConfigManager
    val managerAppId: Int
    val servicePid: Int

    /**
     * 获取指定 UID 的包名列表
     */
    fun getPackagesForUid(uid: Int): List<String>

    /**
     * 请求权限 (复用 Stellar 权限请求流程)
     */
    fun requestPermission(uid: Int, pid: Int, requestCode: Int)
}
