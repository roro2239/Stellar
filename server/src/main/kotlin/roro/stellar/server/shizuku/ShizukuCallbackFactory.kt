package roro.stellar.server.shizuku

import android.content.Intent
import android.os.Process
import android.system.Os
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.PackageManagerApis
import roro.stellar.StellarApiConstants
import roro.stellar.server.ClientManager
import roro.stellar.server.ConfigManager
import roro.stellar.server.ServerConstants
import roro.stellar.server.service.StellarServiceCore
import roro.stellar.server.userservice.UserServiceManager
import roro.stellar.server.util.Logger

object ShizukuCallbackFactory {
    private val LOGGER = Logger("ShizukuCallbackFactory")

    fun create(
        clientManager: ClientManager,
        configManager: ConfigManager,
        userServiceManager: UserServiceManager,
        managerAppId: Int,
        serviceCore: StellarServiceCore,
        shizukuNotifier: ShizukuPermissionNotifier
    ): ShizukuServiceCallback {
        val cachedUid = Os.getuid()
        val cachedPid = Process.myPid()
        val cachedSeContext = try { android.os.SELinux.getContext() } catch (_: Throwable) { null }

        return object : ShizukuServiceCallback {
            override val serviceUid: Int = cachedUid
            override val serviceVersion: Int = StellarApiConstants.SERVER_VERSION
            override val serviceSeLinuxContext: String? = cachedSeContext

            override val clientManager: ClientManager get() = clientManager
            override val configManager: ConfigManager get() = configManager
            override val userServiceManager: UserServiceManager get() = userServiceManager
            override val managerAppId: Int get() = managerAppId
            override val servicePid: Int = cachedPid

            override fun getPackagesForUid(uid: Int): List<String> {
                return PackageManagerApis.getPackagesForUidNoThrow(uid).toList()
            }

            override fun getSystemProperty(name: String?, defaultValue: String?): String {
                return android.os.SystemProperties.get(name, defaultValue)
            }

            override fun setSystemProperty(name: String?, value: String?) {
                android.os.SystemProperties.set(name, value)
            }

            override fun newProcess(uid: Int, pid: Int, cmd: Array<String?>?, env: Array<String?>?, dir: String?): com.stellar.server.IRemoteProcess {
                return serviceCore.processManager.newProcess(uid, pid, cmd ?: emptyArray(), env, dir)
            }

            override fun requestPermission(uid: Int, pid: Int, requestCode: Int) {
                val userId = uid / 100000
                val packages = PackageManagerApis.getPackagesForUidNoThrow(uid)
                val packageName = packages.firstOrNull() ?: return

                LOGGER.i("Shizuku 权限请求: uid=$uid, pid=$pid, pkg=$packageName, code=$requestCode")

                val ai = PackageManagerApis.getApplicationInfoNoThrow(packageName, 0, userId) ?: run {
                    LOGGER.w("无法获取应用信息: $packageName")
                    return
                }

                val currentFlag = configManager.getPermissionFlag(uid, ShizukuApiConstants.PERMISSION_NAME)

                if (currentFlag == ConfigManager.FLAG_DENIED) {
                    LOGGER.i("Shizuku 权限已被永久拒绝: uid=$uid")
                    shizukuNotifier.notifyPermissionResult(uid, pid, requestCode, false)
                    return
                }

                if (currentFlag == ConfigManager.FLAG_GRANTED) {
                    LOGGER.i("Shizuku 权限已被永久授权: uid=$uid")
                    shizukuNotifier.notifyPermissionResult(uid, pid, requestCode, true)
                    return
                }

                if (configManager.find(uid) == null) {
                    configManager.createConfigWithAllPermissions(uid, packageName)
                }

                val lastDenyTime = clientManager.findClient(uid, pid)?.lastDenyTimeMap?.get(ShizukuApiConstants.PERMISSION_NAME) ?: 0
                val denyOnce = (System.currentTimeMillis() - lastDenyTime) > 10000

                val intent = Intent(ServerConstants.REQUEST_PERMISSION_ACTION)
                    .setPackage(ServerConstants.MANAGER_APPLICATION_ID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    .putExtra("uid", uid)
                    .putExtra("pid", pid)
                    .putExtra("requestCode", requestCode)
                    .putExtra("applicationInfo", ai)
                    .putExtra("denyOnce", denyOnce)
                    .putExtra("permission", ShizukuApiConstants.PERMISSION_NAME)

                ActivityManagerApis.startActivityNoThrow(intent, null, userId)
            }
        }
    }
}
