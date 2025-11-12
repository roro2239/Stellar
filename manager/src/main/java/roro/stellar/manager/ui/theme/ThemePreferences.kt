package roro.stellar.manager.ui.theme

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.StellarSettings.THEME_MODE

/**
 * 主题模式
 * Theme Mode
 */
enum class ThemeMode(val value: String) {
    /** 浅色主题 Light theme */
    LIGHT("light"),
    /** 深色主题 Dark theme */
    DARK("dark"),
    /** 跟随系统 Follow system */
    AUTO("auto");
    
    companion object {
        fun fromValue(value: String): ThemeMode {
            return entries.find { it.value == value } ?: AUTO
        }
    }
}

/**
 * 主题偏好设置管理器
 * Theme Preferences Manager
 * 
 * 功能说明 Features：
 * - 管理应用主题模式 - Manages app theme mode
 * - 支持浅色、深色、自动三种模式 - Supports light, dark, and auto modes
 * - 持久化保存用户选择 - Persists user selection
 */
object ThemePreferences {
    
    private var _themeMode: MutableState<ThemeMode>? = null
    
    /**
     * 获取当前主题模式状态
     * Get current theme mode state
     */
    val themeMode: MutableState<ThemeMode>
        get() {
            if (_themeMode == null) {
                val savedValue = StellarSettings.getPreferences()
                    .getString(THEME_MODE, ThemeMode.AUTO.value) ?: ThemeMode.AUTO.value
                _themeMode = mutableStateOf(ThemeMode.fromValue(savedValue))
            }
            return _themeMode!!
        }
    
    /**
     * 设置主题模式
     * Set theme mode
     * 
     * @param mode 主题模式
     */
    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        StellarSettings.getPreferences().edit {
            putString(THEME_MODE, mode.value)
        }
    }
    
    /**
     * 获取主题模式的显示名称
     * Get display name of theme mode
     */
    fun getThemeModeDisplayName(mode: ThemeMode): String {
        return when (mode) {
            ThemeMode.LIGHT -> "浅色"
            ThemeMode.DARK -> "深色"
            ThemeMode.AUTO -> "跟随系统"
        }
    }
}

