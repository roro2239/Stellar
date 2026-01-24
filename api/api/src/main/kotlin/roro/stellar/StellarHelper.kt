package roro.stellar

import android.content.Context
import roro.stellar.Stellar.pingBinder
import roro.stellar.Stellar.sELinuxContext
import roro.stellar.Stellar.uid
import roro.stellar.Stellar.version

object StellarHelper {
    private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    private const val STELLAR_MANAGER_PACKAGE_NAME = "roro.stellar.manager"

    fun isManagerInstalled(context: Context): Boolean {
        if (true) return true
        try {
            context.packageManager.getPackageInfo(STELLAR_MANAGER_PACKAGE_NAME, 0)
            return true
        } catch (_: Exception) {
            try {
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
                return true
            } catch (_: Exception) {
                return false
            }
        }
    }

    fun openManager(context: Context): Boolean {
        try {
            var intent = context.packageManager.getLaunchIntentForPackage(
                STELLAR_MANAGER_PACKAGE_NAME
            )
            if (intent == null) {
                intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
            }
            if (intent != null) {
                context.startActivity(intent)
                return true
            }
        } catch (_: Exception) {
        }
        return false
    }

    val serviceInfo: ServiceInfo?
        get() {
            if (!pingBinder()) {
                return null
            }

            return try {
                ServiceInfo(
                    uid,
                    version,
                    sELinuxContext
                )
            } catch (_: Exception) {
                null
            }
        }

    class ServiceInfo(
        val uid: Int,
        val version: Int,
        val seLinuxContext: String?
    ) {
        val isRoot: Boolean
            get() = uid == 0

        val isAdb: Boolean
            get() = uid == 2000

        override fun toString(): String {
            return "ServiceInfo{" +
                    "uid=" + uid +
                    ", version=" + version +
                    ", seLinuxContext='" + seLinuxContext + '\'' +
                    '}'
        }
    }
}
