package roro.stellar.server.api

import android.content.AttributionSource
import android.content.IContentProvider
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import roro.stellar.server.util.OsUtils.uid

object IContentProviderUtils {
    @Throws(RemoteException::class)
    fun callCompat(
        provider: IContentProvider,
        callingPkg: String?,
        authority: String?,
        method: String?,
        arg: String?,
        extras: Bundle?
    ): Bundle? {
        val result: Bundle?
        result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            provider.call(
                (AttributionSource.Builder(uid)).setPackageName(callingPkg).build(),
                authority,
                method,
                arg,
                extras
            )
        } else if (Build.VERSION.SDK_INT >= 30) {
            provider.call(callingPkg, null as String?, authority, method, arg, extras)
        } else if (Build.VERSION.SDK_INT >= 29) {
            provider.call(callingPkg, authority, method, arg, extras)
        } else {
            provider.call(callingPkg, method, arg, extras)
        }

        return result
    }
}