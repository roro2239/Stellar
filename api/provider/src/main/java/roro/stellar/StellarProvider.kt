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

/**
 * Stellar服务提供者
 * Stellar Service Provider
 *
 *
 * 功能说明 Features：
 *
 *  * 接收来自Stellar服务端的Binder - Receives Binder from Stellar server
 *  * 自动处理服务连接 - Automatically handles service connection
 *  * 支持多进程Binder共享 - Supports multi-process Binder sharing
 *
 *
 *
 * 工作原理 How it works：
 *
 *
 * 当应用进程启动时，Stellar服务端（运行在adb/root下）会通过此Provider发送Binder。
 * This provider receives binder from Stellar server. When app process starts,
 * Stellar server (it runs under adb/root) will send the binder to client apps with this provider.
 *
 *
 *
 * 在Manifest中声明Provider In Manifest：
 *
 *
 * Add the provider to your manifest like this:
 *
 * <pre class="prettyprint">&lt;manifest&gt;
 * ...
 * &lt;application&gt;
 * ...
 * &lt;provider
 * android:name="roro.stellar.StellarProvider"
 * android:authorities="${applicationId}.stellar"
 * android:exported="true"
 * android:multiprocess="false"
 * android:permission="android.permission.INTERACT_ACROSS_USERS_FULL"
 * &lt;/provider&gt;
 * ...
 * &lt;/application&gt;
 * &lt;/manifest&gt;</pre>
 *
 *
 * 重要配置说明 Important Configuration Notes：
 *
 *  1. `android:permission` 应该是只授予Shell但普通应用没有的权限
 * （如android.permission.INTERACT_ACROSS_USERS_FULL），这样只有应用本身和Stellar服务端可以访问。
 * Should be a permission that granted to Shell (com.android.shell) but not normal apps,
 * so that it can only be used by the app itself and Stellar server.
 *
 *  1. `android:exported` 必须为`true`，
 * 以便运行在adb下的Stellar服务端可以访问。
 * Must be true so that the provider can be accessed from Stellar server runs under adb.
 *
 *  1. `android:multiprocess` 必须为`false`，
 * 因为Stellar服务端只在应用启动时获取UID。
 * Must be false since Stellar server only gets uid when app starts.
 *
 *
 *
 * 多进程支持 Multi-process Support：
 *
 *
 * 如果应用运行在多个进程中，此Provider可以在进程间共享Binder。
 * If your app runs in multiple processes, this provider also provides the functionality of sharing
 * the binder across processes. See [.enableMultiProcessSupport].
 *
 */
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
        }
        return reply
    }

    /**
     * 处理发送Binder请求
     * Handle send binder request
     *
     *
     * 接收服务端发来的Binder并通知Stellar
     *
     * 如果启用了多进程支持，还会广播Binder到其他进程
     *
     * @param extras 包含Binder的Bundle
     */
    private fun handleSendBinder(extras: Bundle) {
        if (pingBinder()) {
            Log.d(TAG, "sendBinder is called when already a living binder")
            return
        }

        val container = extras.getParcelable<BinderContainer?>(EXTRA_BINDER)
        if (container != null && container.binder != null) {
            Log.d(TAG, "binder received")

            onBinderReceived(container.binder, context!!.packageName)

            if (enableMultiProcess) {
                Log.d(TAG, "broadcast binder")

                val intent = Intent(ACTION_BINDER_RECEIVED)
                    .putExtra(EXTRA_BINDER, container)
                    .setPackage(context!!.packageName)
                context!!.sendBroadcast(intent)
            }
        }
    }

    /**
     * 处理获取Binder请求
     * Handle get binder request
     *
     *
     * 将已存在的Binder返回给调用者
     *
     * @param reply 返回数据Bundle
     * @return true表示成功获取Binder
     */
    private fun handleGetBinder(reply: Bundle): Boolean {
        // Other processes in the same app can read the provider without permission
        val binder: IBinder? = Stellar.binder
        if (binder == null || !binder.pingBinder()) return false

        reply.putParcelable(EXTRA_BINDER, BinderContainer(binder))
        return true
    }

    // no other provider methods
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

        // For receive Binder from Stellar
        const val METHOD_SEND_BINDER: String = "sendBinder"

        // For share Binder between processes
        const val METHOD_GET_BINDER: String = "getBinder"

        const val ACTION_BINDER_RECEIVED: String = "com.stellar.api.action.BINDER_RECEIVED"

        private const val EXTRA_BINDER = "roro.stellar.manager.intent.extra.BINDER"

        const val PERMISSION: String = "roro.stellar.manager.permission.API_V1"

        const val MANAGER_APPLICATION_ID: String = "roro.stellar.manager"

        private var enableMultiProcess = false

        private var isProviderProcess = false

        /**
         * 设置是否为Provider进程
         * Set whether is provider process
         *
         * @param isProviderProcess true表示是Provider进程
         */
        fun setIsProviderProcess(isProviderProcess: Boolean) {
            Companion.isProviderProcess = isProviderProcess
        }

        /**
         * 启用内置多进程支持
         * Enables built-in multi-process support
         *
         *
         * 此方法必须尽早调用（例如在Application的静态块中）
         *
         * This method MUST be called as early as possible (e.g., static block in Application).
         *
         * @param isProviderProcess true表示是Provider进程
         */
        fun enableMultiProcessSupport(isProviderProcess: Boolean) {
            Log.d(
                TAG,
                "Enable built-in multi-process support (from " + (if (isProviderProcess) "provider process" else "non-provider process") + ")"
            )

            Companion.isProviderProcess = isProviderProcess
            enableMultiProcess = true
        }


        /**
         * Require binder for non-provider process, should have [.enableMultiProcessSupport] called first.
         *
         * @param context Context
         */
        fun requestBinderForNonProviderProcess(context: Context) {
            if (isProviderProcess) {
                return
            }

            Log.d(TAG, "request binder in non-provider process")

            val receiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val container = intent.getParcelableExtra<BinderContainer?>(EXTRA_BINDER)
                    if (container != null && container.binder != null) {
                        Log.i(TAG, "binder received from broadcast")
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
                    Log.i(TAG, "Binder received from other process")
                    onBinderReceived(container.binder, context.packageName)
                }
            }
        }
    }
}