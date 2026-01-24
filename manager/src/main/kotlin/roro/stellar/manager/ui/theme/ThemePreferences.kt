package roro.stellar.manager.ui.theme

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.StellarSettings.THEME_MODE

enum class ThemeMode(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    AUTO("auto");
    
    companion object {
        fun fromValue(value: String): ThemeMode {
            return entries.find { it.value == value } ?: AUTO
        }
    }
}

object ThemePreferences {
    
    private var _themeMode: MutableState<ThemeMode>? = null
    
    val themeMode: MutableState<ThemeMode>
        get() {
            if (_themeMode == null) {
                val savedValue = StellarSettings.getPreferences()
                    .getString(THEME_MODE, ThemeMode.AUTO.value) ?: ThemeMode.AUTO.value
                _themeMode = mutableStateOf(ThemeMode.fromValue(savedValue))
            }
            return _themeMode!!
        }
    
    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        StellarSettings.getPreferences().edit {
            putString(THEME_MODE, mode.value)
        }
    }
    
    fun getThemeModeDisplayName(mode: ThemeMode): String {
        return when (mode) {
            ThemeMode.LIGHT -> "浅色"
            ThemeMode.DARK -> "深色"
            ThemeMode.AUTO -> "跟随系统"
        }
    }
}

