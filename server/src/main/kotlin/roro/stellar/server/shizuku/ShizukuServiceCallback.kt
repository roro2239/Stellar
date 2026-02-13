package roro.stellar.server.shizuku

import roro.stellar.server.ClientManager
import roro.stellar.server.ConfigManager
import roro.stellar.server.userservice.UserServiceManager

/**
 * Shizuku 服务回调接口
 * 提供必要的服务引用，直接获取服务信息（绕过 AIDL 权限检查）
 */
interface ShizukuServiceCallback {
    // 直接获取服务信息（不通过 AIDL，避免权限检查）
    val serviceUid: Int
    val serviceVersion: Int
    val serviceSeLinuxContext: String?

    val clientManager: ClientManager
    val configManager: ConfigManager
    val userServiceManager: UserServiceManager
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

    /**
     * 获取系统属性
     */
    fun getSystemProperty(name: String?, defaultValue: String?): String

    /**
     * 设置系统属性
     */
    fun setSystemProperty(name: String?, value: String?)

    /**
     * 创建新进程
     */
    fun newProcess(uid: Int, pid: Int, cmd: Array<String?>?, env: Array<String?>?, dir: String?): com.stellar.server.IRemoteProcess
}
