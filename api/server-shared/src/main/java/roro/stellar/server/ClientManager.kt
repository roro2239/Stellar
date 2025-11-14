package roro.stellar.server;

import android.os.IBinder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.stellar.server.IStellarApplication;
import roro.stellar.server.util.Logger;

/**
 * 客户端管理器基类
 * Client Manager Base Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>管理所有连接的客户端进程 - Manages all connected client processes</li>
 * <li>维护客户端记录列表 - Maintains client record list</li>
 * <li>提供客户端查询、添加、移除等操作 - Provides client query, add, remove operations</li>
 * <li>与ConfigManager协作管理权限 - Cooperates with ConfigManager for permission management</li>
 * <li>监听客户端进程死亡并自动清理 - Monitors client process death and auto cleanup</li>
 * </ul>
 * 
 * <p>主要功能 Main Features：</p>
 * <ul>
 * <li>findClients(uid) - 查找指定UID的所有客户端</li>
 * <li>findClient(uid, pid) - 查找指定进程的客户端</li>
 * <li>requireClient() - 获取客户端（不存在则抛异常）</li>
 * <li>addClient() - 添加新客户端并设置死亡监听</li>
 * <li>removeClient() - 移除客户端</li>
 * </ul>
 * 
 * <p>客户端生命周期 Client Lifecycle：</p>
 * <ul>
 * <li>1. addClient() - 客户端连接时添加记录</li>
 * <li>2. 自动linkToDeath - 监听进程死亡</li>
 * <li>3. 进程死亡时自动removeClient - 自动清理</li>
 * </ul>
 * 
 * @param <ConfigMgr> 配置管理器类型
 */
public class ClientManager<ConfigMgr extends ConfigManager> {

    protected static final Logger LOGGER = new Logger("ClientManager");

    /** 配置管理器 Configuration manager */
    private final ConfigMgr configManager;
    
    /** 客户端记录列表（线程安全） Client record list (thread-safe) */
    private final List<ClientRecord> clientRecords = Collections.synchronizedList(new ArrayList<>());

    /**
     * 构造客户端管理器
     * Construct client manager
     * 
     * @param configManager 配置管理器实例
     */
    public ClientManager(ConfigMgr configManager) {
        this.configManager = configManager;
    }

    /**
     * 获取配置管理器
     * Get configuration manager
     * 
     * @return 配置管理器实例
     */
    public ConfigMgr getConfigManager() {
        return configManager;
    }

    /**
     * 查找指定UID的所有客户端
     * Find all clients for specified UID
     * 
     * @param uid 要查找的UID
     * @return 客户端记录列表（可能为空）
     */
    public List<ClientRecord> findClients(int uid) {
        synchronized (this) {
            List<ClientRecord> res = new ArrayList<>();
            for (ClientRecord clientRecord : clientRecords) {
                if (clientRecord.uid == uid) {
                    res.add(clientRecord);
                }
            }
            return res;
        }
    }

    /**
     * 查找指定进程的客户端
     * Find client for specified process
     * 
     * @param uid 客户端UID
     * @param pid 客户端PID
     * @return 客户端记录，如果不存在则返回null
     */
    public ClientRecord findClient(int uid, int pid) {
        for (ClientRecord clientRecord : clientRecords) {
            if (clientRecord.pid == pid && clientRecord.uid == uid) {
                return clientRecord;
            }
        }
        return null;
    }

    /**
     * 获取客户端记录（不存在则抛异常）
     * Require client record (throws if not found)
     * 
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @return 客户端记录
     * @throws IllegalStateException 如果客户端不存在
     */
    public ClientRecord requireClient(int callingUid, int callingPid) {
        return requireClient(callingUid, callingPid, false);
    }

    /**
     * 获取客户端记录（不存在或无权限则抛异常）
     * Require client record (throws if not found or no permission)
     * 
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @param requiresPermission 是否要求已授权
     * @return 客户端记录
     * @throws IllegalStateException 如果客户端不存在
     * @throws SecurityException 如果requiresPermission=true且客户端未授权
     */
    public ClientRecord requireClient(int callingUid, int callingPid, boolean requiresPermission) {
        ClientRecord clientRecord = findClient(callingUid, callingPid);
        if (clientRecord == null) {
            LOGGER.w("Caller (uid %d, pid %d) is not an attached client", callingUid, callingPid);
            throw new IllegalStateException("非已连接的客户端");
        }
        if (requiresPermission && !clientRecord.allowed) {
            throw new SecurityException("调用者没有权限");
        }
        return clientRecord;
    }

    /**
     * 添加新客户端
     * Add new client
     * 
     * <p>功能 Features：</p>
     * <ul>
     * <li>创建ClientRecord - Creates ClientRecord</li>
     * <li>从配置中读取权限状态 - Reads permission status from config</li>
     * <li>设置Binder死亡监听 - Sets up Binder death listener</li>
     * <li>添加到客户端列表 - Adds to client list</li>
     * </ul>
     * 
     * @param uid 客户端UID
     * @param pid 客户端PID
     * @param client 客户端回调接口
     * @param packageName 客户端包名
     * @param apiVersion 客户端API版本
     * @return 新创建的客户端记录，如果linkToDeath失败则返回null
     */
    public ClientRecord addClient(int uid, int pid, IStellarApplication client, String packageName, int apiVersion) {
        ClientRecord clientRecord = new ClientRecord(uid, pid, client, packageName, apiVersion);

        // 从配置中读取权限状态
        ConfigPackageEntry entry = configManager.find(uid);
        if (entry != null && entry.isAllowed()) {
            clientRecord.allowed = true;
        }

        // 监听客户端进程死亡
        IBinder binder = client.asBinder();
        IBinder.DeathRecipient deathRecipient = () -> clientRecords.remove(clientRecord);
        try {
            binder.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            LOGGER.w(e, "addClient: linkToDeath failed");
            return null;
        }

        clientRecords.add(clientRecord);
        return clientRecord;
    }
}



