package roro.stellar.manager.model

import roro.stellar.Stellar

/**
 * 服务状态数据类
 * Service Status Data Class
 * 
 * 功能说明 Features：
 * - 封装Stellar服务的运行状态信息 - Encapsulates Stellar service runtime status
 * - 包含UID、版本、SELinux上下文等信息 - Contains UID, version, SELinux context, etc.
 * - 提供服务运行状态判断 - Provides service running status check
 * 
 * @property uid 服务进程UID（-1表示未运行）
 * @property apiVersion API版本号
 * @property patchVersion 补丁版本号
 * @property seContext SELinux上下文
 * @property permission 是否有权限
 */
data class ServiceStatus(
        val uid: Int = -1,
        val apiVersion: Int = -1,
        val patchVersion: Int = -1,
        val seContext: String? = null,
        val permission: Boolean = false
) {
    /**
     * 服务是否正在运行
     * Whether service is running
     * 
     * 通过检查UID和Binder状态判断
     * Determined by checking UID and Binder status
     */
    val isRunning: Boolean
        get() = uid != -1 && Stellar.pingBinder()
}

