package roro.stellar;

import android.os.RemoteException;

/**
 * Android系统属性访问工具类
 * Android System Properties Access Utility
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>通过Stellar服务访问系统属性 - Access system properties via Stellar service</li>
 * <li>支持读取和设置系统属性 - Supports reading and setting system properties</li>
 * <li>提供类型转换方法（int, long, boolean） - Provides type conversion methods</li>
 * </ul>
 * 
 * <p>使用场景 Use Cases：</p>
 * <ul>
 * <li>读取系统配置信息 - Read system configuration</li>
 * <li>修改调试属性（ADB模式） - Modify debug properties (ADB mode)</li>
 * <li>修改系统属性（Root模式） - Modify system properties (Root mode)</li>
 * <li>调试和开发工具 - Debug and development tools</li>
 * </ul>
 * 
 * <p>写入权限说明 Write Permission：</p>
 * <ul>
 * <li><b>ADB 模式 (uid=2000)：</b>可写入 debug.*, persist.debug.*, log.*, vendor.debug.* 等调试属性</li>
 * <li><b>Root 模式 (uid=0)：</b>可写入大部分系统属性（ro.* 只读属性除外）</li>
 * <li><b>普通应用：</b>无写入权限</li>
 * </ul>
 * 
 * <p>注意事项 Notes：</p>
 * <ul>
 * <li>需要Stellar服务运行 - Requires Stellar service running</li>
 * <li>读取属性无需特殊权限 - Reading properties requires no special permission</li>
 * <li>某些属性值变化可能需要重启才能生效 - Some property changes may require reboot to take effect</li>
 * </ul>
 * 
 * @since 从版本9开始添加 Added from version 9
 */
public class StellarSystemProperties {

    /**
     * 获取系统属性值
     * Get system property value
     * 
     * @param key 属性键名 Property key
     * @return 属性值，如果不存在则返回null Property value, or null if not exists
     * @throws RemoteException 远程调用异常
     */
    public static String get(String key) throws RemoteException {
        return Stellar.requireService().getSystemProperty(key, null);
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
    public static String get(String key, String def) throws RemoteException {
        return Stellar.requireService().getSystemProperty(key, def);
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
    public static int getInt(String key, int def) throws RemoteException {
        return Integer.decode(Stellar.requireService().getSystemProperty(key, Integer.toString(def)));
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
    public static long getLong(String key, long def) throws RemoteException {
        return Long.decode(Stellar.requireService().getSystemProperty(key, Long.toString(def)));
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
    public static boolean getBoolean(String key, boolean def) throws RemoteException {
        return Boolean.parseBoolean(Stellar.requireService().getSystemProperty(key, Boolean.toString(def)));
    }

    /**
     * 设置系统属性
     * Set system property
     * 
     * <p>权限要求 Permission Requirements：</p>
     * <ul>
     * <li><b>ADB 模式：</b>可设置 debug.*, persist.debug.*, log.*, vendor.debug.* 等属性</li>
     * <li><b>Root 模式：</b>可设置大部分属性（ro.* 只读属性除外）</li>
     * </ul>
     * 
     * <p>示例 Examples：</p>
     * <pre>
     * // ADB 模式下可用 (Available in ADB mode)
     * set("debug.myapp.feature", "enabled");
     * set("persist.debug.level", "verbose");
     * set("log.tag.MyTag", "DEBUG");
     * 
     * // 需要 Root 模式 (Requires root mode)
     * set("persist.sys.locale", "zh_CN");
     * set("sys.usb.config", "adb");
     * </pre>
     * 
     * @param key 属性键名 Property key
     * @param val 属性值 Property value
     * @throws RemoteException 远程调用异常
     * @throws IllegalStateException 如果权限不足或属性为只读
     */
    public static void set(String key, String val) throws RemoteException {
        Stellar.requireService().setSystemProperty(key, val);
    }
}

