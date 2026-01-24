package roro.stellar.manager

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.annotation.IntDef
import roro.stellar.manager.util.EmptySharedPreferencesImpl
import roro.stellar.manager.util.PortBlacklistUtils

object StellarSettings {
    const val NAME = "settings"
    const val KEEP_START_ON_BOOT = "start_on_boot"
    const val KEEP_START_ON_BOOT_WIRELESS = "start_on_boot_wireless"
    const val TCPIP_PORT = "tcpip_port"
    const val TCPIP_PORT_ENABLED = "tcpip_port_enabled"
    const val THEME_MODE = "theme_mode"
    const val DROP_PRIVILEGES = "drop_privileges"
    const val BOOT_COMMAND = "boot_command"
    const val FOLLOW_STARTUP_COMMAND = "follow_startup_command"

    private var preferences: SharedPreferences? = null

    fun getPreferences(): SharedPreferences = preferences ?: EmptySharedPreferencesImpl()

    private fun getSettingsStorageContext(context: Context): Context {
        val storageContext = context.createDeviceProtectedStorageContext()
        return object : ContextWrapper(storageContext) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return try {
                    super.getSharedPreferences(name, mode)
                } catch (e: IllegalStateException) {
                    EmptySharedPreferencesImpl()
                }
            }
        }
    }

    fun initialize(context: Context) {
        if (preferences == null) {
            preferences = getSettingsStorageContext(context)
                .getSharedPreferences(NAME, Context.MODE_PRIVATE)

            preferences?.let { prefs ->
                if (!prefs.contains(TCPIP_PORT_ENABLED)) {
                    prefs.edit().putBoolean(TCPIP_PORT_ENABLED, true).apply()
                }
                if (!prefs.contains(TCPIP_PORT)) {
                    var randomPort = PortBlacklistUtils.generateSafeRandomPort(1000, 9999, 100)
                    if (randomPort == -1) {
                        randomPort = 8765
                    }
                    prefs.edit().putString(TCPIP_PORT, randomPort.toString()).apply()
                }
            }
        }
    }

    @IntDef(LaunchMethod.UNKNOWN, LaunchMethod.ROOT, LaunchMethod.ADB)
    @Retention(AnnotationRetention.SOURCE)
    annotation class LaunchMethod {
        companion object {
            const val UNKNOWN = -1
            const val ROOT = 0
            const val ADB = 1
        }
    }

    @LaunchMethod
    fun getLastLaunchMode(): Int = getPreferences().getInt("mode", LaunchMethod.UNKNOWN)

    fun setLastLaunchMode(@LaunchMethod method: Int) {
        getPreferences().edit().putInt("mode", method).apply()
    }
}
