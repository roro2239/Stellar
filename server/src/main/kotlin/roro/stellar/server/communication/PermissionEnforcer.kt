package roro.stellar.server.communication

import android.os.Binder
import android.system.Os
import roro.stellar.StellarApiConstants
import roro.stellar.server.ClientManager
import roro.stellar.server.ConfigManager
import roro.stellar.server.util.Logger
import roro.stellar.server.util.OsUtils
import roro.stellar.server.util.UserHandleCompat.getAppId

class PermissionEnforcer(
    private val clientManager: ClientManager,
    private val configManager: ConfigManager,
    private val managerAppId: Int
) {
    companion object {
        private val LOGGER = Logger("PermissionEnforcer")
    }

    fun isManager(caller: CallerContext): Boolean = getAppId(caller.uid) == managerAppId

    fun isSelf(caller: CallerContext): Boolean = caller.uid == OsUtils.uid || caller.pid == Os.getpid()

    fun hasPermission(caller: CallerContext, permission: String = "stellar"): Boolean {
        if (isSelf(caller) || isManager(caller)) return true

        val clientRecord = clientManager.findClient(caller.uid, caller.pid) ?: return false

        if (StellarApiConstants.isRuntimePermission(permission)) {
            return clientRecord.allowedMap[permission] == true
        }

        return configManager.find(caller.uid)?.permissions?.get(permission) == ConfigManager.FLAG_GRANTED
    }

    fun enforceManager(caller: CallerContext, func: String) {
        if (isSelf(caller) || isManager(caller)) return

        val msg = "Permission Denial: $func from pid=${caller.pid}, uid=${caller.uid} is not manager"
        LOGGER.w(msg)
        throw SecurityException(msg)
    }

    fun enforcePermission(caller: CallerContext, func: String, permission: String = "stellar") {
        if (isSelf(caller) || isManager(caller)) return

        val clientRecord = clientManager.findClient(caller.uid, caller.pid)
        if (clientRecord == null) {
            val msg = "Permission Denial: $func from pid=${caller.pid}, uid=${caller.uid} is not an attached client"
            LOGGER.w(msg)
            throw SecurityException(msg)
        }

        if (clientRecord.allowedMap[permission] != true) {
            val msg = "Permission Denial: $func from pid=${caller.pid}, uid=${caller.uid} requires permission $permission"
            LOGGER.w(msg)
            throw SecurityException(msg)
        }
    }
}
