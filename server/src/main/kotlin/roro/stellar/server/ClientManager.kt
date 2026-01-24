package roro.stellar.server

import android.os.IBinder.DeathRecipient
import android.os.RemoteException
import com.stellar.server.IStellarApplication
import roro.stellar.server.util.Logger
import java.util.Collections

open class ClientManager(
    val configManager: ConfigManager
) {
    private val clientRecords =
        Collections.synchronizedList(
            ArrayList<ClientRecord>()
        )

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
        if (requiresPermission && clientRecord.allowedMap["stellar"] != true) {
            throw SecurityException("调用者没有权限")
        }
        return clientRecord
    }

    fun addClient(
        uid: Int,
        pid: Int,
        client: IStellarApplication,
        packageName: String,
        apiVersion: Int
    ): ClientRecord? {
        val oldClients = findClients(uid)
        if (oldClients.isNotEmpty()) {
            LOGGER.w("发现 uid=%d 存在 %d 个旧客户端记录，正在清理", uid, oldClients.size)
            for (oldClient in oldClients) {
                LOGGER.i("清理旧客户端: uid=%d, pid=%d, package=%s",
                    oldClient.uid, oldClient.pid, oldClient.packageName)
                clientRecords.remove(oldClient)
            }
        }

        val clientRecord = ClientRecord(uid, pid, client, packageName, apiVersion)

        val oldConfig = configManager.findOldConfigByPackageName(uid, packageName)
        if (oldConfig != null) {
            val (oldUid, _) = oldConfig
            LOGGER.i("检测到应用重装: %s 的 UID 从 %d 变为 %d，删除旧配置并创建新配置", packageName, oldUid, uid)
            configManager.remove(oldUid)
        }

        var entry = configManager.find(uid)
        if (entry == null) {
            LOGGER.i("为新 UID 创建配置: uid=%d, package=%s", uid, packageName)
            configManager.createConfigWithAllPermissions(uid, packageName)
            entry = configManager.find(uid)
        }

        LOGGER.i("addClient: uid=%d, pid=%d, package=%s, configEntry=%s",
            uid, pid, packageName, if (entry != null) "found" else "null")
        if (entry != null) {
            LOGGER.i("加载权限配置: uid=%d, permissions=%s", uid, entry.permissions.toString())
            for (permission in entry.permissions) {
                clientRecord.allowedMap[permission.key] = permission.value == ConfigManager.FLAG_GRANTED
            }
        } else {
            LOGGER.w("未找到 uid=%d 的配置条目，需要重新申请权限", uid)
        }
        LOGGER.i("客户端记录创建完成: uid=%d, allowedMap=%s", uid, clientRecord.allowedMap.toString())

        val binder = client.asBinder()
        val deathRecipient = DeathRecipient {
            LOGGER.i("客户端死亡回调: uid=%d, pid=%d, package=%s", uid, pid, packageName)
            clientRecords.remove(clientRecord)
        }
        try {
            binder.linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            LOGGER.w(e, "addClient: linkToDeath 失败")
            return null
        }

        clientRecords.add(clientRecord)
        LOGGER.i("客户端记录已添加: uid=%d, pid=%d, 当前客户端总数=%d", uid, pid, clientRecords.size)
        return clientRecord
    }

    companion object {
        protected val LOGGER: Logger = Logger("ClientManager")
    }
}