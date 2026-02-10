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
    private val checker: PermissionChecker
    private val requester: PermissionRequester
    private val confirmation: PermissionConfirmation

    companion object {
        private val LOGGER = Logger("PermissionManager")
    }

    init {
        checker = PermissionChecker(clientManager, configManager)
        confirmation = PermissionConfirmation()
        requester = PermissionRequester(clientManager, configManager, confirmation)
    }

    fun checkSelfPermission(uid: Int, pid: Int, permission: String): Boolean {
        return checker.checkSelfPermission(uid, pid, permission)
    }

    fun requestPermission(uid: Int, pid: Int, permission: String, requestCode: Int) {
        val userId = getUserId(uid)
        requester.requestPermission(uid, pid, userId, permission, requestCode)
    }

    fun shouldShowRequestPermissionRationale(uid: Int): Boolean {
        return checker.shouldShowRequestPermissionRationale(uid)
    }

    fun getSupportedPermissions(): Array<String> {
        return StellarApiConstants.PERMISSIONS
    }

    fun dispatchPermissionResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: Bundle
    ) {
        requester.dispatchPermissionResult(requestUid, requestPid, requestCode, data)
    }

    fun getFlagForUid(uid: Int, permission: String): Int {
        val entry = configManager.find(uid)
        return entry?.permissions?.get(permission) ?: ConfigManager.FLAG_ASK
    }

    fun updateFlagForUid(uid: Int, permission: String, newFlag: Int) {
        val records = clientManager.findClients(uid)

        for (record in records) {
            fun stopApp() {
                if (StellarApiConstants.isRuntimePermission(permission) &&
                    record.allowedMap[permission] == true) {
                    ActivityManagerApis.forceStopPackageNoThrow(
                        record.packageName,
                        getUserId(uid)
                    )
                }
            }

            when (newFlag) {
                ConfigManager.FLAG_ASK -> {
                    stopApp()
                    record.allowedMap[permission] = false
                    record.onetimeMap[permission] = false
                }
                ConfigManager.FLAG_DENIED -> {
                    stopApp()
                    record.allowedMap[permission] = false
                    record.onetimeMap[permission] = false
                }
                ConfigManager.FLAG_GRANTED -> {
                    record.allowedMap[permission] = true
                    record.onetimeMap[permission] = false
                }
            }
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
