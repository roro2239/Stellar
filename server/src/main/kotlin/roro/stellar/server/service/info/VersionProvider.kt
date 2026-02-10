package roro.stellar.server.service.info

import android.content.pm.PackageInfo
import android.os.Build
import rikka.hidden.compat.PackageManagerApis
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID

class VersionProvider {
    private val managerPackageInfo: PackageInfo?
        get() = PackageManagerApis.getPackageInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0)

    fun getVersionName(): String? {
        val pi = managerPackageInfo ?: return "unknown"
        return pi.versionName ?: "unknown"
    }

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
