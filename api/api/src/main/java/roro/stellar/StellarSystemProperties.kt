package roro.stellar

import android.os.RemoteException

/**
 * Android系统属性访问工具类
 * Android System Properties Access Utility
 *
 *
 * 功能说明 Features：
 *
 *  * 通过Stellar服务访问系统属性 - Access system properties via Stellar service
 *  * 支持读取和设置系统属性 - Supports reading and setting system properties
 *  * 提供类型转换方法（int, long, boolean） - Provides type conversion methods
 *
 *
 *
 * 使用场景 Use Cases：
 *
 *  * 读取系统配置信息 - Read system configuration
 *  * 修改调试属性（ADB模式） - Modify debug properties (ADB mode)
 *  * 修改系统属性（Root模式） - Modify system properties (Root mode)
 *  * 调试和开发工具 - Debug and development tools
 *
 *
 *
 * 写入权限说明 Write Permission：
 *
 *  * **ADB 模式 (uid=2000)：**可写入 debug.*, persist.debug.*, log.*, vendor.debug.* 等调试属性
 *  * **Root 模式 (uid=0)：**可写入大部分系统属性（ro.* 只读属性除外）
 *  * **普通应用：**无写入权限
 *
 *
 *
 * 注意事项 Notes：
 *
 *  * 需要Stellar服务运行 - Requires Stellar service running
 *  * 读取属性无需特殊权限 - Reading properties requires no special permission
 *  * 某些属性值变化可能需要重启才能生效 - Some property changes may require reboot to take effect
 *
 *
 * @since 从版本9开始添加 Added from version 9
 */
object StellarSystemProperties {
    /**
     * 获取系统属性值
     * Get system property value
     *
     * @param key 属性键名 Property key
     * @return 属性值，如果不存在则返回null Property value, or null if not exists
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    fun get(key: String?): String {
        return Stellar.requireService().getSystemProperty(key, null)
    }

    /**
     * 获取系统属性值，支持默认值
     * Get system property value with default
     *
     * @param key 属性键名 Property key
     * @param def 默认值 Default value
     * @return 属性值，如果不存在则返回默认值 Property value, or default if not exists
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    fun get(key: String?, def: String?): String {
        return Stellar.requireService().getSystemProperty(key, def)
    }

    /**
     * 获取整数类型的系统属性
     * Get integer system property
     *
     * @param key 属性键名 Property key
     * @param def 默认值 Default value
     * @return 属性值（整数） Property value as integer
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    fun getInt(key: String?, def: Int): Int {
        return Stellar.requireService().getSystemProperty(key, def.toString()).toInt()
    }

    /**
     * 获取长整数类型的系统属性
     * Get long system property
     *
     * @param key 属性键名 Property key
     * @param def 默认值 Default value
     * @return 属性值（长整数） Property value as long
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    fun getLong(key: String?, def: Long): Long {
        return Stellar.requireService().getSystemProperty(key, def.toString()).toLong()
    }

    /**
     * 获取布尔类型的系统属性
     * Get boolean system property
     *
     * @param key 属性键名 Property key
     * @param def 默认值 Default value
     * @return 属性值（布尔） Property value as boolean
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    fun getBoolean(key: String?, def: Boolean): Boolean {
        return Stellar.requireService().getSystemProperty(key, def.toString()).toBoolean()
    }

    /**
     * 设置系统属性
     * Set system property
     *
     *
     * 权限要求 Permission Requirements：
     *
     *  * **ADB 模式：**可设置 debug.*, persist.debug.*, log.*, vendor.debug.* 等属性
     *  * **Root 模式：**可设置大部分属性（ro.* 只读属性除外）
     *
     *
     *
     * 示例 Examples：
     * <pre>
     * // ADB 模式下可用 (Available in ADB mode)
     * set("debug.myapp.feature", "enabled");
     * set("persist.debug.level", "verbose");
     * set("log.tag.MyTag", "DEBUG");
     *
     * // 需要 Root 模式 (Requires root mode)
     * set("persist.sys.locale", "zh_CN");
     * set("sys.usb.config", "adb");
    </pre> *
     *
     * @param key 属性键名 Property key
     * @param value 属性值 Property value
     * @throws RemoteException 远程调用异常
     * @throws IllegalStateException 如果权限不足或属性为只读
     */
    @Throws(RemoteException::class)
    fun set(key: String?, value: String?) {
        Stellar.requireService().setSystemProperty(key, value)
    }
}