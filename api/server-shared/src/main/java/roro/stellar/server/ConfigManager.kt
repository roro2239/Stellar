package roro.stellar.server;

import androidx.annotation.Nullable;

import java.util.List;

import roro.stellar.server.util.Logger;

/**
 * 配置管理器抽象类
 * Configuration Manager Abstract Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>管理客户端应用的权限配置 - Manages permission configuration for client apps</li>
 * <li>提供配置的查询、更新和删除接口 - Provides query, update and delete interfaces for configuration</li>
 * <li>支持按UID管理多个包的权限 - Supports managing permissions for multiple packages by UID</li>
 * </ul>
 * 
 * <p>权限标志 Permission Flags：</p>
 * <ul>
 * <li>FLAG_ALLOWED - 已授权标志</li>
 * <li>FLAG_DENIED - 已拒绝标志</li>
 * <li>MASK_PERMISSION - 权限掩码（包含允许和拒绝）</li>
 * </ul>
 */
public abstract class ConfigManager {

    protected static final Logger LOGGER = new Logger("ConfigManager");

    /** 权限标志：已授权 Permission flag: allowed */
    public static final int FLAG_ALLOWED = 1 << 1;
    
    /** 权限标志：已拒绝 Permission flag: denied */
    public static final int FLAG_DENIED = 1 << 2;
    
    /** 权限掩码：包含允许和拒绝 Permission mask: includes allowed and denied */
    public static final int MASK_PERMISSION = FLAG_ALLOWED | FLAG_DENIED;

    /**
     * 查找指定UID的配置条目
     * Find configuration entry for specified UID
     * 
     * @param uid 要查找的UID
     * @return 配置条目，如果不存在则返回null
     */
    @Nullable
    public abstract ConfigPackageEntry find(int uid);

    /**
     * 更新指定UID的配置
     * Update configuration for specified UID
     * 
     * @param uid UID
     * @param packages 包名列表
     * @param mask 要更新的标志位掩码
     * @param values 标志位的新值
     */
    public abstract void update(int uid, List<String> packages, int mask, int values);

    /**
     * 移除指定UID的配置
     * Remove configuration for specified UID
     * 
     * @param uid 要移除的UID
     */
    public abstract void remove(int uid);
}

