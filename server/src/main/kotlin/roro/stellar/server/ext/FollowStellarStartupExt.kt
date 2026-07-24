package roro.stellar.server.ext

import android.content.Intent
import android.os.Binder
import rikka.hidden.compat.ActivityManagerApis
import roro.stellar.StellarApiConstants
import roro.stellar.server.ConfigManager
import roro.stellar.server.binder.BinderDistributor
import roro.stellar.server.util.Logger
import roro.stellar.server.util.UserHandleCompat

object FollowStellarStartupExt {
    const val ACTION_FOLLOW_STARTUP = StellarApiConstants.ACTION_FOLLOW_STARTUP
    const val PERMISSION_FOLLOW_STARTUP = StellarApiConstants.PERMISSION_FOLLOW_STARTUP

    private val LOGGER: Logger = Logger("FollowStellarStartupExt")

    fun schedule(binder: Binder?, configManager: ConfigManager) {
        for ((uid, entry) in configManager.packages) {
            if (entry.permissions[PERMISSION_FOLLOW_STARTUP] != ConfigManager.FLAG_GRANTED) {
                LOGGER.v("跳过 uid=%d 的跟随启动：未授予跟随启动权限", uid)
                continue
            }

            if (entry.permissions[StellarApiConstants.PERMISSION_STELLAR] != ConfigManager.FLAG_GRANTED) {
                LOGGER.w(
                    "uid=%d 已授予跟随启动，但基础 Stellar 权限未授予；仍会发送启动广播",
                    uid
                )
            }

            for (packageName in entry.packages) {
                val userId = UserHandleCompat.getUserId(uid)
                val binderSent = BinderDistributor.sendBinderToUserApp(
                    binder = binder,
                    packageName = packageName,
                    userId = userId
                )
                if (!binderSent) {
                    LOGGER.w(
                        "向 %s 预发送 Binder 失败，仍继续发送跟随启动广播",
                        packageName
                    )
                }
                broadcast(
                    action = ACTION_FOLLOW_STARTUP,
                    packageName = packageName,
                    uid = uid
                )
            }
        }
    }

    private fun broadcast(
        action: String,
        packageName: String,
        uid: Int
    ) {
        val userId = UserHandleCompat.getUserId(uid)
        LOGGER.i(
            "发送跟随启动广播：packageName=%s，uid=%d，userId=%d，action=%s",
            packageName,
            uid,
            userId,
            action
        )

        try {
            ActivityManagerApis.broadcastIntent(
                Intent().apply {
                    setAction(action)
                    setPackage(packageName)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }, null, null, 0, null, null,
                null, -1, null, true, false, userId
            )
            LOGGER.i(
                "跟随启动广播已提交：packageName=%s，uid=%d，action=%s",
                packageName,
                uid,
                action
            )
        } catch (tr: Throwable) {
            LOGGER.e(
                tr,
                "跟随启动广播发送失败：packageName=%s，uid=%d，action=%s",
                packageName,
                uid,
                action
            )
        }
    }
}
