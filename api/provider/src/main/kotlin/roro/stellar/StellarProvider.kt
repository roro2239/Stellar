package roro.stellar

import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.net.toUri
import com.stellar.api.BinderContainer
import roro.stellar.Stellar.onBinderReceived
import roro.stellar.Stellar.pingBinder

@Suppress("deprecation")
open class StellarProvider : ContentProvider() {
    override fun attachInfo(context: Context?, info: ProviderInfo) {
        super.attachInfo(context, info)

        check(!info.multiprocess) { "android:multiprocess must be false" }

        check(info.exported) { "android:exported must be true" }

        isProviderProcess = true
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) {
            return null
        }

        extras.classLoader = BinderContainer::class.java.getClassLoader()

        val reply = Bundle()
        when (method) {
            METHOD_SEND_BINDER -> {
                handleSendBinder(extras)
            }

            METHOD_GET_BINDER -> {
                if (!handleGetBinder(reply)) {
                    return null
                }
            }

            METHOD_SEND_USER_SERVICE -> {
                return handleSendUserService(extras)
            }
        }
        return reply
    }

    private fun handleSendBinder(extras: Bundle) {
        Log.i(TAG, ", ${Stellar.binder}, ${pingBinder()}")

        if (pingBinder()) {
            Log.w(TAG, "")
            return
        }

        val container = extras.getParcelable<BinderContainer?>(EXTRA_BINDER)
        if (container != null && container.binder != null) {
            Log.i(TAG, "")

            onBinderReceived(container.binder, context!!.packageName)

            if (enableMultiProcess) {
                Log.d(TAG, "")

                val intent = Intent(ACTION_BINDER_RECEIVED)
                    .putExtra(EXTRA_BINDER, container)
                    .setPackage(context!!.packageName)
                context!!.sendBroadcast(intent)
            }
        } else {
            Log.e(TAG, "")
        }
    }

    private fun handleGetBinder(reply: Bundle): Boolean {
        val binder: IBinder? = Stellar.binder
        if (binder == null || !binder.pingBinder()) return false

        reply.putParcelable(EXTRA_BINDER, BinderContainer(binder))
        return true
    }

    private fun handleSendUserService(extras: Bundle): Bundle? {
        Log.i(TAG, "")

        val service = Stellar.getService()
        if (service == null) {
            Log.e(TAG, "")
            return null
        }

        val container = extras.getParcelable<BinderContainer?>(EXTRA_BINDER)
        if (container?.binder == null) {
            Log.e(TAG, "")
            return null
        }

        try {
            service.attachUserService(container.binder, extras)
        } catch (e: Exception) {
            Log.e(TAG, "", e)
            return null
        }

        val reply = Bundle()
        val stellarBinder = Stellar.binder
        if (stellarBinder != null) {
            reply.putParcelable(EXTRA_BINDER, BinderContainer(stellarBinder))
        }
        val clientBinder = Stellar.getClientBinder()
        if (clientBinder != null) {
            reply.putParcelable(EXTRA_CLIENT_BINDER, BinderContainer(clientBinder))
        }
        return reply
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String?>?
    ): Int {
        return 0
    }

    companion object {
        private const val TAG = "StellarProvider"

        const val METHOD_SEND_BINDER: String = "sendBinder"

        const val METHOD_GET_BINDER: String = "getBinder"

        const val METHOD_SEND_USER_SERVICE: String = "sendUserService"

        const val ACTION_BINDER_RECEIVED: String = "com.stellar.api.action.BINDER_RECEIVED"

        private const val EXTRA_BINDER = "roro.stellar.manager.intent.extra.BINDER"
        private const val EXTRA_CLIENT_BINDER = "roro.stellar.manager.intent.extra.CLIENT_BINDER"

        const val MANAGER_APPLICATION_ID: String = "roro.stellar.manager"

        private var enableMultiProcess = false

        private var isProviderProcess = false

        fun setIsProviderProcess(isProviderProcess: Boolean) {
            Companion.isProviderProcess = isProviderProcess
        }

        fun enableMultiProcessSupport(isProviderProcess: Boolean) {
            Log.d(
                TAG,
                "" + (if (isProviderProcess) "" else "")
            )

            Companion.isProviderProcess = isProviderProcess
            enableMultiProcess = true
        }

        fun requestBinderForNonProviderProcess(context: Context) {
            if (isProviderProcess) {
                return
            }

            Log.d(TAG, "")

            val receiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val container = intent.getParcelableExtra<BinderContainer?>(EXTRA_BINDER)
                    if (container != null && container.binder != null) {
                        Log.i(TAG, "")
                        onBinderReceived(container.binder, context.packageName)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(ACTION_BINDER_RECEIVED),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(receiver, IntentFilter(ACTION_BINDER_RECEIVED))
            }
            val reply = try {
                context.contentResolver.call(
                    ("content://" + context.packageName + ".stellar").toUri(),
                    METHOD_GET_BINDER, null, Bundle()
                )
            } catch (tr: Throwable) {
                null
            }

            if (reply != null) {
                reply.classLoader = BinderContainer::class.java.getClassLoader()

                val container = reply.getParcelable<BinderContainer?>(EXTRA_BINDER)
                if (container != null && container.binder != null) {
                    Log.i(TAG, "")
                    onBinderReceived(container.binder, context.packageName)
                }
            }
        }
    }
}
