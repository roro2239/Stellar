package roro.stellar.server.service

import roro.stellar.server.ClientManager
import roro.stellar.server.ConfigManager
import roro.stellar.server.service.info.ServiceInfoProvider
import roro.stellar.server.service.info.VersionProvider
import roro.stellar.server.service.log.LogManager
import roro.stellar.server.service.permission.PermissionManager
import roro.stellar.server.service.process.ProcessManager
import roro.stellar.server.service.system.SystemPropertyManager
import roro.stellar.server.service.userservice.UserServiceCoordinator
import roro.stellar.server.userservice.UserServiceManager

class StellarServiceCore(
    private val clientManager: ClientManager,
    private val configManager: ConfigManager,
    private val userServiceManager: UserServiceManager
) {
    val serviceInfo: ServiceInfoProvider
    val permissionManager: PermissionManager
    val processManager: ProcessManager
    val systemPropertyManager: SystemPropertyManager
    val logManager: LogManager
    val userServiceCoordinator: UserServiceCoordinator

    init {
        val versionProvider = VersionProvider()
        serviceInfo = ServiceInfoProvider(versionProvider)
        permissionManager = PermissionManager(clientManager, configManager)
        processManager = ProcessManager(clientManager)
        systemPropertyManager = SystemPropertyManager()
        logManager = LogManager()
        userServiceCoordinator = UserServiceCoordinator(userServiceManager)
    }
}
