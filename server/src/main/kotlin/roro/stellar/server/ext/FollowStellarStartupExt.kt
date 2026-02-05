package roro.stellar.server.ext

import android.content.Intent
import rikka.hidden.compat.ActivityManagerApis
import roro.stellar.server.ConfigManager
import roro.stellar.server.util.Logger
import roro.stellar.server.util.UserHandleCompat

object FollowStellarStartupExt {
    const val ACTION_FOLLOW_STARTUP = "roro.stellar.intent.action.FOLLOW_STELLAR_STARTUP"

    const val PERMISSION_FOLLOW_STARTUP = "follow_stellar_startup"

    private val LOGGER: Logger = Logger("FollowStellarStartupExt")

    fun schedule(configManager: ConfigManager) {
        for (entry in configManager.packages) {
            if (entry.value.permissions["stellar"] != ConfigManager.FLAG_GRANTED) continue

            if (entry.value.permissions[PERMISSION_FOLLOW_STARTUP] == ConfigManager.FLAG_GRANTED) {
                for (packageName in entry.value.packages) {
                    broadcast(
                        action = ACTION_FOLLOW_STARTUP,
                        packageName = packageName,
                        uid = entry.key
                    )
                }
            }
        }

    }

    private fun broadcast(
        action: String,
        packageName: String,
        uid: Int
    ) {
        LOGGER.i("向 packageName = $packageName, uid = $uid 发送 action = $action 的广播")
        ActivityManagerApis.broadcastIntent(
            Intent().apply {
                setAction(action)
                setPackage(packageName)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }, null, null, 0, null, null,
            null, -1, null, true, false, UserHandleCompat.getUserId(uid)
        )
    }
}