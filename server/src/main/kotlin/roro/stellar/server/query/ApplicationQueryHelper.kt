package roro.stellar.server.query

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import rikka.hidden.compat.UserManagerApis
import rikka.parcelablelist.ParcelableListSlice
import roro.stellar.StellarApiConstants
import roro.stellar.server.ConfigManager
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.shizuku.ShizukuApiConstants
import roro.stellar.server.util.PackageManagerCompat
import roro.stellar.server.util.ProviderDiscovery

object ApplicationQueryHelper {
    private const val SHIZUKU_MANAGER_PERMISSION = "moe.shizuku.manager.permission.MANAGER"

    fun getApplications(userId: Int, configManager: ConfigManager): ParcelableListSlice<PackageInfo?> {
        val list = ArrayList<PackageInfo?>()
        val users = ArrayList<Int?>()
        if (userId == -1) {
            users.addAll(UserManagerApis.getUserIdsNoThrow())
        } else {
            users.add(userId)
        }

        for (user in users) {
            for (pi in PackageManagerCompat.getInstalledPackagesNoThrow(
                (PackageManager.MATCH_ALL or PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS or PackageManager.GET_PROVIDERS).toLong(),
                user!!
            )) {
                val packageInfo = pi ?: continue
                if (MANAGER_APPLICATION_ID == packageInfo.packageName) continue
                if (packageInfo.requestedPermissions?.contains(SHIZUKU_MANAGER_PERMISSION) == true) continue
                val applicationInfo = packageInfo.applicationInfo ?: continue
                val uid = applicationInfo.uid
                var flag = -1

                configManager.find(uid)?.let {
                    if (!it.packages.contains(packageInfo.packageName)) return@let
                    it.permissions.values.firstOrNull()?.let { configFlag ->
                        flag = configFlag
                    }
                }

                if (flag != -1) {
                    list.add(packageInfo)
                } else if (applicationInfo.metaData != null) {
                    val stellarPermission = applicationInfo.metaData.getString(
                        StellarApiConstants.PERMISSION_KEY,
                        ""
                    )
                    if (stellarPermission.split(",").map { it.trim() }
                            .any { StellarApiConstants.PERMISSIONS.contains(it) } ||
                        ProviderDiscovery.hasStellarProvider(packageInfo)
                    ) {
                        list.add(packageInfo)
                    } else if (
                        applicationInfo.metaData.getBoolean(ShizukuApiConstants.META_DATA_KEY, false) ||
                        ProviderDiscovery.hasShizukuProvider(packageInfo)
                    ) {
                        list.add(packageInfo)
                    }
                } else if (ProviderDiscovery.hasShizukuProvider(packageInfo)) {
                    list.add(packageInfo)
                }
            }
        }
        return ParcelableListSlice(list)
    }
}
