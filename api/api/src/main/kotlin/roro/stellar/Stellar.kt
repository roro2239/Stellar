package roro.stellar

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import androidx.annotation.RestrictTo
import com.stellar.server.IStellarApplication
import com.stellar.server.IStellarService
import roro.stellar.Stellar.addBinderDeadListener
import roro.stellar.Stellar.addBinderReceivedListener
import roro.stellar.Stellar.addBinderReceivedListenerSticky
import roro.stellar.Stellar.addRequestPermissionResultListener
import roro.stellar.Stellar.removeRequestPermissionResultListener
import roro.stellar.Stellar.requestPermission
import java.util.Objects

object Stellar {
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    var binder: IBinder? = null

    private var service: IStellarService? = null

    private var serverUid = -1

    private var serverApiVersion = -1

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    var serverPatchVersion: Int = -1

    private var serverContext: String? = null

    private var permissionGranted = false

    private var shouldShowRequestPermissionRationale = false

    private var binderReady = false

    private var packageName: String? = null


    private val Stellar_APPLICATION: IStellarApplication = object : IStellarApplication.Stub() {
        override fun bindApplication(data: Bundle) {
            serverUid = data.getInt(StellarApiConstants.BIND_APPLICATION_SERVER_UID, -1)
            serverApiVersion = data.getInt(StellarApiConstants.BIND_APPLICATION_SERVER_VERSION, -1)
            serverPatchVersion =
                data.getInt(StellarApiConstants.BIND_APPLICATION_SERVER_PATCH_VERSION, -1)
            serverContext = data.getString(StellarApiConstants.BIND_APPLICATION_SERVER_SECONTEXT)
            permissionGranted =
                data.getBoolean(StellarApiConstants.BIND_APPLICATION_PERMISSION_GRANTED, false)
            shouldShowRequestPermissionRationale = data.getBoolean(
                StellarApiConstants.BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE,
                false
            )

            scheduleBinderReceivedListeners()
        }

        override fun dispatchRequestPermissionResult(requestCode: Int, data: Bundle) {
            val allowed =
                data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, false)
            val onetime =
                data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, false)
            scheduleRequestPermissionResultListener(
                requestCode,
                allowed,
                onetime
            )
        }
    }

    private val DEATH_RECIPIENT = DeathRecipient {
        binderReady = false
        onBinderReceived(null, null)
    }

    @Throws(RemoteException::class)
    private fun attachApplication(binder: IBinder, packageName: String?): Boolean {
        var result: Boolean

        val args = Bundle()
        args.putInt(
            StellarApiConstants.ATTACH_APPLICATION_API_VERSION,
            StellarApiConstants.SERVER_VERSION
        )
        args.putString(StellarApiConstants.ATTACH_APPLICATION_PACKAGE_NAME, packageName)

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("com.stellar.server.IStellarService")
            data.writeStrongBinder(Stellar_APPLICATION.asBinder())
            data.writeInt(1)
            args.writeToParcel(data, 0)
            result = binder.transact(
                18,
                data,
                reply,
                0
            )
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }

        return result
    }


    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    fun onBinderReceived(newBinder: IBinder?, packageName: String?) {
        if (binder === newBinder) return

        if (newBinder == null) {
            binder = null
            service = null
            serverUid = -1
            serverApiVersion = -1
            serverContext = null
            this.packageName = null

            scheduleBinderDeadListeners()
        } else {
            if (binder != null) {
                binder!!.unlinkToDeath(DEATH_RECIPIENT, 0)
            }
            binder = newBinder
            service = IStellarService.Stub.asInterface(newBinder)
            this.packageName = packageName

            try {
                binder!!.linkToDeath(DEATH_RECIPIENT, 0)
            } catch (_: Throwable) {
            }

            try {
                attachApplication(binder!!, packageName)
            } catch (e: Throwable) {
                Log.w("StellarApplication", Log.getStackTraceString(e))
            }

            binderReady = true
            scheduleBinderReceivedListeners()
        }
    }

    private val RECEIVED_LISTENERS: MutableList<ListenerHolder<OnBinderReceivedListener>> =
        ArrayList<ListenerHolder<OnBinderReceivedListener>>()
    private val DEAD_LISTENERS: MutableList<ListenerHolder<OnBinderDeadListener>> =
        ArrayList<ListenerHolder<OnBinderDeadListener>>()
    private val PERMISSION_LISTENERS: MutableList<ListenerHolder<OnRequestPermissionResultListener>> =
        ArrayList<ListenerHolder<OnRequestPermissionResultListener>>()
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())


    fun addBinderReceivedListener(
        listener: OnBinderReceivedListener,
        handler: Handler? = null
    ) {
        addBinderReceivedListener(
            listener,
            false,
            handler
        )
    }

    fun addBinderReceivedListenerSticky(
        listener: OnBinderReceivedListener,
        handler: Handler? = null
    ) {
        addBinderReceivedListener(
            listener = listener,
            sticky = true,
            handler = handler
        )
    }

    private fun addBinderReceivedListener(
        listener: OnBinderReceivedListener,
        sticky: Boolean,
        handler: Handler?
    ) {
        if (sticky && binderReady) {
            if (handler != null) {
                handler.post { listener.onBinderReceived() }
            } else if (Looper.myLooper() == Looper.getMainLooper()) {
                listener.onBinderReceived()
            } else {
                MAIN_HANDLER.post { listener.onBinderReceived() }
            }
        }
        synchronized(RECEIVED_LISTENERS) {
            RECEIVED_LISTENERS.add(ListenerHolder(listener, handler))
        }
    }

    fun removeBinderReceivedListener(listener: OnBinderReceivedListener): Boolean {
        synchronized(RECEIVED_LISTENERS) {
            return RECEIVED_LISTENERS.removeIf { it.listener === listener }
        }
    }

    private fun scheduleBinderReceivedListeners() {
        synchronized(RECEIVED_LISTENERS) {
            for (holder in RECEIVED_LISTENERS) {
                if (holder.handler != null) {
                    holder.handler.post { holder.listener.onBinderReceived() }
                } else {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        holder.listener.onBinderReceived()
                    } else {
                        MAIN_HANDLER.post { holder.listener.onBinderReceived() }
                    }
                }
            }
        }
        binderReady = true
    }

    fun addBinderDeadListener(listener: OnBinderDeadListener, handler: Handler? = null) {
        synchronized(RECEIVED_LISTENERS) {
            DEAD_LISTENERS.add(ListenerHolder(listener, handler))
        }
    }

    fun removeBinderDeadListener(listener: OnBinderDeadListener): Boolean {
        synchronized(RECEIVED_LISTENERS) {
            return DEAD_LISTENERS.removeIf { it.listener === listener }
        }
    }

    private fun scheduleBinderDeadListeners() {
        synchronized(RECEIVED_LISTENERS) {
            for (holder in DEAD_LISTENERS) {
                if (holder.handler != null) {
                    holder.handler.post { holder.listener.onBinderDead() }
                } else {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        holder.listener.onBinderDead()
                    } else {
                        MAIN_HANDLER.post { holder.listener.onBinderDead() }
                    }
                }
            }
        }
    }

    @JvmOverloads
    fun addRequestPermissionResultListener(
        listener: OnRequestPermissionResultListener,
        handler: Handler? = null
    ) {
        synchronized(RECEIVED_LISTENERS) {
            PERMISSION_LISTENERS.add(
                ListenerHolder(
                    listener,
                    handler
                )
            )
        }
    }

    fun removeRequestPermissionResultListener(listener: OnRequestPermissionResultListener): Boolean {
        synchronized(RECEIVED_LISTENERS) {
            return PERMISSION_LISTENERS.removeIf { it.listener === listener }
        }
    }

    private fun scheduleRequestPermissionResultListener(requestCode: Int, allowed: Boolean, onetime: Boolean) {
        synchronized(RECEIVED_LISTENERS) {
            for (holder in PERMISSION_LISTENERS) {
                if (holder.handler != null) {
                    holder.handler.post {
                        holder.listener.onRequestPermissionResult(
                            requestCode,
                            allowed,
                            onetime
                        )
                    }
                } else {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        holder.listener.onRequestPermissionResult(requestCode, allowed, onetime)
                    } else {
                        MAIN_HANDLER.post {
                            holder.listener.onRequestPermissionResult(
                                requestCode,
                                allowed,
                                onetime
                            )
                        }
                    }
                }
            }
        }
    }

    internal fun requireService(): IStellarService {
        checkNotNull(service)
        return service!!
    }

    fun getService(): IStellarService? {
        return service
    }

    fun getPackageName(): String? {
        return packageName
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    fun getClientBinder(): IBinder? {
        return Stellar_APPLICATION.asBinder()
    }

    fun pingBinder(): Boolean {
        return binder != null && binder!!.pingBinder()
    }

    private fun rethrowAsRuntimeException(e: RemoteException?): RuntimeException {
        return RuntimeException(e)
    }

    fun transactRemote(data: Parcel, reply: Parcel?, flags: Int) {
        try {
            requireService().asBinder()
                .transact(StellarApiConstants.BINDER_TRANSACTION_transact, data, reply, flags)
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    fun newProcess(cmd: Array<String?>, env: Array<String?>?, dir: String?): StellarRemoteProcess {
        try {
            return StellarRemoteProcess(requireService().newProcess(cmd, env, dir))
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    val uid: Int
        get() {
            if (serverUid != -1) return serverUid
            try {
                serverUid = requireService().getUid()
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            } catch (_: SecurityException) {
                return -1
            }
            return serverUid
        }

    val version: Int
        get() {
            if (serverApiVersion != -1) return serverApiVersion
            try {
                serverApiVersion = requireService().getVersion()
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            } catch (_: SecurityException) {
                return -1
            }
            return serverApiVersion
        }


    val latestServiceVersion: Int
        get() = StellarApiConstants.SERVER_VERSION

    val sELinuxContext: String?
        get() {
            if (serverContext != null) return serverContext
            try {
                serverContext = requireService().getSELinuxContext()
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            } catch (_: SecurityException) {
                return null
            }
            return serverContext
        }

    val versionName: String?
        get() {
            try {
                return requireService().getVersionName()
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            }
        }

    val versionCode: Int
        get() {
            try {
                return requireService().getVersionCode()
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            }
        }


    fun checkRemotePermission(permission: String?): Int {
        if (serverUid == 0) return PackageManager.PERMISSION_GRANTED
        try {
            return requireService().checkPermission(permission)
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    val supportedPermissions: Array<String>
        get() =
            try {
                requireService().supportedPermissions
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            }

    fun requestPermission(permission: String = "stellar", requestCode: Int) {
        try {
            requireService().requestPermission(permission, requestCode)
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    fun checkSelfPermission(permission: String = "stellar"): Boolean {
        return try {
            requireService().checkSelfPermission(permission)
        } catch (_: RemoteException) {
            false
        }
    }

    fun shouldShowRequestPermissionRationale(): Boolean {
        if (permissionGranted) return false
        if (shouldShowRequestPermissionRationale) return true
        try {
            shouldShowRequestPermissionRationale =
                requireService().shouldShowRequestPermissionRationale()
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
        return shouldShowRequestPermissionRationale
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    fun exit() {
        try {
            requireService().exit()
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }


    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    fun dispatchPermissionConfirmationResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: Bundle,
    ) {
        try {
            requireService().dispatchPermissionConfirmationResult(
                requestUid,
                requestPid,
                requestCode,
                data
            )
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    fun getFlagForUid(uid: Int, permission: String): Int {
        try {
            return requireService().getFlagForUid(uid, permission)
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    fun updateFlagForUid(uid: Int, permission: String, flag: Int) {
        try {
            requireService().updateFlagForUid(uid, permission, flag)
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        try {
            requireService().grantRuntimePermission(packageName, permissionName, userId)
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        try {
            requireService().revokeRuntimePermission(packageName, permissionName, userId)
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    fun interface OnBinderReceivedListener {
        fun onBinderReceived()
    }

    fun interface OnBinderDeadListener {
        fun onBinderDead()
    }

    fun interface OnRequestPermissionResultListener {
        fun onRequestPermissionResult(requestCode: Int, allowed: Boolean, onetime: Boolean)
    }

    private class ListenerHolder<T>(val listener: T, val handler: Handler?) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val that = other as ListenerHolder<*>
            return listener == that.listener && handler == that.handler
        }

        override fun hashCode(): Int {
            return Objects.hash(listener, handler)
        }
    }
}
