package roro.stellar.server.shizuku

import android.os.IBinder.DeathRecipient
import android.os.RemoteException
import moe.shizuku.server.IShizukuApplication
import roro.stellar.server.ConfigManager
import roro.stellar.server.util.Logger
import java.util.Collections

/**
 * Shizuku 客户端管理器
 * 管理使用 Shizuku API 的客户端连接
 */
class ShizukuClientManager(
    private val configManager: ConfigManager
) {
    private val clientRecords = Collections.synchronizedList(ArrayList<ShizukuClientRecord>())

    fun findClients(uid: Int): MutableList<ShizukuClientRecord> {
        synchronized(this) {
            val res = ArrayList<ShizukuClientRecord>()
            for (record in clientRecords) {
                if (record.uid == uid) {
                    res.add(record)
                }
            }
            return res
        }
    }

    fun findClient(uid: Int, pid: Int): ShizukuClientRecord? {
        for (record in clientRecords) {
            if (record.pid == pid && record.uid == uid) {
                return record
            }
        }
        return null
    }

    fun requireClient(callingUid: Int, callingPid: Int): ShizukuClientRecord {
        val record = findClient(callingUid, callingPid)
        if (record == null) {
            LOGGER.w("Caller (uid %d, pid %d) is not an attached Shizuku client", callingUid, callingPid)
            throw IllegalStateException("非已连接的 Shizuku 客户端")
        }
        return record
    }

    fun addClient(
        uid: Int,
        pid: Int,
        client: IShizukuApplication,
        packageName: String,
        apiVersion: Int
    ): ShizukuClientRecord? {
        // 清理同一 UID 的旧客户端
        val oldClients = findClients(uid)
        for (oldClient in oldClients) {
            LOGGER.i("清理旧 Shizuku 客户端: uid=%d, pid=%d", oldClient.uid, oldClient.pid)
            clientRecords.remove(oldClient)
        }

        val record = ShizukuClientRecord(uid, pid, client, packageName, apiVersion)

        // 从配置中加载权限状态
        val entry = configManager.find(uid)
        if (entry != null) {
            record.allowed = entry.permissions["stellar"] == ConfigManager.FLAG_GRANTED
        }

        val binder = client.asBinder()
        val deathRecipient = DeathRecipient {
            LOGGER.i("Shizuku 客户端死亡: uid=%d, pid=%d", uid, pid)
            clientRecords.remove(record)
        }

        try {
            binder.linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            LOGGER.w(e, "linkToDeath 失败")
            return null
        }

        clientRecords.add(record)
        LOGGER.i("Shizuku 客户端已添加: uid=%d, pid=%d, package=%s", uid, pid, packageName)
        return record
    }

    companion object {
        private val LOGGER = Logger("ShizukuClientManager")
    }
}
