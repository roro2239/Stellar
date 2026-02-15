package roro.stellar.server.query

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.UserManagerApis
import rikka.parcelablelist.ParcelableListSlice
import roro.stellar.StellarApiConstants
import roro.stellar.server.ConfigManager
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID

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
            for (pi in PackageManagerApis.getInstalledPackagesNoThrow(
                (PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS).toLong(),
                user!!
            )) {
                if (MANAGER_APPLICATION_ID == pi.packageName) continue
                if (pi.requestedPermissions?.contains(SHIZUKU_MANAGER_PERMISSION) == true) continue
                val applicationInfo = pi.applicationInfo ?: continue
                val uid = applicationInfo.uid
                var flag = -1

                configManager.find(uid)?.let {
                    if (!it.packages.contains(pi.packageName)) return@let
                    it.permissions["stellar"]?.let { configFlag ->
                        flag = configFlag
                    }
                }

                if (flag != -1) {
                    list.add(pi)
                } else if (applicationInfo.metaData != null) {
                    val stellarPermission = applicationInfo.metaData.getString(
                        StellarApiConstants.PERMISSION_KEY,
                        ""
                    )
                    if (stellarPermission.split(",").contains("stellar")) {
                        list.add(pi)
                    } else if (applicationInfo.metaData.getBoolean("moe.shizuku.client.V3_SUPPORT", false)) {
                        list.add(pi)
                    }
                }
            }
        }
        return ParcelableListSlice(list)
    }
}
