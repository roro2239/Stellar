package roro.stellar.manager.compat

import android.os.Build

/**
 * Build工具类
 * Build Utility Class
 * 
 * 替代Rikka BuildUtils
 * Replaces Rikka BuildUtils
 */
object BuildUtils {
    
    /**
     * 是否为Android 10 (API 29)或更高版本
     * Is Android 10 (API 29) or higher
     */
    val atLeast29: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    
    /**
     * 是否为Android 11 (API 30)或更高版本
     * Is Android 11 (API 30) or higher
     */
    val atLeast30: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    
    /**
     * 是否为Android 12 (API 31)或更高版本
     * Is Android 12 (API 31) or higher
     */
    val atLeast31: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    /**
     * 是否为Android 13 (API 33)或更高版本
     * Is Android 13 (API 33) or higher
     */
    val atLeast33: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

