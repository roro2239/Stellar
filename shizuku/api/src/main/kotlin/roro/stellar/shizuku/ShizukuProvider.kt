package roro.stellar.shizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Shizuku 兼容 Provider
 * 用于接收来自 Stellar 服务的 Shizuku 兼容 Binder
 */
open class ShizukuProvider : ContentProvider() {

    override fun attachInfo(context: Context?, info: ProviderInfo) {
        super.attachInfo(context, info)
        check(!info.multiprocess) { "android:multiprocess must be false" }
        check(info.exported) { "android:exported must be true" }
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) return null

        extras.classLoader = BinderContainer::class.java.classLoader

        return when (method) {
            METHOD_SEND_BINDER -> {
                handleSendBinder(extras)
                Bundle()
            }
            METHOD_GET_BINDER -> {
                handleGetBinder()
            }
            else -> null
        }
    }

    private fun handleSendBinder(extras: Bundle) {
        val container = extras.getParcelable<BinderContainer>(EXTRA_BINDER)
        if (container?.binder != null) {
            Log.i(TAG, "收到 Shizuku Binder")
            ShizukuCompat.onBinderReceived(container.binder, context!!.packageName)
        }
    }

    private fun handleGetBinder(): Bundle? {
        val binder = ShizukuCompat.binder
        if (binder == null || !binder.pingBinder()) return null

        val reply = Bundle()
        reply.putParcelable(EXTRA_BINDER, BinderContainer(binder))
        return reply
    }

    override fun query(
        uri: Uri, projection: Array<String?>?,
        selection: String?, selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String?>?): Int = 0

    companion object {
        private const val TAG = "ShizukuProvider"
        private const val METHOD_SEND_BINDER = "sendBinder"
        private const val METHOD_GET_BINDER = "getBinder"
        private const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
    }
}
