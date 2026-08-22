package roro.stellar.server.service.info

import android.content.pm.PackageInfo
import android.os.Build
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.util.PackageManagerCompat

class VersionProvider {
    private val managerPackageInfo: PackageInfo?
        get() = PackageManagerCompat.getPackageInfo(MANAGER_APPLICATION_ID, 0, 0)

    fun getVersionName(): String = managerPackageInfo?.versionName ?: "unknown"

    fun getVersionCode(): Int {
        val pi = managerPackageInfo ?: return -1
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode
            }
        } catch (_: Exception) {
            -1
        }
    }
}
