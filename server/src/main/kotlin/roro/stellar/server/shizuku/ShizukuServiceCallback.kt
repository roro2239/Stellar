package roro.stellar.server.shizuku

import roro.stellar.server.ClientManager
import roro.stellar.server.ConfigManager
import roro.stellar.server.userservice.UserServiceManager

interface ShizukuServiceCallback {
    val serviceUid: Int
    val serviceVersion: Int
    val serviceSeLinuxContext: String?

    val clientManager: ClientManager
    val configManager: ConfigManager
    val userServiceManager: UserServiceManager
    val managerAppId: Int
    val servicePid: Int

    fun getPackagesForUid(uid: Int): List<String>

    fun requestPermission(uid: Int, pid: Int, requestCode: Int)

    fun getSystemProperty(name: String?, defaultValue: String?): String

    fun setSystemProperty(name: String?, value: String?)

    fun newProcess(uid: Int, pid: Int, cmd: Array<String?>?, env: Array<String?>?, dir: String?): com.stellar.server.IRemoteProcess
}
