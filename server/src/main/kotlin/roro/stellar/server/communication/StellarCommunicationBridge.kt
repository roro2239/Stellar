package roro.stellar.server.communication

import android.os.Bundle
import android.os.IBinder
import com.stellar.server.IRemoteProcess
import com.stellar.server.IUserServiceCallback
import roro.stellar.server.service.StellarServiceCore

class StellarCommunicationBridge(
    private val serviceCore: StellarServiceCore,
    private val permissionEnforcer: PermissionEnforcer
) {
    fun handleGetVersion(caller: CallerContext): Int {
        permissionEnforcer.enforcePermission(caller, "getVersion")
        return serviceCore.serviceInfo.getVersion()
    }

    fun handleGetUid(caller: CallerContext): Int {
        permissionEnforcer.enforcePermission(caller, "getUid")
        return serviceCore.serviceInfo.getUid()
    }

    fun handleGetSELinuxContext(caller: CallerContext): String? {
        permissionEnforcer.enforcePermission(caller, "getSELinuxContext")
        return serviceCore.serviceInfo.getSELinuxContext()
    }

    fun handleGetVersionName(caller: CallerContext): String? {
        permissionEnforcer.enforcePermission(caller, "getVersionName")
        return serviceCore.serviceInfo.getVersionName()
    }

    fun handleGetVersionCode(caller: CallerContext): Int {
        permissionEnforcer.enforcePermission(caller, "getVersionCode")
        return serviceCore.serviceInfo.getVersionCode()
    }

    fun handleCheckSelfPermission(caller: CallerContext, permission: String): Boolean {
        return serviceCore.permissionManager.checkSelfPermission(caller.uid, caller.pid, permission)
    }

    fun handleRequestPermission(caller: CallerContext, permission: String, requestCode: Int) {
        serviceCore.permissionManager.requestPermission(caller.uid, caller.pid, permission, requestCode)
    }

    fun handleShouldShowRequestPermissionRationale(caller: CallerContext): Boolean {
        return serviceCore.permissionManager.shouldShowRequestPermissionRationale(caller.uid)
    }

    fun handleGetSupportedPermissions(): Array<String> {
        return serviceCore.permissionManager.getSupportedPermissions()
    }

    fun handleDispatchPermissionConfirmationResult(
        caller: CallerContext,
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: Bundle
    ) {
        permissionEnforcer.enforceManager(caller, "dispatchPermissionConfirmationResult")
        serviceCore.permissionManager.dispatchPermissionResult(requestUid, requestPid, requestCode, data)
    }

    fun handleGetFlagForUid(caller: CallerContext, uid: Int, permission: String): Int {
        permissionEnforcer.enforceManager(caller, "getFlagForUid")
        return serviceCore.permissionManager.getFlagForUid(uid, permission)
    }

    fun handleUpdateFlagForUid(caller: CallerContext, uid: Int, permission: String, flag: Int) {
        permissionEnforcer.enforceManager(caller, "updateFlagForUid")
        serviceCore.permissionManager.updateFlagForUid(uid, permission, flag)
    }

    fun handleGrantRuntimePermission(
        caller: CallerContext,
        packageName: String,
        permissionName: String,
        userId: Int
    ) {
        permissionEnforcer.enforcePermission(caller, "grantRuntimePermission")
        serviceCore.permissionManager.grantRuntimePermission(packageName, permissionName, userId)
    }

    fun handleRevokeRuntimePermission(
        caller: CallerContext,
        packageName: String,
        permissionName: String,
        userId: Int
    ) {
        permissionEnforcer.enforcePermission(caller, "revokeRuntimePermission")
        serviceCore.permissionManager.revokeRuntimePermission(packageName, permissionName, userId)
    }

    fun handleNewProcess(
        caller: CallerContext,
        cmd: Array<String?>,
        env: Array<String?>?,
        dir: String?
    ): IRemoteProcess {
        permissionEnforcer.enforcePermission(caller, "newProcess")
        return serviceCore.processManager.newProcess(caller.uid, caller.pid, cmd, env, dir)
    }

    fun handleGetSystemProperty(caller: CallerContext, name: String, defaultValue: String): String {
        permissionEnforcer.enforcePermission(caller, "getSystemProperty")
        return serviceCore.systemPropertyManager.getSystemProperty(name, defaultValue)
    }

    fun handleSetSystemProperty(caller: CallerContext, name: String, value: String) {
        permissionEnforcer.enforcePermission(caller, "setSystemProperty")
        serviceCore.systemPropertyManager.setSystemProperty(name, value)
    }

    fun handleStartUserService(
        caller: CallerContext,
        args: Bundle?,
        callback: IUserServiceCallback?
    ): String? {
        permissionEnforcer.enforcePermission(caller, "startUserService")
        return serviceCore.userServiceCoordinator.startUserService(caller.uid, caller.pid, args, callback)
    }

    fun handleStopUserService(caller: CallerContext, token: String?) {
        permissionEnforcer.enforcePermission(caller, "stopUserService")
        serviceCore.userServiceCoordinator.stopUserService(token)
    }

    fun handleAttachUserService(binder: IBinder?, options: Bundle?) {
        serviceCore.userServiceCoordinator.attachUserService(binder, options)
    }

    fun handleGetUserServiceCount(caller: CallerContext): Int {
        permissionEnforcer.enforcePermission(caller, "getUserServiceCount")
        return serviceCore.userServiceCoordinator.getUserServiceCount(caller.uid)
    }

    fun handleGetLogs(caller: CallerContext): List<String> {
        permissionEnforcer.enforceManager(caller, "getLogs")
        return serviceCore.logManager.getLogs()
    }

    fun handleClearLogs(caller: CallerContext) {
        permissionEnforcer.enforceManager(caller, "clearLogs")
        serviceCore.logManager.clearLogs()
    }
}
