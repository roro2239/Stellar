package roro.stellar.server

import roro.stellar.server.util.Logger

/**
 * 配置管理器抽象类
 * Configuration Manager Abstract Class
 *
 *
 * 功能说明 Features：
 *
 *  * 管理客户端应用的权限配置 - Manages permission configuration for client apps
 *  * 提供配置的查询、更新和删除接口 - Provides query, update and delete interfaces for configuration
 *  * 支持按UID管理多个包的权限 - Supports managing permissions for multiple packages by UID
 *
 *
 *
 * 权限标志 Permission Flags：
 *
 *  * FLAG_ALLOWED - 已授权标志
 *  * FLAG_DENIED - 已拒绝标志
 *  * MASK_PERMISSION - 权限掩码（包含允许和拒绝）
 *
 */
abstract class ConfigManager {
    /**
     * 查找指定UID的配置条目
     * Find configuration entry for specified UID
     *
     * @param uid 要查找的UID
     * @return 配置条目，如果不存在则返回null
     */
    abstract fun find(uid: Int): ConfigPackageEntry?

    /**
     * 更新指定UID的配置
     * Update configuration for specified UID
     *
     * @param uid UID
     * @param packages 包名列表
     * @param mask 要更新的标志位掩码
     * @param values 标志位的新值
     */
    abstract fun update(uid: Int, packages: MutableList<String?>?, mask: Int, values: Int)

    /**
     * 移除指定UID的配置
     * Remove configuration for specified UID
     *
     * @param uid 要移除的UID
     */
    abstract fun remove(uid: Int)

    companion object {
        @JvmStatic
        protected val LOGGER: Logger = Logger("ConfigManager")

        /** 权限标志：已授权 Permission flag: allowed  */
        const val FLAG_ALLOWED: Int = 1 shl 1

        /** 权限标志：已拒绝 Permission flag: denied  */
        const val FLAG_DENIED: Int = 1 shl 2

        /** 权限掩码：包含允许和拒绝 Permission mask: includes allowed and denied  */
        const val MASK_PERMISSION: Int = FLAG_ALLOWED or FLAG_DENIED
    }
}