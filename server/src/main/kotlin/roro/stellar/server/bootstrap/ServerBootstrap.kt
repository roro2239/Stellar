package roro.stellar.server.bootstrap

import android.content.pm.ApplicationInfo
import android.ddm.DdmHandleAppName
import android.os.Looper
import android.os.ServiceManager
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.StellarService
import roro.stellar.server.util.Logger
import roro.stellar.server.util.PackageManagerCompat

object ServerBootstrap {
    private val LOGGER = Logger("ServerBootstrap")

    @JvmStatic
    fun main(args: Array<String>) {
        DdmHandleAppName.setAppName("stellar_server", 0)
        Looper.prepareMainLooper()
        StellarService()
        Looper.loop()
    }

    fun waitSystemService(name: String) {
        while (ServiceManager.getService(name) == null) {
            try {
                LOGGER.i("服务 $name 尚未启动，等待 1 秒。")
                Thread.sleep(1000)
            } catch (_: InterruptedException) {
            }
        }
    }

    val managerApplicationInfo: ApplicationInfo?
        get() = PackageManagerCompat.getApplicationInfo(MANAGER_APPLICATION_ID, 0, 0)
}
