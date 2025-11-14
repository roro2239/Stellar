package roro.stellar.server

import android.os.IBinder.DeathRecipient
import android.os.RemoteException
import com.stellar.server.IStellarApplication
import roro.stellar.server.util.Logger
import java.util.Collections

/**
 * 客户端管理器基类
 * Client Manager Base Class
 *
 *
 * 功能说明 Features：
 *
 *  * 管理所有连接的客户端进程 - Manages all connected client processes
 *  * 维护客户端记录列表 - Maintains client record list
 *  * 提供客户端查询、添加、移除等操作 - Provides client query, add, remove operations
 *  * 与ConfigManager协作管理权限 - Cooperates with ConfigManager for permission management
 *  * 监听客户端进程死亡并自动清理 - Monitors client process death and auto cleanup
 *
 *
 *
 * 主要功能 Main Features：
 *
 *  * findClients(uid) - 查找指定UID的所有客户端
 *  * findClient(uid, pid) - 查找指定进程的客户端
 *  * requireClient() - 获取客户端（不存在则抛异常）
 *  * addClient() - 添加新客户端并设置死亡监听
 *  * removeClient() - 移除客户端
 *
 *
 *
 * 客户端生命周期 Client Lifecycle：
 *
 *  * 1. addClient() - 客户端连接时添加记录
 *  * 2. 自动linkToDeath - 监听进程死亡
 *  * 3. 进程死亡时自动removeClient - 自动清理
 *
 *
 * @param <ConfigMgr> 配置管理器类型
</ConfigMgr> */
open class ClientManager<ConfigMgr : ConfigManager>
/**
 * 构造客户端管理器
 * Construct client manager
 *
 * @param configManager 配置管理器实例
 */(
    /** 配置管理器 Configuration manager  */
    val configManager: ConfigMgr
) {
    /**
     * 获取配置管理器
     * Get configuration manager
     *
     * @return 配置管理器实例
     */

    /** 客户端记录列表（线程安全） Client record list (thread-safe)  */
    private val clientRecords =
        Collections.synchronizedList(
            ArrayList<ClientRecord>()
        )

    /**
     * 查找指定UID的所有客户端
     * Find all clients for specified UID
     *
     * @param uid 要查找的UID
     * @return 客户端记录列表（可能为空）
     */
    fun findClients(uid: Int): MutableList<ClientRecord> {
        synchronized(this) {
            val res = ArrayList<ClientRecord>()
            for (clientRecord in clientRecords) {
                if (clientRecord != null) {
                    if (clientRecord.uid == uid) {
                        res.add(clientRecord)
                    }
                }
            }
            return res
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
    fun findClient(uid: Int, pid: Int): ClientRecord? {
        for (clientRecord in clientRecords) {
            if (clientRecord != null) {
                if (clientRecord.pid == pid && clientRecord.uid == uid) {
                    return clientRecord
                }
            }
        }
        return null
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
    @JvmOverloads
    fun requireClient(
        callingUid: Int,
        callingPid: Int,
        requiresPermission: Boolean = false
    ): ClientRecord {
        val clientRecord = findClient(callingUid, callingPid)
        if (clientRecord == null) {
            LOGGER.w("Caller (uid %d, pid %d) is not an attached client", callingUid, callingPid)
            throw IllegalStateException("非已连接的客户端")
        }
        if (requiresPermission && !clientRecord.allowed) {
            throw SecurityException("调用者没有权限")
        }
        return clientRecord
    }

    /**
     * 添加新客户端
     * Add new client
     *
     *
     * 功能 Features：
     *
     *  * 创建ClientRecord - Creates ClientRecord
     *  * 从配置中读取权限状态 - Reads permission status from config
     *  * 设置Binder死亡监听 - Sets up Binder death listener
     *  * 添加到客户端列表 - Adds to client list
     *
     *
     * @param uid 客户端UID
     * @param pid 客户端PID
     * @param client 客户端回调接口
     * @param packageName 客户端包名
     * @param apiVersion 客户端API版本
     * @return 新创建的客户端记录，如果linkToDeath失败则返回null
     */
    fun addClient(
        uid: Int,
        pid: Int,
        client: IStellarApplication,
        packageName: String?,
        apiVersion: Int
    ): ClientRecord? {
        val clientRecord = ClientRecord(uid, pid, client, packageName, apiVersion)

        // 从配置中读取权限状态
        val entry = configManager.find(uid)
        if (entry != null && entry.isAllowed) {
            clientRecord.allowed = true
        }

        // 监听客户端进程死亡
        val binder = client.asBinder()
        val deathRecipient = DeathRecipient { clientRecords.remove(clientRecord) }
        try {
            binder.linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            LOGGER.w(e, "addClient: linkToDeath failed")
            return null
        }

        clientRecords.add(clientRecord)
        return clientRecord
    }

    companion object {
        protected val LOGGER: Logger = Logger("ClientManager")
    }
}