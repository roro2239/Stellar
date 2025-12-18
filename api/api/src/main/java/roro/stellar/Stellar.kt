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
    /** 服务Binder对象 Service Binder object  */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    var binder: IBinder? = null

    /** Stellar服务接口 Stellar service interface  */
    private var service: IStellarService? = null

    /** 服务端UID Server UID  */
    private var serverUid = -1

    /** 服务端API版本 Server API version  */
    private var serverApiVersion = -1

    /** 服务端补丁版本 Server patch version  */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    var serverPatchVersion: Int = -1

    /** 服务端SELinux上下文 Server SELinux context  */
    private var serverContext: String? = null

    /** 权限是否已授予 Permission granted flag  */
    private var permissionGranted = false

    /** 是否应显示权限说明 Should show permission rationale  */
    private var shouldShowRequestPermissionRationale = false

    /** Binder是否就绪 Binder ready flag  */
    private var binderReady = false


    // ============================================
    // 应用回调接口实现 Application Callback Interface
    // ============================================
    /**
     * Stellar应用回调接口实现
     * 用于接收服务端的回调通知
     */
    private val Stellar_APPLICATION: IStellarApplication = object : IStellarApplication.Stub() {
        /**
         * 绑定应用回调
         * 服务端在连接建立后调用此方法，传递服务器信息和权限状态
         */
        override fun bindApplication(data: Bundle) {
            // 解析服务器信息
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

            // 通知Binder就绪监听器
            scheduleBinderReceivedListeners()
        }

        /**
         * 分发权限请求结果
         * 服务端在处理完权限请求后调用此方法
         */
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

    /**
     * Binder死亡通知接收器
     * 当服务端进程死亡时，系统会调用此方法
     */
    private val DEATH_RECIPIENT = DeathRecipient {
        binderReady = false
        onBinderReceived(null, null)
    }

    /**
     * 附加应用到Stellar服务
     * Attach application to Stellar service
     *
     * @param binder 服务Binder对象
     * @param packageName 应用包名
     * @return 是否成功附加
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    private fun attachApplication(binder: IBinder, packageName: String?): Boolean {
        var result: Boolean

        // 准备参数
        val args = Bundle()
        args.putInt(
            StellarApiConstants.ATTACH_APPLICATION_API_VERSION,
            StellarApiConstants.SERVER_VERSION
        )
        args.putString(StellarApiConstants.ATTACH_APPLICATION_PACKAGE_NAME, packageName)

        // 构造Binder事务
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("com.stellar.server.IStellarService")
            data.writeStrongBinder(Stellar_APPLICATION.asBinder())
            data.writeInt(1)
            args.writeToParcel(data, 0)
            // 执行Binder事务（18 = TRANSACTION_attachApplication）
            result = binder.transact(
                18,  /*IStellarService.Stub.TRANSACTION_attachApplication*/
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

            scheduleBinderDeadListeners()
        } else {
            if (binder != null) {
                binder!!.unlinkToDeath(DEATH_RECIPIENT, 0)
            }
            binder = newBinder
            service = IStellarService.Stub.asInterface(newBinder)

            try {
                binder!!.linkToDeath(DEATH_RECIPIENT, 0)
            } catch (_: Throwable) {
                Log.i("StellarApplication", "attachApplication")
            }

            try {
                attachApplication(binder!!, packageName)
                Log.i("StellarApplication", "附加应用程序")
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

    /**
     * 添加一个接收到 binder 的监听器.
     *
     *
     * Stellar API 只可以被用于 binder 接收否则将抛出 [IllegalStateException].
     *
     *
     * 注意:
     *
     *  * 这个监听器可以被多次触发. 例: 用户在 app 运行时重启 Stellar.
     *
     *
     *
     *
     * @param listener [OnBinderReceivedListener]
     * @param handler  [Handler] 监听器被触发的地方. 如果为 null, 监听器将在主线程被触发.
     */

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

    /**
     * 与 [addBinderReceivedListener] 相同,
     * 但是如果 binder 已被接收则立即触发监听器.
     *
     * @param listener [OnBinderReceivedListener]
     * @param handler  [Handler] 监听器被触发的地方. 如果为 null, 监听器将在主线程被触发.
     */
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

    /**
     * 移除被 [addBinderReceivedListener] 或 [addBinderReceivedListenerSticky] 添加的监听器.
     *
     * @param listener [OnBinderReceivedListener]
     * @return [Boolean] 监听器是否被移除成功
     */
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

    /**
     * 添加一个 binder 死亡的监听器.
     *
     * @param listener [OnBinderReceivedListener]
     * @param handler [Handler] 监听器被触发的地方. 如果为 null, 监听器将在主线程被触发.
     */

    fun addBinderDeadListener(listener: OnBinderDeadListener, handler: Handler? = null) {
        synchronized(RECEIVED_LISTENERS) {
            DEAD_LISTENERS.add(ListenerHolder(listener, handler))
        }
    }

    /**
     * 移除被 [addBinderDeadListener] 添加的监听器.
     *
     * @param listener [OnBinderDeadListener]
     * @return [Boolean] 监听器是否被移除成功
     */
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

    /**
     * Add a listener to receive the result of [requestPermission].
     *
     * @param listener OnBinderReceivedListener
     * @param handler  Where the listener would be called. If null, the listener will be called in main thread.
     */
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

    /**
     * Remove the listener added by [addRequestPermissionResultListener].
     *
     * @param listener OnRequestPermissionResultListener
     * @return If the listener is removed.
     */
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
        checkNotNull(service) { "尚未接收到 binder" }
        return service!!
    }

    /**
     * Check if the binder is alive.
     *
     *
     * Normal apps should use listeners rather calling this method everytime.
     *
     * @see .addBinderReceivedListener
     * @see .addBinderReceivedListenerSticky
     * @see .addBinderDeadListener
     */
    fun pingBinder(): Boolean {
        return binder != null && binder!!.pingBinder()
    }

    private fun rethrowAsRuntimeException(e: RemoteException?): RuntimeException {
        return RuntimeException(e)
    }

    /**
     * Call [IBinder.transact] at remote service.
     *
     *
     * Use [StellarBinderWrapper] to wrap the original binder.
     *
     * @see StellarBinderWrapper
     */
    fun transactRemote(data: Parcel, reply: Parcel?, flags: Int) {
        try {
            requireService().asBinder()
                .transact(StellarApiConstants.BINDER_TRANSACTION_transact, data, reply, flags)
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    /**
     * Start a new process at remote service, parameters are passed to [Runtime.exec].
     * <br></br>From version 11, like "su", the process will be killed when the caller process is dead. If you have complicated
     * requirements, use alternative methods.
     *
     *
     * Note, you may need to read/write streams from RemoteProcess in different threads.
     *
     *
     * @return RemoteProcess holds the binder of remote process
     */
    fun newProcess(cmd: Array<String?>, env: Array<String?>?, dir: String?): StellarRemoteProcess {
        try {
            return StellarRemoteProcess(requireService().newProcess(cmd, env, dir))
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    val uid: Int
        /**
         * Returns uid of remote service.
         *
         * @return uid
         * @throws IllegalStateException if called before binder is received
         */
        get() {
            if (serverUid != -1) return serverUid
            try {
                serverUid = requireService().getUid()
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            } catch (_: SecurityException) {
                // Stellar pre-v11 and permission is not granted
                return -1
            }
            return serverUid
        }

    val version: Int
        /**
         * Returns remote service version.
         *
         * @return server version
         */
        get() {
            if (serverApiVersion != -1) return serverApiVersion
            try {
                serverApiVersion = requireService().getVersion()
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            } catch (_: SecurityException) {
                // Stellar pre-v11 and permission is not granted
                return -1
            }
            return serverApiVersion
        }


    val latestServiceVersion: Int
        /**
         * Return latest service version when this library was released.
         *
         * @return Latest service version
         * @see Stellar.version
         */
        get() = StellarApiConstants.SERVER_VERSION

    val sELinuxContext: String?
        /**
         * Returns SELinux context of Stellar server process.
         *
         *
         * For adb, context should always be `u:r:shell:s0`.
         * <br></br>For root, context depends on su the user uses. E.g., context of Magisk is `u:r:magisk:s0`.
         * If the user's su does not allow binder calls between su and app, Stellar will switch to context `u:r:shell:s0`.
         *
         *
         * @return SELinux context
         */
        get() {
            if (serverContext != null) return serverContext
            try {
                serverContext = requireService().getSELinuxContext()
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            } catch (_: SecurityException) {
                // Stellar pre-v11 and permission is not granted
                return null
            }
            return serverContext
        }

    val versionName: String?
        /**
         * Returns Manager app version name.
         *
         * @return Version name string (e.g. "1.0.0")
         * @throws IllegalStateException if called before binder is received
         */
        get() {
            try {
                return requireService().getVersionName()
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            }
        }

    val versionCode: Int
        /**
         * Returns Manager app version code.
         *
         * @return Version code
         * @throws IllegalStateException if called before binder is received
         */
        get() {
            try {
                return requireService().getVersionCode()
            } catch (e: RemoteException) {
                throw rethrowAsRuntimeException(e)
            }
        }


    /**
     * Check if remote service has specific permission.
     *
     * @param permission permission name
     * @return PackageManager.PERMISSION_DENIED or PackageManager.PERMISSION_GRANTED
     */
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

    /**
     * 请求 Stellar 的某项权限。
     *
     *
     * 与运行时权限不同，你需要一个监听器来接收授权结果。
     *
     * @param permission
     * @param requestCode 用于匹配报告给 [OnRequestPermissionResultListener.onRequestPermissionResult] 的结果的应用程序特定请求代码。
     * @see [addRequestPermissionResultListener]
     * @see [removeRequestPermissionResultListener]
     */
    fun requestPermission(permission: String = "stellar", requestCode: Int) {
        try {
            requireService().requestPermission(permission, requestCode)
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    /**
     * 检查自身是否有 Stellar 的某项权限。
     *
     * @param permission
     * @return [Boolean]
     */
    fun checkSelfPermission(permission: String = "stellar"): Boolean {
        return try {
            requireService().checkSelfPermission(permission)
        } catch (_: RemoteException) {
            false
        }
    }

    /**
     * Should show UI with rationale before requesting the permission.
     */
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

    // --------------------- non-app ----------------------
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

    /**
     * 授予运行时权限
     * Grant runtime permission to an application
     *
     * @param packageName 应用包名
     * @param permissionName 权限名称
     * @param userId 用户ID
     * @throws RemoteException 远程调用异常
     */
    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        try {
            requireService().grantRuntimePermission(packageName, permissionName, userId)
        } catch (e: RemoteException) {
            throw rethrowAsRuntimeException(e)
        }
    }

    /**
     * 撤销运行时权限
     * Revoke runtime permission from an application
     *
     * @param packageName 应用包名
     * @param permissionName 权限名称
     * @param userId 用户ID
     * @throws RemoteException 远程调用异常
     */
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
        /**
         * 请求权限结果回调.
         *
         * @param requestCode The code passed in [requestPermission].
         *
         * @param allowed
         * @param onetime The grant result
         */
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