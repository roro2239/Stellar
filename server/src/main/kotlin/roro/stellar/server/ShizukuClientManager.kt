package roro.stellar.server

import roro.stellar.server.util.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Shizuku 客户端管理器
 * 管理 Shizuku 客户端的一次性权限和拒绝时间记录
 */
class ShizukuClientManager {

    companion object {
        private val LOGGER = Logger("ShizukuClientManager")
    }

    /**
     * Shizuku 客户端记录
     */
    data class ShizukuClientRecord(
        val uid: Int,
        val pid: Int,
        var onetimeAllowed: Boolean = false,
        var lastDenyTime: Long = 0
    )

    // uid -> (pid -> record)
    private val clients = ConcurrentHashMap<Int, ConcurrentHashMap<Int, ShizukuClientRecord>>()

    /**
     * 获取或创建客户端记录
     */
    fun getOrCreateClient(uid: Int, pid: Int): ShizukuClientRecord {
        val pidMap = clients.getOrPut(uid) { ConcurrentHashMap() }
        return pidMap.getOrPut(pid) { ShizukuClientRecord(uid, pid) }
    }

    /**
     * 查找客户端记录
     */
    fun findClient(uid: Int, pid: Int): ShizukuClientRecord? {
        return clients[uid]?.get(pid)
    }

    /**
     * 查找指定 UID 的所有客户端
     */
    fun findClients(uid: Int): List<ShizukuClientRecord> {
        return clients[uid]?.values?.toList() ?: emptyList()
    }

    /**
     * 检查一次性权限
     */
    fun checkOnetimePermission(uid: Int, pid: Int): Boolean {
        return findClient(uid, pid)?.onetimeAllowed ?: false
    }

    /**
     * 设置一次性权限
     */
    fun setOnetimePermission(uid: Int, pid: Int, allowed: Boolean) {
        val record = getOrCreateClient(uid, pid)
        record.onetimeAllowed = allowed
        LOGGER.i("设置一次性权限: uid=$uid, pid=$pid, allowed=$allowed")
    }

    /**
     * 清除指定 UID 的所有一次性权限
     */
    fun clearOnetimePermissions(uid: Int) {
        clients[uid]?.values?.forEach { it.onetimeAllowed = false }
        LOGGER.i("清除一次性权限: uid=$uid")
    }

    /**
     * 记录拒绝时间
     */
    fun recordDenyTime(uid: Int, pid: Int) {
        val record = getOrCreateClient(uid, pid)
        record.lastDenyTime = System.currentTimeMillis()
        LOGGER.i("记录拒绝时间: uid=$uid, pid=$pid")
    }

    /**
     * 获取上次拒绝时间
     */
    fun getLastDenyTime(uid: Int, pid: Int): Long {
        return findClient(uid, pid)?.lastDenyTime ?: 0
    }

    /**
     * 移除客户端
     */
    fun removeClient(uid: Int, pid: Int) {
        clients[uid]?.remove(pid)
        if (clients[uid]?.isEmpty() == true) {
            clients.remove(uid)
        }
        LOGGER.i("移除客户端: uid=$uid, pid=$pid")
    }
}
