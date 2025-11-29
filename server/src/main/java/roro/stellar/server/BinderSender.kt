package roro.stellar.server

import android.app.ActivityManagerHidden
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import android.text.TextUtils
import androidx.annotation.RequiresApi
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.adapter.ProcessObserverAdapter
import rikka.hidden.compat.adapter.UidObserverAdapter
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.util.Logger

/**
 * Binder发送器
 * Binder Sender
 *
 *
 * 功能说明 Features：
 *
 *  * 自动监听应用启动 - Auto monitors app startup
 *  * 主动向符合条件的应用发送Binder - Proactively sends Binder to eligible apps
 *  * 支持进程和UID两种监听模式 - Supports process and UID monitoring modes
 *  * 区分Manager应用和普通应用 - Distinguishes manager app and normal apps
 *
 *
 *
 * 工作原理 How It Works：
 *
 *  * 注册ProcessObserver监听进程前台变化 - Registers ProcessObserver to monitor foreground changes
 *  * 注册UidObserver监听UID状态变化（Android 7.0+） - Registers UidObserver for UID status changes (Android 7.0+)
 *  * 检查应用权限后发送Binder - Sends Binder after checking app permissions
 *
 */
object BinderSender {
    private val LOGGER = Logger("BinderSender")

    /** API权限 API permission  */
    private const val PERMISSION = "roro.stellar.manager.permission.API_V1"

    /** Stellar服务实例 Stellar service instance  */
    private var sStellarService: StellarService? = null

    /**
     * 发送Binder到指定UID的应用
     * Send Binder to app with specified UID
     *
     * @param uid 应用UID
     * @param pid 进程PID（-1表示使用UID检查权限）
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    private fun sendBinder(uid: Int, pid: Int) {
        // 获取UID对应的所有包名
        val packages = PackageManagerApis.getPackagesForUidNoThrow(uid)
        if (packages.isEmpty()) return

        LOGGER.d("sendBinder to uid %d: packages=%s", uid, TextUtils.join(", ", packages))

        val userId = uid / 100000
        for (packageName in packages) {
            val pi = PackageManagerApis.getPackageInfoNoThrow(
                packageName,
                PackageManager.GET_PERMISSIONS.toLong(),
                userId
            )
            if (pi == null || pi.requestedPermissions == null) continue

            // 检查是否为Manager应用
            if (pi.packageName == MANAGER_APPLICATION_ID) {
                StellarService.sendBinderToManger(sStellarService, userId)
            } else if ((pi.requestedPermissions as Array<out String?>).contains(PERMISSION)) {
                StellarService.sendBinderToUserApp(sStellarService, packageName, userId)
            }
            return
        }
    }

    /**
     * 注册观察者
     * Register observers
     *
     * @param stellarService Stellar服务实例
     */
    fun register(stellarService: StellarService?) {
        sStellarService = stellarService

        // 注册进程观察者
        try {
            ActivityManagerApis.registerProcessObserver(ProcessObserver())
        } catch (tr: Throwable) {
            LOGGER.e(tr, "registerProcessObserver")
        }

        // 注册UID观察者（Android 8.0+）
        if (Build.VERSION.SDK_INT >= 26) {
            var flags =
                ActivityManagerHidden.UID_OBSERVER_GONE or ActivityManagerHidden.UID_OBSERVER_IDLE or ActivityManagerHidden.UID_OBSERVER_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                flags = flags or ActivityManagerHidden.UID_OBSERVER_CACHED
            }
            try {
                ActivityManagerApis.registerUidObserver(
                    UidObserver(), flags,
                    ActivityManagerHidden.PROCESS_STATE_UNKNOWN,
                    null
                )
            } catch (tr: Throwable) {
                LOGGER.e(tr, "registerUidObserver")
            }
        }
    }

    /**
     * 进程观察者
     * Process Observer
     *
     *
     * 监听进程前台状态变化和进程死亡事件
     */
    private class ProcessObserver : ProcessObserverAdapter() {
        /**
         * 前台Activity状态变化回调
         * Foreground activity state change callback
         */
        @Throws(RemoteException::class)
        override fun onForegroundActivitiesChanged(
            pid: Int,
            uid: Int,
            foregroundActivities: Boolean
        ) {
            LOGGER.d(
                "onForegroundActivitiesChanged: pid=%d, uid=%d, foregroundActivities=%s",
                pid,
                uid,
                if (foregroundActivities) "true" else "false"
            )

            synchronized(PID_LIST) {
                if (PID_LIST.contains(pid) || !foregroundActivities) {
                    return
                }
                PID_LIST.add(pid)
            }

            sendBinder(uid, pid)
        }

        /**
         * 进程死亡回调
         * Process death callback
         *
         * @param pid 进程ID
         * @param uid 用户ID
         */
        override fun onProcessDied(pid: Int, uid: Int) {
            LOGGER.d("onProcessDied: pid=%d, uid=%d", pid, uid)

            synchronized(PID_LIST) {
                val index = PID_LIST.indexOf(pid)
                if (index != -1) {
                    PID_LIST.removeAt(index)
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
        @Throws(RemoteException::class)
        override fun onProcessStateChanged(pid: Int, uid: Int, procState: Int) {
            LOGGER.d("onProcessStateChanged: pid=%d, uid=%d, procState=%d", pid, uid, procState)

            synchronized(PID_LIST) {
                if (PID_LIST.contains(pid)) {
                    return
                }
                PID_LIST.add(pid)
            }

            sendBinder(uid, pid)
        }

        companion object {
            /** 已处理的PID列表 Processed PID list  */
            private val PID_LIST: MutableList<Int?> = ArrayList<Int?>()
        }
    }

    /**
     * UID观察者（Android 7.0+）
     * UID Observer (Android 7.0+)
     *
     *
     * 监听UID激活、缓存和死亡状态
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private class UidObserver : UidObserverAdapter() {
        /**
         * UID激活回调
         * UID active callback
         *
         * @param uid 用户ID
         * @throws RemoteException 远程调用异常
         */
        @Throws(RemoteException::class)
        override fun onUidActive(uid: Int) {
            LOGGER.d("onUidCachedChanged: uid=%d", uid)

            uidStarts(uid)
        }

        /**
         * UID缓存状态变化回调
         * UID cached state change callback
         *
         * @param uid 用户ID
         * @param cached 是否已缓存
         * @throws RemoteException 远程调用异常
         */
        @Throws(RemoteException::class)
        override fun onUidCachedChanged(uid: Int, cached: Boolean) {
            LOGGER.d("onUidCachedChanged: uid=%d, cached=%s", uid, cached.toString())

            if (!cached) {
                uidStarts(uid)
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
        @Throws(RemoteException::class)
        override fun onUidIdle(uid: Int, disabled: Boolean) {
            LOGGER.d("onUidIdle: uid=%d, disabled=%s", uid, disabled.toString())

            uidStarts(uid)
        }

        /**
         * UID死亡回调
         * UID gone callback
         *
         * @param uid 用户ID
         * @param disabled 是否已禁用
         * @throws RemoteException 远程调用异常
         */
        @Throws(RemoteException::class)
        override fun onUidGone(uid: Int, disabled: Boolean) {
            LOGGER.d("onUidGone: uid=%d, disabled=%s", uid, disabled.toString())

            uidGone(uid)
        }

        /**
         * UID启动处理
         * Handle UID starts
         *
         * @param uid 用户ID
         * @throws RemoteException 远程调用异常
         */
        @Throws(RemoteException::class)
        fun uidStarts(uid: Int) {
            synchronized(UID_LIST) {
                if (UID_LIST.contains(uid)) {
                    LOGGER.v("Uid %d already starts", uid)
                    return
                }
                UID_LIST.add(uid)
                LOGGER.v("Uid %d starts", uid)
            }

            sendBinder(uid, -1)
        }

        /**
         * UID死亡处理
         * Handle UID gone
         *
         * @param uid 用户ID
         */
        fun uidGone(uid: Int) {
            synchronized(UID_LIST) {
                val index = UID_LIST.indexOf(uid)
                if (index != -1) {
                    UID_LIST.removeAt(index)
                    LOGGER.v("Uid %d dead", uid)
                }
            }
        }

        companion object {
            /** 已处理的UID列表 Processed UID list  */
            private val UID_LIST: MutableList<Int?> = ArrayList<Int?>()
        }
    }
}