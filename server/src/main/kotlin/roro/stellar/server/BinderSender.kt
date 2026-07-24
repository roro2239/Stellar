package roro.stellar.server

import android.app.ActivityManagerHidden
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import android.text.TextUtils
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.adapter.ProcessObserverAdapter
import rikka.hidden.compat.adapter.UidObserverAdapter
import roro.stellar.StellarApiConstants.PERMISSION_KEY
import roro.stellar.StellarApiConstants.PERMISSION_STELLAR
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.binder.BinderDistributor
import roro.stellar.server.ktx.mainHandler
import roro.stellar.server.shizuku.ShizukuApiConstants
import roro.stellar.server.util.Logger
import roro.stellar.server.util.ProviderDiscovery
import roro.stellar.server.util.UserHandleCompat.PER_USER_RANGE
import kotlin.system.exitProcess

object BinderSender {
    private val LOGGER = Logger("BinderSender")
    private var stellarService: StellarService? = null
    private var initialManagerUid: Int = -1

    private const val SHIZUKU_MANAGER_PERMISSION = "moe.shizuku.manager.permission.MANAGER"

    @Throws(RemoteException::class)
    private fun sendBinder(uid: Int, pid: Int) {
        val packages = PackageManagerApis.getPackagesForUidNoThrow(uid)
        if (packages.isEmpty()) {
            LOGGER.w("sendBinder：uid=%d 没有关联包名", uid)
            return
        }

        LOGGER.i("sendBinder：准备向 uid=%d, pid=%d 发送 Binder，packages=%s", uid, pid, TextUtils.join(", ", packages))

        val userId = uid / PER_USER_RANGE
        for (packageName in packages) {
            try {
                val pi = PackageManagerApis.getPackageInfoNoThrow(
                    packageName,
                    (PackageManager.GET_META_DATA or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS).toLong(),
                    userId
                )

                if (pi == null) {
                    LOGGER.w("sendBinder：无法获取包信息：%s", packageName)
                    continue
                }

                if (pi.applicationInfo == null) {
                    LOGGER.w("sendBinder：applicationInfo 为 null：%s", packageName)
                    continue
                }

                if (pi.packageName == MANAGER_APPLICATION_ID) {
                    LOGGER.i("sendBinder：向管理器发送 Binder：%s", packageName)
                    BinderDistributor.sendBinderToManager(stellarService, userId)
                    return
                }

                val hasStellarProvider = ProviderDiscovery.hasStellarProvider(pi)
                val hasShizukuProvider = ProviderDiscovery.hasShizukuProvider(pi)
                val metaData = pi.applicationInfo!!.metaData
                if (metaData != null) {
                    val permissions = metaData.getString(PERMISSION_KEY, "")
                    val declaredPermissions = permissions.split(",").map { it.trim() }
                    if (declaredPermissions.contains(PERMISSION_STELLAR) || hasStellarProvider) {
                        LOGGER.i("sendBinder：向用户应用发送 Stellar Binder：%s", packageName)
                        val sent = BinderDistributor.sendBinderToUserApp(stellarService, packageName, userId)
                        if (sent) return
                        LOGGER.w("sendBinder：向 %s 发送 Stellar Binder 失败，继续尝试同 UID 的其他包", packageName)
                        continue
                    }

                    val shizukuSupport =
                        metaData.getBoolean(ShizukuApiConstants.META_DATA_KEY, false) ||
                            metaData.getString(ShizukuApiConstants.META_DATA_KEY)
                                ?.equals("true", ignoreCase = true) == true ||
                            hasShizukuProvider

                    if (shizukuSupport) {
                        if (pi.requestedPermissions?.contains(SHIZUKU_MANAGER_PERMISSION) == true) {
                            LOGGER.i("sendBinder：跳过 Shizuku 管理器：%s", packageName)
                            continue
                        }

                        LOGGER.i("sendBinder：向应用发送 Shizuku Binder：%s", packageName)
                        val sent = BinderDistributor.sendShizukuBinderToUserApp(
                            stellarService?.shizukuServiceIntercept,
                            packageName,
                            userId
                        )
                        if (sent) return
                        LOGGER.w("sendBinder：向 %s 发送 Shizuku Binder 失败，继续尝试同 UID 的其他包", packageName)
                        continue
                    }

                    LOGGER.d("sendBinder：包 %s 的 meta-data 未声明 Stellar/Shizuku 支持", packageName)
                } else {
                    if (hasStellarProvider) {
                        LOGGER.i("sendBinder：通过 Provider 向用户应用发送 Stellar Binder：%s", packageName)
                        val sent = BinderDistributor.sendBinderToUserApp(stellarService, packageName, userId)
                        if (sent) return
                        LOGGER.w("sendBinder：向 %s 发送 Stellar Binder 失败，继续尝试同 UID 的其他包", packageName)
                        continue
                    }

                    if (hasShizukuProvider) {
                        if (pi.requestedPermissions?.contains(SHIZUKU_MANAGER_PERMISSION) == true) {
                            LOGGER.i("sendBinder：跳过 Shizuku 管理器：%s", packageName)
                            continue
                        }

                        LOGGER.i("sendBinder：通过 Provider 向应用发送 Shizuku Binder：%s", packageName)
                        val sent = BinderDistributor.sendShizukuBinderToUserApp(
                            stellarService?.shizukuServiceIntercept,
                            packageName,
                            userId
                        )
                        if (sent) return
                        LOGGER.w("sendBinder：向 %s 发送 Shizuku Binder 失败，继续尝试同 UID 的其他包", packageName)
                        continue
                    }

                    LOGGER.d("sendBinder：包 %s 没有 meta-data，也未发现兼容 Provider，跳过", packageName)
                }
            } catch (t: Throwable) {
                LOGGER.e(t, "sendBinder：处理包 %s 时异常，继续下一个包", packageName)
            }
        }

        LOGGER.w("sendBinder：uid=%d 的所有包都不满足条件，未发送 Binder", uid)
    }

    fun register(stellarService: StellarService?) {
        BinderSender.stellarService = stellarService

        val ai = PackageManagerApis.getApplicationInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0)
        if (ai != null) {
            initialManagerUid = ai.uid
            LOGGER.i("初始管理器 UID：%d", initialManagerUid)
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
                    UidObserver(),
                    flags,
                    ActivityManagerHidden.PROCESS_STATE_UNKNOWN,
                    null
                )
            } catch (tr: Throwable) {
                LOGGER.e(tr, "注册 UID 观察者失败")
            }
        }
    }

    fun sendBinderToAuthorizedRunningClients(configManager: ConfigManager) {
        val runningProcesses = getRunningAppProcesses()
        if (runningProcesses.isEmpty()) {
            LOGGER.w("启动补发 Binder：未获取到正在运行的进程")
            return
        }

        val handledUids = HashSet<Int>()
        for (process in runningProcesses) {
            val uid = process.uid
            if (!handledUids.add(uid)) continue

            val entry = configManager.find(uid) ?: continue
            if (entry.permissions.none { it.value == ConfigManager.FLAG_GRANTED }) {
                LOGGER.v("启动补发 Binder：跳过未授权 uid=%d，process=%s", uid, process.processName)
                continue
            }

            try {
                LOGGER.i(
                    "启动补发 Binder：向已运行且已授权的 uid=%d 发送 Binder，process=%s",
                    uid,
                    process.processName
                )
                sendBinder(uid, process.pid)
            } catch (tr: Throwable) {
                LOGGER.w(tr, "启动补发 Binder：uid=%d 发送失败", uid)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRunningAppProcesses(): List<ActivityManager.RunningAppProcessInfo> {
        return try {
            val service = ActivityManager::class.java
                .getDeclaredMethod("getService")
                .invoke(null)
            val method = service.javaClass.methods.firstOrNull {
                it.name == "getRunningAppProcesses" && it.parameterTypes.isEmpty()
            } ?: return emptyList()
            method.invoke(service) as? List<ActivityManager.RunningAppProcessInfo> ?: emptyList()
        } catch (tr: Throwable) {
            LOGGER.w(tr, "启动补发 Binder：获取正在运行的进程失败")
            emptyList()
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
                "前台 Activity 状态变化：pid=%d，uid=%d，foregroundActivities=%s",
                pid,
                uid,
                foregroundActivities.toString()
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
            LOGGER.d("进程死亡：pid=%d，uid=%d", pid, uid)

            synchronized(PID_LIST) {
                PID_LIST.remove(pid)
            }
        }

        @Throws(RemoteException::class)
        override fun onProcessStateChanged(pid: Int, uid: Int, procState: Int) {
            LOGGER.d("进程状态变化：pid=%d，uid=%d，procState=%d", pid, uid, procState)

            synchronized(PID_LIST) {
                if (PID_LIST.contains(pid)) {
                    return
                }
                PID_LIST.add(pid)
            }

            sendBinder(uid, pid)
        }

        companion object {
            private val PID_LIST: MutableList<Int> = ArrayList()
        }
    }

    private class UidObserver : UidObserverAdapter() {
        @Throws(RemoteException::class)
        override fun onUidActive(uid: Int) {
            LOGGER.i("UID 变为 active：uid=%d", uid)

            if (stellarService?.permissionEnforcer?.isManager(
                    roro.stellar.server.communication.CallerContext(uid, 0)
                ) == true
            ) {
                LOGGER.i("检测到管理器 UID active，延迟 500ms 后发送 Binder")
                mainHandler.postDelayed({
                    uidStarts(uid)
                }, 500)
            } else {
                uidStarts(uid)
            }
        }

        @Throws(RemoteException::class)
        override fun onUidCachedChanged(uid: Int, cached: Boolean) {
            LOGGER.i("UID cached 状态变化：uid=%d，cached=%s", uid, cached.toString())

            if (!cached) {
                uidStarts(uid)
            }
        }

        @Throws(RemoteException::class)
        override fun onUidIdle(uid: Int, disabled: Boolean) {
            LOGGER.i("UID idle：uid=%d，disabled=%s", uid, disabled.toString())

            uidStarts(uid)
        }

        @Throws(RemoteException::class)
        override fun onUidGone(uid: Int, disabled: Boolean) {
            LOGGER.i("UID gone：uid=%d，disabled=%s，应用可能已停止或卸载", uid, disabled.toString())

            uidGone(uid)

            if (stellarService?.permissionEnforcer?.isManager(
                    roro.stellar.server.communication.CallerContext(uid, 0)
                ) == true
            ) {
                LOGGER.w("检测到管理器 UID gone，检查管理器是否仍然安装")
                mainHandler.postDelayed({
                    val ai = PackageManagerApis.getApplicationInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0)
                    if (ai == null) {
                        LOGGER.w("管理器应用已卸载，退出服务")
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
                    LOGGER.w("检测到管理器 UID 变化：%d -> %d，退出服务", initialManagerUid, ai.uid)
                    exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)
                }
            }

            sendBinder(uid, -1)
        }

        fun uidGone(uid: Int) {
            synchronized(UID_LIST) {
                if (UID_LIST.remove(uid)) {
                    LOGGER.v("UID 已移除启动记录：uid=%d", uid)
                }
            }
        }

        companion object {
            private val UID_LIST: MutableList<Int> = ArrayList()
        }
    }
}
