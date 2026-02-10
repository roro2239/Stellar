package roro.stellar.server.service.permission

import android.os.Bundle
import roro.stellar.StellarApiConstants
import roro.stellar.server.ClientManager
import roro.stellar.server.ClientRecord
import roro.stellar.server.ConfigManager
import roro.stellar.server.util.Logger

class PermissionRequester(
    private val clientManager: ClientManager,
    private val configManager: ConfigManager,
    private val confirmation: PermissionConfirmation
) {
    companion object {
        private val LOGGER = Logger("PermissionRequester")
    }
    fun requestPermission(
        uid: Int,
        pid: Int,
        userId: Int,
        permission: String,
        requestCode: Int
    ) {
        val clientRecord = clientManager.requireClient(uid, pid)

        if (!StellarApiConstants.PERMISSIONS.contains(permission)) {
            clientRecord.dispatchRequestPermissionResult(
                requestCode,
                allowed = false,
                onetime = false,
                permission
            )
            return
        }
        when (configManager.find(uid)?.permissions?.get(permission)) {
            ConfigManager.FLAG_GRANTED -> {
                clientRecord.dispatchRequestPermissionResult(
                    requestCode,
                    allowed = true,
                    onetime = false,
                    permission
                )
            }
            ConfigManager.FLAG_DENIED -> {
                clientRecord.dispatchRequestPermissionResult(
                    requestCode,
                    allowed = false,
                    onetime = false,
                    permission
                )
            }
            else -> {
                confirmation.showPermissionConfirmation(
                    requestCode,
                    clientRecord,
                    uid,
                    pid,
                    userId,
                    permission
                )
            }
        }
    }

    fun dispatchPermissionResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: Bundle
    ) {
        val allowed = data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED)
        val onetime = data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME)
        val permission = data.getString(
            StellarApiConstants.REQUEST_PERMISSION_REPLY_PERMISSION,
            "stellar"
        )

        LOGGER.i(
            "dispatchPermissionResult: uid=$requestUid, pid=$requestPid, " +
                    "requestCode=$requestCode, allowed=$allowed, onetime=$onetime, permission=$permission"
        )

        val records = clientManager.findClients(requestUid)
        val packages = ArrayList<String>()

        if (records.isEmpty()) {
            LOGGER.w("dispatchPermissionResult: 未找到 uid $requestUid 的客户端")
        } else {
            for (record in records) {
                packages.add(record.packageName)
                if (StellarApiConstants.isRuntimePermission(permission)) {
                    record.allowedMap[permission] = allowed
                }
                if (record.pid == requestPid) {
                    record.dispatchRequestPermissionResult(
                        requestCode,
                        allowed,
                        onetime,
                        permission
                    )
                }
            }
        }

        configManager.update(requestUid, packages)
        configManager.updatePermission(
            requestUid,
            permission,
            if (onetime) ConfigManager.FLAG_ASK
            else if (allowed) ConfigManager.FLAG_GRANTED
            else ConfigManager.FLAG_DENIED
        )
    }
}
