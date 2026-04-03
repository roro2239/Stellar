package roro.stellar.manager

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import roro.stellar.manager.util.EmptySharedPreferencesImpl
import roro.stellar.manager.util.PortBlacklistUtils

object StellarSettings {
    const val NAME = "settings"
    const val BOOT_SCRIPT_ENABLED = "boot_script_enabled"
    const val BOOT_MODE = "boot_mode"
    const val TCPIP_PORT = "tcpip_port"
    const val TCPIP_PORT_ENABLED = "tcpip_port_enabled"
    const val THEME_MODE = "theme_mode"
    const val DROP_PRIVILEGES = "drop_privileges"
    const val SHIZUKU_COMPAT_ENABLED = "shizuku_compat_enabled"
    const val ACCESSIBILITY_AUTO_START_PROMPTED = "accessibility_auto_start_prompted"
    const val LAST_VERSION_CODE = "last_version_code"
    const val DAEMON_ENABLED = "daemon_enabled"

    /** 开机启动模式，三选一，默认 NONE */
    enum class BootMode { NONE, BROADCAST, ACCESSIBILITY, SCRIPT }

    /** 记录上次服务的启动方式（Root / ADB），用于开机自启时决定走哪条路径 */
    enum class LaunchMethod { UNKNOWN, ROOT, ADB }
    const val LAST_LAUNCH_METHOD = "last_launch_method"

    fun getLastLaunchMethod(): LaunchMethod {
        val name = getPreferences().getString(LAST_LAUNCH_METHOD, LaunchMethod.UNKNOWN.name)
            ?: LaunchMethod.UNKNOWN.name
        return runCatching { LaunchMethod.valueOf(name) }.getOrDefault(LaunchMethod.UNKNOWN)
    }

    fun setLastLaunchMethod(method: LaunchMethod) {
        getPreferences().edit().putString(LAST_LAUNCH_METHOD, method.name).apply()
    }

    fun getBootMode(): BootMode {
        val name = getPreferences().getString(BOOT_MODE, BootMode.NONE.name) ?: BootMode.NONE.name
        return runCatching { BootMode.valueOf(name) }.getOrDefault(BootMode.NONE)
    }

    fun setBootMode(mode: BootMode) {
        getPreferences().edit().putString(BOOT_MODE, mode.name).apply()
    }

    private var preferences: SharedPreferences? = null

    fun getPreferences(): SharedPreferences = preferences ?: EmptySharedPreferencesImpl()

    private fun getSettingsStorageContext(context: Context): Context {
        val storageContext = context.createDeviceProtectedStorageContext()
        return object : ContextWrapper(storageContext) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return try {
                    super.getSharedPreferences(name, mode)
                } catch (_: IllegalStateException) {
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
}
