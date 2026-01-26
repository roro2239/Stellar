package roro.stellar.manager.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object AutoStartUtils {

    fun openAutoStartSettings(context: Context): Boolean {
        val intents = getAutoStartIntents(context)

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                continue
            }
        }

        return openAppDetailSettings(context)
    }

    private fun getAutoStartIntents(context: Context): List<Intent> {
        val intents = mutableListOf<Intent>()
        val manufacturer = Build.MANUFACTURER.lowercase()

        if (manufacturer.contains("vivo")) {
            intents.add(Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            })
        }

        return intents
    }

    private fun openAppDetailSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
