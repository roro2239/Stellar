package roro.stellar.server

import android.os.IBinder.DeathRecipient
import android.os.RemoteException
import com.stellar.server.IStellarApplication
import moe.shizuku.server.IShizukuApplication
import roro.stellar.server.util.Logger
import java.util.concurrent.ConcurrentHashMap

open class ClientManager(
    val configManager: ConfigManager
) {
    private val clientsByKey = ConcurrentHashMap<Long, ClientRecord>()
    private val clientsByUid = ConcurrentHashMap<Int, MutableSet<ClientRecord>>()

    private fun makeKey(uid: Int, pid: Int): Long = (uid.toLong() shl 32) or (pid.toLong() and 0xFFFFFFFFL)

    fun findClients(uid: Int): List<ClientRecord> = clientsByUid[uid]?.toList() ?: emptyList()

    fun findClient(uid: Int, pid: Int): ClientRecord? = clientsByKey[makeKey(uid, pid)]

    private fun addToMaps(record: ClientRecord) {
        clientsByKey[makeKey(record.uid, record.pid)] = record
        clientsByUid.getOrPut(record.uid) { ConcurrentHashMap.newKeySet() }.add(record)
    }

    private fun removeFromMaps(record: ClientRecord) {
        clientsByKey.remove(makeKey(record.uid, record.pid))
        clientsByUid[record.uid]?.remove(record)
    }

    fun getOrCreateClient(uid: Int, pid: Int, packageName: String, apiVersion: Int = 0): ClientRecord {
        findClient(uid, pid)?.let { return it }

        val record = ClientRecord(uid, pid, null, packageName, apiVersion)

        configManager.find(uid)?.let { entry ->
            for (permission in entry.permissions) {
                record.allowedMap[permission.key] = permission.value == ConfigManager.FLAG_GRANTED
            }
        }

        addToMaps(record)
        LOGGER.i("创建 Shizuku 客户端记录: uid=%d, pid=%d, package=%s", uid, pid, packageName)
        return record
    }

    fun attachShizukuApplication(uid: Int, pid: Int, application: IShizukuApplication, packageName: String, apiVersion: Int = 0): ClientRecord {
        val record = getOrCreateClient(uid, pid, packageName, apiVersion)
        record.shizukuApplication = application

        try {
            application.asBinder().linkToDeath({
                LOGGER.i("Shizuku 客户端死亡: uid=%d, pid=%d", uid, pid)
                record.shizukuApplication = null
                if (record.client == null) {
                    removeFromMaps(record)
                }
            }, 0)
        } catch (e: RemoteException) {
            LOGGER.w(e, "attachShizukuApplication: linkToDeath 失败")
        }

        LOGGER.i("附加 Shizuku 应用: uid=%d, pid=%d", uid, pid)
        return record
    }

    @JvmOverloads
    fun requireClient(
        callingUid: Int,
        callingPid: Int,
        requiresPermission: Boolean = false
    ): ClientRecord {
        val clientRecord = findClient(callingUid, callingPid)
            ?: throw IllegalStateException("非已连接的客户端").also {
                LOGGER.w("Caller (uid %d, pid %d) is not an attached client", callingUid, callingPid)
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
                removeFromMaps(oldClient)
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
            removeFromMaps(clientRecord)
        }
        try {
            binder.linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            LOGGER.w(e, "addClient: linkToDeath 失败")
            return null
        }

        addToMaps(clientRecord)
        LOGGER.i("客户端记录已添加: uid=%d, pid=%d, 当前客户端总数=%d", uid, pid, clientsByKey.size)
        return clientRecord
    }

    companion object {
        protected val LOGGER: Logger = Logger("ClientManager")
    }
}
