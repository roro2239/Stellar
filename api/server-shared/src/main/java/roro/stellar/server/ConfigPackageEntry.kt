package roro.stellar.server;

import roro.stellar.server.util.Logger;

/**
 * 配置包条目抽象类
 * Configuration Package Entry Abstract Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>表示单个应用包的权限配置条目 - Represents permission configuration entry for a single app package</li>
 * <li>提供权限状态查询接口 - Provides permission status query interface</li>
 * <li>支持授权/拒绝状态判断 - Supports allowed/denied status determination</li>
 * </ul>
 */
public abstract class ConfigPackageEntry {

    protected static final Logger LOGGER = new Logger("ConfigPackageEntry");

    public ConfigPackageEntry() {
    }

    /**
     * 是否已授权
     * Whether allowed
     * 
     * @return true表示已授权
     */
    public abstract boolean isAllowed();

    /**
     * 是否已拒绝
     * Whether denied
     * 
     * @return true表示已拒绝
     */
    public abstract boolean isDenied();
}

