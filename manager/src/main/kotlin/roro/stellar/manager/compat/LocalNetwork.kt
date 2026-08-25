package roro.stellar.manager.compat

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun isRequired(): Boolean = BuildUtils.atLeast37

    fun hasAccess(context: Context): Boolean {
        if (!isRequired()) return true
        return ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}
