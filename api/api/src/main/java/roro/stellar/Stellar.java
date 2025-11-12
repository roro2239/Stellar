package roro.stellar;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;
import static roro.stellar.StellarApiConstants.ATTACH_APPLICATION_API_VERSION;
import static roro.stellar.StellarApiConstants.ATTACH_APPLICATION_PACKAGE_NAME;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_PERMISSION_GRANTED;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_SERVER_PATCH_VERSION;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_SERVER_SECONTEXT;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_SERVER_UID;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_SERVER_VERSION;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE;
import static roro.stellar.StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.stellar.server.IStellarApplication;
import com.stellar.server.IStellarService;

/**
 * Stellar API 核心类
 * Stellar API Core Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>提供与Stellar服务的通信接口 - Provides communication interface with Stellar service</li>
 * <li>管理Binder生命周期和监听器 - Manages Binder lifecycle and listeners</li>
 * <li>处理权限请求和状态检查 - Handles permission requests and status checks</li>
 * <li>支持远程进程执行 - Supports remote process execution</li>
 * <li>提供系统服务访问能力 - Provides system service access capabilities</li>
 * </ul>
 * 
 * <p>使用流程 Usage Flow：</p>
 * <ol>
 * <li>添加Binder接收监听器 - Add binder received listener</li>
 * <li>等待服务连接 - Wait for service connection</li>
 * <li>检查权限状态 - Check permission status</li>
 * <li>调用API方法 - Call API methods</li>
 * </ol>
 * 
 * <p>注意事项 Notes：</p>
 * <ul>
 * <li>所有API调用必须在Binder就绪后进行 - All API calls must be made after Binder is ready</li>
 * <li>监听器默认在主线程回调 - Listeners callback on main thread by default</li>
 * <li>支持自定义Handler以在特定线程回调 - Supports custom Handler for specific thread callbacks</li>
 * </ul>
 */
public class Stellar {

    // ============================================
    // 服务连接状态 Service Connection State
    // ============================================
    
    /** 服务Binder对象 Service Binder object */
    private static IBinder binder;
    /** Stellar服务接口 Stellar service interface */
    private static IStellarService service;

    /** 服务端UID Server UID */
    private static int serverUid = -1;
    /** 服务端API版本 Server API version */
    private static int serverApiVersion = -1;
    /** 服务端补丁版本 Server patch version */
    private static int serverPatchVersion = -1;
    /** 服务端SELinux上下文 Server SELinux context */
    private static String serverContext = null;
    /** 权限是否已授予 Permission granted flag */
    private static boolean permissionGranted = false;
    /** 是否应显示权限说明 Should show permission rationale */
    private static boolean shouldShowRequestPermissionRationale = false;

    /** Binder是否就绪 Binder ready flag */
    private static boolean binderReady = false;

    // ============================================
    // 应用回调接口实现 Application Callback Interface
    // ============================================
    
    /**
     * Stellar应用回调接口实现
     * 用于接收服务端的回调通知
     */
    private static final IStellarApplication Stellar_APPLICATION = new IStellarApplication.Stub() {

        /**
         * 绑定应用回调
         * 服务端在连接建立后调用此方法，传递服务器信息和权限状态
         */
        @Override
        public void bindApplication(Bundle data) {
            // 解析服务器信息
            serverUid = data.getInt(BIND_APPLICATION_SERVER_UID, -1);
            serverApiVersion = data.getInt(BIND_APPLICATION_SERVER_VERSION, -1);
            serverPatchVersion = data.getInt(BIND_APPLICATION_SERVER_PATCH_VERSION, -1);
            serverContext = data.getString(BIND_APPLICATION_SERVER_SECONTEXT);
            permissionGranted = data.getBoolean(BIND_APPLICATION_PERMISSION_GRANTED, false);
            shouldShowRequestPermissionRationale = data.getBoolean(BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, false);

            // 通知Binder就绪监听器
            scheduleBinderReceivedListeners();
        }

        /**
         * 分发权限请求结果
         * 服务端在处理完权限请求后调用此方法
         */
        @Override
        public void dispatchRequestPermissionResult(int requestCode, Bundle data) {
            boolean allowed = data.getBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, false);
            scheduleRequestPermissionResultListener(requestCode, allowed ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);
        }
    };

    /**
     * Binder死亡通知接收器
     * 当服务端进程死亡时，系统会调用此方法
     */
    private static final IBinder.DeathRecipient DEATH_RECIPIENT = () -> {
        binderReady = false;
        onBinderReceived(null, null);
    };

    /**
     * 附加应用到Stellar服务（V13版本协议）
     * Attach application to Stellar service (V13 protocol)
     * 
     * @param binder 服务Binder对象
     * @param packageName 应用包名
     * @return 是否成功附加
     * @throws RemoteException 远程调用异常
     */
    private static boolean attachApplicationV13(IBinder binder, String packageName) throws RemoteException {
        boolean result;

        // 准备参数
        Bundle args = new Bundle();
        args.putInt(ATTACH_APPLICATION_API_VERSION, StellarApiConstants.SERVER_VERSION);
        args.putString(ATTACH_APPLICATION_PACKAGE_NAME, packageName);

        // 构造Binder事务
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("com.stellar.server.IStellarService");
            data.writeStrongBinder(Stellar_APPLICATION.asBinder());
            data.writeInt(1);
            args.writeToParcel(data, 0);
            // 执行Binder事务（18 = TRANSACTION_attachApplication）
            result = binder.transact(18 /*IStellarService.Stub.TRANSACTION_attachApplication*/, data, reply, 0);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }

        return result;
    }



    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public static void onBinderReceived(@Nullable IBinder newBinder, String packageName) {
        if (binder == newBinder) return;

        if (newBinder == null) {
            binder = null;
            service = null;
            serverUid = -1;
            serverApiVersion = -1;
            serverContext = null;

            scheduleBinderDeadListeners();
        } else {
            if (binder != null) {
                binder.unlinkToDeath(DEATH_RECIPIENT, 0);
            }
            binder = newBinder;
            service = IStellarService.Stub.asInterface(newBinder);

            try {
                binder.linkToDeath(DEATH_RECIPIENT, 0);
            } catch (Throwable e) {
                Log.i("StellarApplication", "attachApplication");
            }

            try {
                attachApplicationV13(binder, packageName);
                Log.i("StellarApplication", "附加应用程序");
            } catch (Throwable e) {
                Log.w("StellarApplication", Log.getStackTraceString(e));
            }
            
            binderReady = true;
            scheduleBinderReceivedListeners();
        }
    }

    public interface OnBinderReceivedListener {
        void onBinderReceived();
    }

    public interface OnBinderDeadListener {
        void onBinderDead();
    }

    public interface OnRequestPermissionResultListener {

        /**
         * Callback for the result from requesting permission.
         *
         * @param requestCode The code passed in {@link #requestPermission(int)}.
         * @param grantResult The grant result for which is either {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
         *                    or {@link android.content.pm.PackageManager#PERMISSION_DENIED}.
         */
        void onRequestPermissionResult(int requestCode, int grantResult);
    }

    private static class ListenerHolder<T> {

        private final T listener;
        private final Handler handler;

        private ListenerHolder(@NonNull T listener, @Nullable Handler handler) {
            this.listener = listener;
            this.handler = handler;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListenerHolder<?> that = (ListenerHolder<?>) o;
            return Objects.equals(listener, that.listener) && Objects.equals(handler, that.handler);
        }

        @Override
        public int hashCode() {
            return Objects.hash(listener, handler);
        }
    }

    private static final List<ListenerHolder<OnBinderReceivedListener>> RECEIVED_LISTENERS = new ArrayList<>();
    private static final List<ListenerHolder<OnBinderDeadListener>> DEAD_LISTENERS = new ArrayList<>();
    private static final List<ListenerHolder<OnRequestPermissionResultListener>> PERMISSION_LISTENERS = new ArrayList<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * Add a listener that will be called when binder is received.
     * <p>
     * Stellar APIs can only be used when the binder is received, or a
     * {@link IllegalStateException} will be thrown.
     *
     * <p>Note:</p>
     * <ul>
     * <li>The listener will be called in main thread.</li>
     * <li>The listener could be called multiply times. For example, user restarts Stellar when app is running.</li>
     * </ul>
     * <p>
     *
     * @param listener OnBinderReceivedListener
     */
    public static void addBinderReceivedListener(@NonNull OnBinderReceivedListener listener) {
        addBinderReceivedListener(listener, null);
    }

    /**
     * Add a listener that will be called when binder is received.
     * <p>
     * Stellar APIs can only be used when the binder is received, or a
     * {@link IllegalStateException} will be thrown.
     *
     * <p>Note:</p>
     * <ul>
     * <li>The listener could be called multiply times. For example, user restarts Stellar when app is running.</li>
     * </ul>
     * <p>
     *
     * @param listener OnBinderReceivedListener
     * @param handler  Where the listener would be called. If null, the listener will be called in main thread.
     */
    public static void addBinderReceivedListener(@NonNull OnBinderReceivedListener listener, @Nullable Handler handler) {
        addBinderReceivedListener(Objects.requireNonNull(listener), false, handler);
    }

    /**
     * Same to {@link #addBinderReceivedListener(OnBinderReceivedListener)} but only call the listener
     * immediately if the binder is already received.
     *
     * @param listener OnBinderReceivedListener
     */
    public static void addBinderReceivedListenerSticky(@NonNull OnBinderReceivedListener listener) {
        addBinderReceivedListenerSticky(Objects.requireNonNull(listener), null);
    }

    /**
     * Same to {@link #addBinderReceivedListener(OnBinderReceivedListener)} but only call the listener
     * immediately if the binder is already received.
     *
     * @param listener OnBinderReceivedListener
     * @param handler  Where the listener would be called. If null, the listener will be called in main thread.
     */
    public static void addBinderReceivedListenerSticky(@NonNull OnBinderReceivedListener listener, @Nullable Handler handler) {
        addBinderReceivedListener(Objects.requireNonNull(listener), true, handler);
    }

    private static void addBinderReceivedListener(@NonNull OnBinderReceivedListener listener, boolean sticky, @Nullable Handler handler) {
        if (sticky && binderReady) {
            if (handler != null) {
                handler.post(listener::onBinderReceived);
            } else if (Looper.myLooper() == Looper.getMainLooper()) {
                listener.onBinderReceived();
            } else {
                MAIN_HANDLER.post(listener::onBinderReceived);
            }
        }
        synchronized (RECEIVED_LISTENERS) {
            RECEIVED_LISTENERS.add(new ListenerHolder<>(listener, handler));
        }
    }

    /**
     * Remove the listener added by {@link #addBinderReceivedListener(OnBinderReceivedListener)}
     * or {@link #addBinderReceivedListenerSticky(OnBinderReceivedListener)}.
     *
     * @param listener OnBinderReceivedListener
     * @return If the listener is removed.
     */
    public static boolean removeBinderReceivedListener(@NonNull OnBinderReceivedListener listener) {
        synchronized (RECEIVED_LISTENERS) {
            return RECEIVED_LISTENERS.removeIf(holder -> holder.listener == listener);
        }
    }

    private static void scheduleBinderReceivedListeners() {
        synchronized (RECEIVED_LISTENERS) {
            for (ListenerHolder<OnBinderReceivedListener> holder : RECEIVED_LISTENERS) {
                if (holder.handler != null) {
                    holder.handler.post(holder.listener::onBinderReceived);
                } else {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        holder.listener.onBinderReceived();
                    } else {
                        MAIN_HANDLER.post(holder.listener::onBinderReceived);
                    }
                }
            }
        }
        binderReady = true;
    }

    /**
     * Add a listener that will be called when binder is dead.
     * <p>Note:</p>
     * <ul>
     * <li>The listener will be called in main thread.</li>
     * </ul>
     * <p>
     *
     * @param listener OnBinderReceivedListener
     */
    public static void addBinderDeadListener(@NonNull OnBinderDeadListener listener) {
        addBinderDeadListener(listener, null);
    }

    /**
     * Add a listener that will be called when binder is dead.
     *
     * @param listener OnBinderReceivedListener
     * @param handler  Where the listener would be called. If null, the listener will be called in main thread.
     */
    public static void addBinderDeadListener(@NonNull OnBinderDeadListener listener, @Nullable Handler handler) {
        synchronized (RECEIVED_LISTENERS) {
            DEAD_LISTENERS.add(new ListenerHolder<>(listener, handler));
        }
    }

    /**
     * Remove the listener added by {@link #addBinderDeadListener(OnBinderDeadListener)}.
     *
     * @param listener OnBinderDeadListener
     * @return If the listener is removed.
     */
    public static boolean removeBinderDeadListener(@NonNull OnBinderDeadListener listener) {
        synchronized (RECEIVED_LISTENERS) {
            return DEAD_LISTENERS.removeIf(holder -> holder.listener == listener);
        }
    }

    private static void scheduleBinderDeadListeners() {
        synchronized (RECEIVED_LISTENERS) {
            for (ListenerHolder<OnBinderDeadListener> holder : DEAD_LISTENERS) {
                if (holder.handler != null) {
                    holder.handler.post(holder.listener::onBinderDead);
                } else {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        holder.listener.onBinderDead();
                    } else {
                        MAIN_HANDLER.post(holder.listener::onBinderDead);
                    }
                }

            }
        }
    }

    /**
     * Add a listener to receive the result of {@link #requestPermission(int)}.
     * <p>Note:</p>
     * <ul>
     * <li>The listener will be called in main thread.</li>
     * </ul>
     * <p>
     *
     * @param listener OnBinderReceivedListener
     */
    public static void addRequestPermissionResultListener(@NonNull OnRequestPermissionResultListener listener) {
        addRequestPermissionResultListener(listener, null);
    }

    /**
     * Add a listener to receive the result of {@link #requestPermission(int)}.
     *
     * @param listener OnBinderReceivedListener
     * @param handler  Where the listener would be called. If null, the listener will be called in main thread.
     */
    public static void addRequestPermissionResultListener(@NonNull OnRequestPermissionResultListener listener, @Nullable Handler handler) {
        synchronized (RECEIVED_LISTENERS) {
            PERMISSION_LISTENERS.add(new ListenerHolder<>(listener, handler));
        }
    }

    /**
     * Remove the listener added by {@link #addRequestPermissionResultListener(OnRequestPermissionResultListener)}.
     *
     * @param listener OnRequestPermissionResultListener
     * @return If the listener is removed.
     */
    public static boolean removeRequestPermissionResultListener(@NonNull OnRequestPermissionResultListener listener) {
        synchronized (RECEIVED_LISTENERS) {
            return PERMISSION_LISTENERS.removeIf(holder -> holder.listener == listener);
        }
    }

    private static void scheduleRequestPermissionResultListener(int requestCode, int result) {
        synchronized (RECEIVED_LISTENERS) {
            for (ListenerHolder<OnRequestPermissionResultListener> holder : PERMISSION_LISTENERS) {
                if (holder.handler != null) {
                    holder.handler.post(() -> holder.listener.onRequestPermissionResult(requestCode, result));
                } else {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        holder.listener.onRequestPermissionResult(requestCode, result);
                    } else {
                        MAIN_HANDLER.post(() -> holder.listener.onRequestPermissionResult(requestCode, result));
                    }
                }
            }
        }
    }

    @NonNull
    protected static IStellarService requireService() {
        if (service == null) {
            throw new IllegalStateException("尚未接收到 binder");
        }
        return service;
    }

    /**
     * Get the binder.
     * <p>
     * Normal apps should not use this method.
     */
    @Nullable
    public static IBinder getBinder() {
        return binder;
    }

    /**
     * Check if the binder is alive.
     * <p>
     * Normal apps should use listeners rather calling this method everytime.
     *
     * @see #addBinderReceivedListener(OnBinderReceivedListener)
     * @see #addBinderReceivedListenerSticky(OnBinderReceivedListener)
     * @see #addBinderDeadListener(OnBinderDeadListener)
     */
    public static boolean pingBinder() {
        return binder != null && binder.pingBinder();
    }

    private static RuntimeException rethrowAsRuntimeException(RemoteException e) {
        return new RuntimeException(e);
    }

    /**
     * Call {@link IBinder#transact(int, Parcel, Parcel, int)} at remote service.
     * <p>
     * Use {@link StellarBinderWrapper} to wrap the original binder.
     *
     * @see StellarBinderWrapper
     */
    public static void transactRemote(@NonNull Parcel data, @Nullable Parcel reply, int flags) {
        try {
            requireService().asBinder().transact(StellarApiConstants.BINDER_TRANSACTION_transact, data, reply, flags);
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
    }

    /**
     * Start a new process at remote service, parameters are passed to {@link Runtime#exec(String, String[], java.io.File)}.
     * <br>From version 11, like "su", the process will be killed when the caller process is dead. If you have complicated
     * requirements, use alternative methods.
     * <p>
     * Note, you may need to read/write streams from RemoteProcess in different threads.
     * </p>
     *
     * @return RemoteProcess holds the binder of remote process
     */
    public static StellarRemoteProcess newProcess(@NonNull String[] cmd, @Nullable String[] env, @Nullable String dir) {
        try {
            return new StellarRemoteProcess(requireService().newProcess(cmd, env, dir));
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
    }

    /**
     * Returns uid of remote service.
     *
     * @return uid
     * @throws IllegalStateException if called before binder is received
     */
    public static int getUid() {
        if (serverUid != -1) return serverUid;
        try {
            serverUid = requireService().getUid();
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        } catch (SecurityException e) {
            // Stellar pre-v11 and permission is not granted
            return -1;
        }
        return serverUid;
    }

    /**
     * Returns remote service version.
     *
     * @return server version
     */
    public static int getVersion() {
        if (serverApiVersion != -1) return serverApiVersion;
        try {
            serverApiVersion = requireService().getVersion();
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        } catch (SecurityException e) {
            // Stellar pre-v11 and permission is not granted
            return -1;
        }
        return serverApiVersion;
    }



    /**
     * Return latest service version when this library was released.
     *
     * @return Latest service version
     * @see Stellar#getVersion()
     */
    public static int getLatestServiceVersion() {
        return StellarApiConstants.SERVER_VERSION;
    }

    /**
     * Returns SELinux context of Stellar server process.
     *
     * <p>For adb, context should always be <code>u:r:shell:s0</code>.
     * <br>For root, context depends on su the user uses. E.g., context of Magisk is <code>u:r:magisk:s0</code>.
     * If the user's su does not allow binder calls between su and app, Stellar will switch to context <code>u:r:shell:s0</code>.
     * </p>
     *
     * @return SELinux context
     * @since Added from version 6
     */
    public static String getSELinuxContext() {
        if (serverContext != null) return serverContext;
        try {
            serverContext = requireService().getSELinuxContext();
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        } catch (SecurityException e) {
            // Stellar pre-v11 and permission is not granted
            return null;
        }
        return serverContext;
    }

    /**
     * Returns Manager app version name.
     *
     * @return Version name string (e.g. "1.0.0")
     * @throws IllegalStateException if called before binder is received
     */
    public static String getVersionName() {
        try {
            return requireService().getVersionName();
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
    }

    /**
     * Returns Manager app version code.
     *
     * @return Version code
     * @throws IllegalStateException if called before binder is received
     */
    public static int getVersionCode() {
        try {
            return requireService().getVersionCode();
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
    }









    /**
     * Check if remote service has specific permission.
     *
     * @param permission permission name
     * @return PackageManager.PERMISSION_DENIED or PackageManager.PERMISSION_GRANTED
     */
    public static int checkRemotePermission(String permission) {
        if (serverUid == 0) return PackageManager.PERMISSION_GRANTED;
        try {
            return requireService().checkPermission(permission);
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
    }

    /**
     * Request permission.
     * <p>
     * Different from runtime permission, you need to add a listener to receive
     * the result.
     *
     * @param requestCode Application specific request code to match with a result
     *                    reported to {@link OnRequestPermissionResultListener#onRequestPermissionResult(int, int)}.
     * @see #addRequestPermissionResultListener(OnRequestPermissionResultListener)
     * @see #removeRequestPermissionResultListener(OnRequestPermissionResultListener)
     * @since Added from version 11
     */
    public static void requestPermission(int requestCode) {
        try {
            requireService().requestPermission(requestCode);
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
    }

    /**
     * Check if self has permission.
     *
     * @return Either {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
     * or {@link android.content.pm.PackageManager#PERMISSION_DENIED}.
     * @since Added from version 11
     */
    public static int checkSelfPermission() {
        if (permissionGranted) return PackageManager.PERMISSION_GRANTED;
        try {
            permissionGranted = requireService().checkSelfPermission();
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
        return permissionGranted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
    }

    /**
     * Should show UI with rationale before requesting the permission.
     *
     * @since Added from version 11
     */
    public static boolean shouldShowRequestPermissionRationale() {
        if (permissionGranted) return false;
        if (shouldShowRequestPermissionRationale) return true;
        try {
            shouldShowRequestPermissionRationale = requireService().shouldShowRequestPermissionRationale();
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
        return shouldShowRequestPermissionRationale;
    }

    // --------------------- non-app ----------------------

    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public static void exit() {
        try {
            requireService().exit();
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
    }



    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public static void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode, @NonNull Bundle data) {
        try {
            requireService().dispatchPermissionConfirmationResult(requestUid, requestPid, requestCode, data);
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
    }

    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public static int getFlagsForUid(int uid, int mask) {
        try {
            return requireService().getFlagsForUid(uid, mask);
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
    }

    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public static void updateFlagsForUid(int uid, int mask, int value) {
        try {
            requireService().updateFlagsForUid(uid, mask, value);
        } catch (RemoteException e) {
            throw rethrowAsRuntimeException(e);
        }
    }

    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public static int getServerPatchVersion() {
        return serverPatchVersion;
    }

}

