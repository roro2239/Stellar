package roro.stellar.server.service.permission

import roro.stellar.StellarApiConstants
import roro.stellar.server.ClientManager
import roro.stellar.server.ConfigManager
import roro.stellar.server.util.OsUtils

class PermissionChecker(
    private val clientManager: ClientManager,
    private val configManager: ConfigManager
) {
    fun checkSelfPermission(uid: Int, pid: Int, permission: String): Boolean {
        if (!StellarApiConstants.PERMISSIONS.contains(permission)) {
            return false
        }
        if (uid == OsUtils.uid || pid == OsUtils.pid) {
            return true
        }
        return when (configManager.find(uid)?.permissions?.get(permission)) {
            ConfigManager.FLAG_GRANTED -> true
            ConfigManager.FLAG_DENIED -> false
            else -> {
                if (StellarApiConstants.isRuntimePermission(permission)) {
                    clientManager.requireClient(uid, pid).allowedMap[permission] ?: false
                } else {
                    false
                }
            }
        }
    }

    fun shouldShowRequestPermissionRationale(uid: Int): Boolean =
        configManager.find(uid)?.permissions?.get("stellar") == ConfigManager.FLAG_DENIED
}
