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
import roro.stellar.StellarApiConstants.PERMISSION_KEY
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.ktx.mainHandler
import roro.stellar.server.util.Logger
import kotlin.system.exitProcess

object BinderSender {
    private val LOGGER = Logger("BinderSender")
    private var stellarService: StellarService? = null
    private var initialManagerUid: Int = -1

    @Throws(RemoteException::class)
    private fun sendBinder(uid: Int, pid: Int) {
        val packages = PackageManagerApis.getPackagesForUidNoThrow(uid)
        if (packages.isEmpty()) {
            LOGGER.w("sendBinder: uid %d 没有关联的包名", uid)
            return
        }

        LOGGER.i("向 uid %d, pid %d 发送 binder: packages=%s", uid, pid, TextUtils.join(", ", packages))

        val userId = uid / 100000
        for (packageName in packages) {
            val pi = PackageManagerApis.getPackageInfoNoThrow(
                packageName,
                PackageManager.GET_META_DATA.toLong(),
                userId
            )

            if (pi == null) {
                LOGGER.w("sendBinder: 无法获取包信息: %s", packageName)
                continue
            }

            if (pi.applicationInfo == null) {
                LOGGER.w("sendBinder: applicationInfo 为 null: %s", packageName)
                continue
            }

            if (pi.packageName == MANAGER_APPLICATION_ID) {
                LOGGER.i("sendBinder: 发送 Binder 到管理器: %s", packageName)
                StellarService.sendBinderToManger(stellarService, userId)
                return
            }

            val metaData = pi.applicationInfo!!.metaData
            if (metaData != null) {
                val permissions = metaData.getString(PERMISSION_KEY, "")
                if (permissions.split(",").contains("stellar")) {
                    LOGGER.i("sendBinder: 发送 Binder 到用户应用: %s (通过 metaData)", packageName)
                    StellarService.sendBinderToUserApp(stellarService, packageName, userId)
                    return
                }

                // 检查是否支持 Shizuku
                if (metaData.getBoolean("moe.shizuku.client.V3_SUPPORT", false)) {
                    LOGGER.i("sendBinder: 发送 Shizuku Binder 到应用: %s", packageName)
                    StellarService.sendShizukuBinderToUserApp(stellarService, packageName, userId)
                    return
                }

                LOGGER.d("sendBinder: 包 %s 的 metaData 不包含 stellar 或 shizuku 权限", packageName)
            } else {
                LOGGER.w("sendBinder: 包 %s 的 metaData 为 null，跳过", packageName)
            }
        }

        LOGGER.w("sendBinder: uid %d 的所有包都不满足条件，未发送 Binder", uid)
    }

    fun register(stellarService: StellarService?) {
        BinderSender.stellarService = stellarService
        
        val ai = PackageManagerApis.getApplicationInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0)
        if (ai != null) {
            initialManagerUid = ai.uid
            LOGGER.i("初始管理器 UID: $initialManagerUid")
        }

        try {
            ActivityManagerApis.registerProcessObserver(ProcessObserver())
        } catch (tr: Throwable) {
            LOGGER.e(tr, "注册进程观察者失败")
        }

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
                LOGGER.e(tr, "注册 UID 观察者失败")
            }
        }
    }

    private class ProcessObserver : ProcessObserverAdapter() {
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

        override fun onProcessDied(pid: Int, uid: Int) {
            LOGGER.d("onProcessDied: pid=%d, uid=%d", pid, uid)

            synchronized(PID_LIST) {
                val index = PID_LIST.indexOf(pid)
                if (index != -1) {
                    PID_LIST.removeAt(index)
                }
            }
        }

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
            private val PID_LIST: MutableList<Int?> = ArrayList<Int?>()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private class UidObserver : UidObserverAdapter() {
        @Throws(RemoteException::class)
        override fun onUidActive(uid: Int) {
            LOGGER.i("onUidActive: uid=%d", uid)

            uidStarts(uid)
        }

        @Throws(RemoteException::class)
        override fun onUidCachedChanged(uid: Int, cached: Boolean) {
            LOGGER.i("onUidCachedChanged: uid=%d, cached=%s", uid, cached.toString())

            if (!cached) {
                uidStarts(uid)
            }
        }

        @Throws(RemoteException::class)
        override fun onUidIdle(uid: Int, disabled: Boolean) {
            LOGGER.i("onUidIdle: uid=%d, disabled=%s", uid, disabled.toString())

            uidStarts(uid)
        }

        @Throws(RemoteException::class)
        override fun onUidGone(uid: Int, disabled: Boolean) {
            LOGGER.i("onUidGone: uid=%d, disabled=%s - 应用已卸载或进程已终止", uid, disabled.toString())

            uidGone(uid)
            
            if (stellarService?.permissionEnforcer?.isManager(
                roro.stellar.server.communication.CallerContext(uid, 0)
            ) == true) {
                LOGGER.w("检测到管理器应用 UID gone，检查应用是否被卸载...")
                mainHandler.postDelayed({
                    val ai = PackageManagerApis.getApplicationInfoNoThrow(ServerConstants.MANAGER_APPLICATION_ID, 0, 0)
                    if (ai == null) {
                        LOGGER.w("管理器应用已被卸载，正在退出...")
                        exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)
                    } else {
                        LOGGER.i("管理器应用仍然存在，可能只是进程终止")
                    }
                }, 2000)
            }
        }

        @Throws(RemoteException::class)
        fun uidStarts(uid: Int) {
            synchronized(UID_LIST) {
                if (UID_LIST.contains(uid)) {
                    LOGGER.v("UID %d 已经启动", uid)
                    return
                }
                UID_LIST.add(uid)
                LOGGER.v("UID %d 启动", uid)
            }
            
            if (initialManagerUid != -1) {
                val ai = PackageManagerApis.getApplicationInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0)
                if (ai != null && ai.uid != initialManagerUid) {
                    LOGGER.w("检测到管理器 UID 变化: $initialManagerUid -> ${ai.uid}，正在退出...")
                    exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)
                }
            }

            sendBinder(uid, -1)
        }

        fun uidGone(uid: Int) {
            synchronized(UID_LIST) {
                val index = UID_LIST.indexOf(uid)
                if (index != -1) {
                    UID_LIST.removeAt(index)
                    LOGGER.v("UID %d 已死亡", uid)
                }
            }
        }

        companion object {
            private val UID_LIST: MutableList<Int?> = ArrayList<Int?>()
        }
    }
}