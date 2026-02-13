package roro.stellar.server.service.permission

import android.os.Bundle
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.PermissionManagerApis
import roro.stellar.StellarApiConstants
import roro.stellar.server.ClientManager
import roro.stellar.server.ConfigManager
import roro.stellar.server.util.Logger
import roro.stellar.server.util.UserHandleCompat.getUserId

class PermissionManager(
    private val clientManager: ClientManager,
    private val configManager: ConfigManager
) {
    private val confirmation = PermissionConfirmation()
    private val checker = PermissionChecker(clientManager, configManager)
    private val requester = PermissionRequester(clientManager, configManager, confirmation)

    companion object {
        private val LOGGER = Logger("PermissionManager")
    }

    fun checkSelfPermission(uid: Int, pid: Int, permission: String): Boolean =
        checker.checkSelfPermission(uid, pid, permission)

    fun requestPermission(uid: Int, pid: Int, permission: String, requestCode: Int) {
        requester.requestPermission(uid, pid, getUserId(uid), permission, requestCode)
    }

    fun shouldShowRequestPermissionRationale(uid: Int): Boolean =
        checker.shouldShowRequestPermissionRationale(uid)

    fun getSupportedPermissions(): Array<String> = StellarApiConstants.PERMISSIONS

    fun dispatchPermissionResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: Bundle
    ) = requester.dispatchPermissionResult(requestUid, requestPid, requestCode, data)

    fun getFlagForUid(uid: Int, permission: String): Int =
        configManager.find(uid)?.permissions?.get(permission) ?: ConfigManager.FLAG_ASK

    fun updateFlagForUid(uid: Int, permission: String, newFlag: Int) {
        for (record in clientManager.findClients(uid)) {
            val shouldStopApp = StellarApiConstants.isRuntimePermission(permission) &&
                    record.allowedMap[permission] == true &&
                    newFlag != ConfigManager.FLAG_GRANTED

            if (shouldStopApp) {
                ActivityManagerApis.forceStopPackageNoThrow(record.packageName, getUserId(uid))
            }

            record.allowedMap[permission] = newFlag == ConfigManager.FLAG_GRANTED
            record.onetimeMap[permission] = false
        }

        configManager.updatePermission(uid, permission, newFlag)
    }

    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        try {
            PermissionManagerApis.grantRuntimePermission(packageName, permissionName, userId)
        } catch (e: Exception) {
            throw RuntimeException("授予权限失败: ${e.message}", e)
        }
    }

    fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        try {
            PermissionManagerApis.revokeRuntimePermission(packageName, permissionName, userId)
        } catch (e: Exception) {
            throw RuntimeException("撤销权限失败: ${e.message}", e)
        }
    }
}
