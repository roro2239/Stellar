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
        val clientRecord = ClientRecord(uid, pid, client, packageName, apiVersion)

        val entry = configManager.find(uid)
        if (entry != null) {
            for (permission in entry.permissions) {
                clientRecord.allowedMap[permission.key] = permission.value == ConfigManager.FLAG_GRANTED
            }
        }

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