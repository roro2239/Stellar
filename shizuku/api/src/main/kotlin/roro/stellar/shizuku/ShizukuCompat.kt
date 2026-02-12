package roro.stellar.shizuku

import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import moe.shizuku.server.IShizukuApplication
import moe.shizuku.server.IShizukuService

/**
 * Shizuku 兼容层
 * 管理 Shizuku Binder 状态和监听器
 */
object ShizukuCompat {

    private const val TAG = "ShizukuCompat"

    var binder: IBinder? = null
        private set

    private var service: IShizukuService? = null
    private var serverUid = -1
    private var serverVersion = -1
    private var serverContext: String? = null
    private var permissionGranted = false
    private var binderReady = false

    private val receivedListeners = mutableListOf<OnBinderReceivedListener>()
    private val deadListeners = mutableListOf<OnBinderDeadListener>()
    private val permissionListeners = mutableListOf<OnRequestPermissionResultListener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val application = object : IShizukuApplication.Stub() {
        override fun bindApplication(data: Bundle?) {
            if (data == null) return
            serverUid = data.getInt("moe.shizuku.privileged.api.intent.extra.SERVER_UID", -1)
            serverVersion = data.getInt("moe.shizuku.privileged.api.intent.extra.SERVER_VERSION", -1)
            serverContext = data.getString("moe.shizuku.privileged.api.intent.extra.SERVER_SECONTEXT")
            permissionGranted = data.getBoolean("moe.shizuku.privileged.api.intent.extra.PERMISSION_GRANTED", false)
            notifyBinderReceived()
        }

        override fun dispatchRequestPermissionResult(requestCode: Int, data: Bundle?) {
            val allowed = data?.getBoolean("moe.shizuku.privileged.api.intent.extra.ALLOWED", false) ?: false
            notifyPermissionResult(requestCode, allowed)
        }

        override fun showPermissionConfirmation(requestUid: Int, requestPid: Int, requestPackageName: String?, requestCode: Int) {
            // Sui only, not implemented
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        binderReady = false
        onBinderReceived(null, null)
    }

    fun onBinderReceived(newBinder: IBinder?, packageName: String?) {
        if (binder === newBinder) return

        if (newBinder == null) {
            binder = null
            service = null
            serverUid = -1
            serverVersion = -1
            serverContext = null
            notifyBinderDead()
        } else {
            binder?.unlinkToDeath(deathRecipient, 0)
            binder = newBinder
            service = IShizukuService.Stub.asInterface(newBinder)

            try {
                binder?.linkToDeath(deathRecipient, 0)
            } catch (e: Throwable) {
                Log.w(TAG, "linkToDeath failed", e)
            }

            try {
                attachApplication(newBinder, packageName)
            } catch (e: Throwable) {
                Log.w(TAG, "attachApplication failed", e)
            }

            binderReady = true
            notifyBinderReceived()
        }
    }

    @Throws(RemoteException::class)
    private fun attachApplication(binder: IBinder, packageName: String?) {
        val args = Bundle()
        args.putInt("moe.shizuku.privileged.api.intent.extra.API_VERSION", 13)
        args.putString("moe.shizuku.privileged.api.intent.extra.PACKAGE_NAME", packageName)

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeStrongBinder(application.asBinder())
            data.writeInt(1)
            args.writeToParcel(data, 0)
            binder.transact(17, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun notifyBinderReceived() {
        synchronized(receivedListeners) {
            for (listener in receivedListeners) {
                mainHandler.post { listener.onBinderReceived() }
            }
        }
    }

    private fun notifyBinderDead() {
        synchronized(deadListeners) {
            for (listener in deadListeners) {
                mainHandler.post { listener.onBinderDead() }
            }
        }
    }

    private fun notifyPermissionResult(requestCode: Int, allowed: Boolean) {
        synchronized(permissionListeners) {
            for (listener in permissionListeners) {
                mainHandler.post { listener.onRequestPermissionResult(requestCode, allowed) }
            }
        }
    }

    // Public API
    fun pingBinder(): Boolean = binder?.pingBinder() == true
    fun getUid(): Int = serverUid
    fun getVersion(): Int = serverVersion
    fun getSELinuxContext(): String? = serverContext
    fun isPreV11(): Boolean = serverVersion < 11

    fun addBinderReceivedListener(listener: OnBinderReceivedListener) {
        synchronized(receivedListeners) { receivedListeners.add(listener) }
        if (binderReady) mainHandler.post { listener.onBinderReceived() }
    }

    fun removeBinderReceivedListener(listener: OnBinderReceivedListener) {
        synchronized(receivedListeners) { receivedListeners.remove(listener) }
    }

    fun addBinderDeadListener(listener: OnBinderDeadListener) {
        synchronized(deadListeners) { deadListeners.add(listener) }
    }

    fun removeBinderDeadListener(listener: OnBinderDeadListener) {
        synchronized(deadListeners) { deadListeners.remove(listener) }
    }

    fun addRequestPermissionResultListener(listener: OnRequestPermissionResultListener) {
        synchronized(permissionListeners) { permissionListeners.add(listener) }
    }

    fun removeRequestPermissionResultListener(listener: OnRequestPermissionResultListener) {
        synchronized(permissionListeners) { permissionListeners.remove(listener) }
    }

    fun checkSelfPermission(): Int {
        return try {
            if (service?.checkSelfPermission() == true) 0 else -1
        } catch (e: RemoteException) {
            Log.w(TAG, "checkSelfPermission failed", e)
            -1
        }
    }

    fun shouldShowRequestPermissionRationale(): Boolean {
        return try {
            service?.shouldShowRequestPermissionRationale() ?: false
        } catch (e: RemoteException) {
            Log.w(TAG, "shouldShowRequestPermissionRationale failed", e)
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        try {
            service?.requestPermission(requestCode)
        } catch (e: RemoteException) {
            Log.w(TAG, "requestPermission failed", e)
        }
    }

    fun interface OnBinderReceivedListener {
        fun onBinderReceived()
    }

    fun interface OnBinderDeadListener {
        fun onBinderDead()
    }

    fun interface OnRequestPermissionResultListener {
        fun onRequestPermissionResult(requestCode: Int, allowed: Boolean)
    }
}
