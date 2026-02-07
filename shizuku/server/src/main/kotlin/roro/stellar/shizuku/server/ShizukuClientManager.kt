package roro.stellar.shizuku.server

import android.os.IBinder.DeathRecipient
import android.os.RemoteException
import android.util.Log
import moe.shizuku.server.IShizukuApplication
import java.util.Collections

/**
 * Shizuku 客户端管理器
 * 管理使用 Shizuku API 的客户端连接
 */
class ShizukuClientManager(
    private val shizukuConfigManager: ShizukuConfigManager
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
            Log.w(TAG, "Caller (uid $callingUid, pid $callingPid) is not an attached Shizuku client")
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
            Log.i(TAG, "清理旧 Shizuku 客户端: uid=${oldClient.uid}, pid=${oldClient.pid}")
            clientRecords.remove(oldClient)
        }

        val record = ShizukuClientRecord(uid, pid, client, packageName, apiVersion)

        // 从 Shizuku 配置中加载权限状态
        record.allowed = shizukuConfigManager.getFlag(uid) == ShizukuConfigManager.FLAG_GRANTED

        val binder = client.asBinder()
        val deathRecipient = DeathRecipient {
            Log.i(TAG, "Shizuku 客户端死亡: uid=$uid, pid=$pid")
            clientRecords.remove(record)
        }

        try {
            binder.linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            Log.w(TAG, "linkToDeath 失败", e)
            return null
        }

        clientRecords.add(record)
        Log.i(TAG, "Shizuku 客户端已添加: uid=$uid, pid=$pid, package=$packageName")
        return record
    }

    companion object {
        private const val TAG = "ShizukuClientManager"
    }
}