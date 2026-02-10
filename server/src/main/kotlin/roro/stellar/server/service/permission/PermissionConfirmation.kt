package roro.stellar.server.service.permission

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.UserInfo
import android.os.Build
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.UserManagerApis
import roro.stellar.server.ClientRecord
import roro.stellar.server.ServerConstants
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.util.Logger

class PermissionConfirmation {
    companion object {
        private val LOGGER = Logger("PermissionConfirmation")
    }

    fun showPermissionConfirmation(
        requestCode: Int,
        clientRecord: ClientRecord,
        callingUid: Int,
        callingPid: Int,
        userId: Int,
        permission: String = "stellar"
    ) {
        val ai = PackageManagerApis.getApplicationInfoNoThrow(clientRecord.packageName, 0, userId)
            ?: return

        val pi = PackageManagerApis.getPackageInfoNoThrow(MANAGER_APPLICATION_ID, 0, userId)
        val userInfo = UserManagerApis.getUserInfo(userId)
        val isWorkProfileUser = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "android.os.usertype.profile.MANAGED" == userInfo.userType
        } else {
            (userInfo.flags and UserInfo.FLAG_MANAGED_PROFILE) != 0
        }

        if (pi == null && !isWorkProfileUser) {
            LOGGER.w("在非工作配置文件用户 $userId 中未找到管理器，撤销权限")
            clientRecord.dispatchRequestPermissionResult(
                requestCode,
                allowed = false,
                onetime = false
            )
            return
        }

        val intent = Intent(ServerConstants.REQUEST_PERMISSION_ACTION)
            .setPackage(MANAGER_APPLICATION_ID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            .putExtra("uid", callingUid)
            .putExtra("pid", callingPid)
            .putExtra("requestCode", requestCode)
            .putExtra("applicationInfo", ai)
            .putExtra(
                "denyOnce",
                (System.currentTimeMillis() - (clientRecord.lastDenyTimeMap[permission] ?: 0)) > 10000
            )
            .putExtra("permission", permission)

        ActivityManagerApis.startActivityNoThrow(intent, null, if (isWorkProfileUser) 0 else userId)
    }
}
