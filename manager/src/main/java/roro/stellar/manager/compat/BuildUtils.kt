package roro.stellar.manager.compat

import android.os.Build

object BuildUtils {
    
    val atLeast29: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    
    val atLeast30: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    
    val atLeast31: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    val atLeast33: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

