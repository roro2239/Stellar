package roro.stellar.server;

import static android.app.ActivityManagerHidden.UID_OBSERVER_ACTIVE;
import static android.app.ActivityManagerHidden.UID_OBSERVER_CACHED;
import static android.app.ActivityManagerHidden.UID_OBSERVER_GONE;
import static android.app.ActivityManagerHidden.UID_OBSERVER_IDLE;

import android.app.ActivityManagerHidden;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

import kotlin.collections.ArraysKt;
import rikka.hidden.compat.ActivityManagerApis;
import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.PermissionManagerApis;
import rikka.hidden.compat.adapter.ProcessObserverAdapter;
import rikka.hidden.compat.adapter.UidObserverAdapter;
import roro.stellar.server.util.Logger;

/**
 * Binder发送器
 * Binder Sender
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>自动监听应用启动 - Auto monitors app startup</li>
 * <li>主动向符合条件的应用发送Binder - Proactively sends Binder to eligible apps</li>
 * <li>支持进程和UID两种监听模式 - Supports process and UID monitoring modes</li>
 * <li>区分Manager应用和普通应用 - Distinguishes manager app and normal apps</li>
 * </ul>
 * 
 * <p>工作原理 How It Works：</p>
 * <ul>
 * <li>注册ProcessObserver监听进程前台变化 - Registers ProcessObserver to monitor foreground changes</li>
 * <li>注册UidObserver监听UID状态变化（Android 7.0+） - Registers UidObserver for UID status changes (Android 7.0+)</li>
 * <li>检查应用权限后发送Binder - Sends Binder after checking app permissions</li>
 * </ul>
 */
public class BinderSender {

    private static final Logger LOGGER = new Logger("BinderSender");

    /** Manager权限 Manager permission */
    private static final String PERMISSION_MANAGER = "roro.stellar.manager.permission.MANAGER";
    
    /** API权限 API permission */
    private static final String PERMISSION = "roro.stellar.manager.permission.API_V23";

    /** Stellar服务实例 Stellar service instance */
    private static StellarService sStellarService;

    /**
     * 进程观察者
     * Process Observer
     * 
     * <p>监听进程前台状态变化和进程死亡事件</p>
     */
    private static class ProcessObserver extends ProcessObserverAdapter {

        /** 已处理的PID列表 Processed PID list */
        private static final List<Integer> PID_LIST = new ArrayList<>();

        /**
         * 前台Activity状态变化回调
         * Foreground activity state change callback
         */
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) throws RemoteException {
            LOGGER.d("onForegroundActivitiesChanged: pid=%d, uid=%d, foregroundActivities=%s", pid, uid, foregroundActivities ? "true" : "false");

            synchronized (PID_LIST) {
                if (PID_LIST.contains(pid) || !foregroundActivities) {
                    return;
                }
                PID_LIST.add(pid);
            }

            sendBinder(uid, pid);
        }

        /**
         * 进程死亡回调
         * Process death callback
         * 
         * @param pid 进程ID
         * @param uid 用户ID
         */
        @Override
        public void onProcessDied(int pid, int uid) {
            LOGGER.d("onProcessDied: pid=%d, uid=%d", pid, uid);

            synchronized (PID_LIST) {
                int index = PID_LIST.indexOf(pid);
                if (index != -1) {
                    PID_LIST.remove(index);
                }
            }
        }

        /**
         * 进程状态变化回调
         * Process state change callback
         * 
         * @param pid 进程ID
         * @param uid 用户ID
         * @param procState 进程状态
         * @throws RemoteException 远程调用异常
         */
        @Override
        public void onProcessStateChanged(int pid, int uid, int procState) throws RemoteException {
            LOGGER.d("onProcessStateChanged: pid=%d, uid=%d, procState=%d", pid, uid, procState);

            synchronized (PID_LIST) {
                if (PID_LIST.contains(pid)) {
                    return;
                }
                PID_LIST.add(pid);
            }

            sendBinder(uid, pid);
        }
    }

    /**
     * UID观察者（Android 7.0+）
     * UID Observer (Android 7.0+)
     * 
     * <p>监听UID激活、缓存和死亡状态</p>
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private static class UidObserver extends UidObserverAdapter {

        /** 已处理的UID列表 Processed UID list */
        private static final List<Integer> UID_LIST = new ArrayList<>();

        /**
         * UID激活回调
         * UID active callback
         * 
         * @param uid 用户ID
         * @throws RemoteException 远程调用异常
         */
        @Override
        public void onUidActive(int uid) throws RemoteException {
            LOGGER.d("onUidCachedChanged: uid=%d", uid);

            uidStarts(uid);
        }

        /**
         * UID缓存状态变化回调
         * UID cached state change callback
         * 
         * @param uid 用户ID
         * @param cached 是否已缓存
         * @throws RemoteException 远程调用异常
         */
        @Override
        public void onUidCachedChanged(int uid, boolean cached) throws RemoteException {
            LOGGER.d("onUidCachedChanged: uid=%d, cached=%s", uid, Boolean.toString(cached));

            if (!cached) {
                uidStarts(uid);
            }
        }

        /**
         * UID空闲回调
         * UID idle callback
         * 
         * @param uid 用户ID
         * @param disabled 是否已禁用
         * @throws RemoteException 远程调用异常
         */
        @Override
        public void onUidIdle(int uid, boolean disabled) throws RemoteException {
            LOGGER.d("onUidIdle: uid=%d, disabled=%s", uid, Boolean.toString(disabled));

            uidStarts(uid);
        }

        /**
         * UID死亡回调
         * UID gone callback
         * 
         * @param uid 用户ID
         * @param disabled 是否已禁用
         * @throws RemoteException 远程调用异常
         */
        @Override
        public void onUidGone(int uid, boolean disabled) throws RemoteException {
            LOGGER.d("onUidGone: uid=%d, disabled=%s", uid, Boolean.toString(disabled));

            uidGone(uid);
        }

        /**
         * UID启动处理
         * Handle UID starts
         * 
         * @param uid 用户ID
         * @throws RemoteException 远程调用异常
         */
        private void uidStarts(int uid) throws RemoteException {
            synchronized (UID_LIST) {
                if (UID_LIST.contains(uid)) {
                    LOGGER.v("Uid %d already starts", uid);
                    return;
                }
                UID_LIST.add(uid);
                LOGGER.v("Uid %d starts", uid);
            }

            sendBinder(uid, -1);
        }

        /**
         * UID死亡处理
         * Handle UID gone
         * 
         * @param uid 用户ID
         */
        private void uidGone(int uid) {
            synchronized (UID_LIST) {
                int index = UID_LIST.indexOf(uid);
                if (index != -1) {
                    UID_LIST.remove(index);
                    LOGGER.v("Uid %d dead", uid);
                }
            }
        }
    }

    /**
     * 发送Binder到指定UID的应用
     * Send Binder to app with specified UID
     * 
     * @param uid 应用UID
     * @param pid 进程PID（-1表示使用UID检查权限）
     * @throws RemoteException 远程调用异常
     */
    private static void sendBinder(int uid, int pid) throws RemoteException {
        // 获取UID对应的所有包名
        List<String> packages = PackageManagerApis.getPackagesForUidNoThrow(uid);
        if (packages.isEmpty())
            return;

        LOGGER.d("sendBinder to uid %d: packages=%s", uid, TextUtils.join(", ", packages));

        int userId = uid / 100000;
        for (String packageName : packages) {
            PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(packageName, PackageManager.GET_PERMISSIONS, userId);
            if (pi == null || pi.requestedPermissions == null)
                continue;

            // 检查是否为Manager应用
            if (ArraysKt.contains(pi.requestedPermissions, PERMISSION_MANAGER)) {
                boolean granted;
                if (pid == -1)
                    granted = PermissionManagerApis.checkPermission(PERMISSION_MANAGER, uid) == PackageManager.PERMISSION_GRANTED;
                else
                    granted = ActivityManagerApis.checkPermission(PERMISSION_MANAGER, pid, uid) == PackageManager.PERMISSION_GRANTED;

                if (granted) {
                    StellarService.sendBinderToManger(sStellarService, userId);
                    return;
                }
            } 
            // 检查是否为普通API应用
            else if (ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                StellarService.sendBinderToUserApp(sStellarService, packageName, userId);
                return;
            }
        }
    }

    /**
     * 注册观察者
     * Register observers
     * 
     * @param StellarService Stellar服务实例
     */
    public static void register(StellarService StellarService) {
        sStellarService = StellarService;

        // 注册进程观察者
        try {
            ActivityManagerApis.registerProcessObserver(new ProcessObserver());
        } catch (Throwable tr) {
            LOGGER.e(tr, "registerProcessObserver");
        }

        // 注册UID观察者（Android 8.0+）
        if (Build.VERSION.SDK_INT >= 26) {
            int flags = UID_OBSERVER_GONE | UID_OBSERVER_IDLE | UID_OBSERVER_ACTIVE;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                flags |= UID_OBSERVER_CACHED;
            }
            try {
                ActivityManagerApis.registerUidObserver(new UidObserver(), flags,
                        ActivityManagerHidden.PROCESS_STATE_UNKNOWN,
                        null);
            } catch (Throwable tr) {
                LOGGER.e(tr, "registerUidObserver");
            }
        }
    }
}

