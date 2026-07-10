package roro.stellar.manager.compat

import android.os.Build

object BuildUtils {
    val atLeast26: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    val atLeast28: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    val atLeast29: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    val atLeast30: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    val atLeast31: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val atLeast33: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val atLeast34: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}
