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
import roro.stellar.server.util.Logger

object BinderSender {
    private val LOGGER = Logger("BinderSender")
    private var stellarService: StellarService? = null

    @Throws(RemoteException::class)
    private fun sendBinder(uid: Int, pid: Int) {
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
            if (pi == null || pi.applicationInfo == null || pi.applicationInfo!!.metaData == null) continue

            if (pi.packageName == MANAGER_APPLICATION_ID) {
                StellarService.sendBinderToManger(stellarService, userId)
            } else if (pi.applicationInfo!!.metaData.getString(PERMISSION_KEY, "").split(",")
                    .contains("stellar")
            ) {
                StellarService.sendBinderToUserApp(stellarService, packageName, userId)
            }

            return
        }
    }

    fun register(stellarService: StellarService?) {
        BinderSender.stellarService = stellarService

        try {
            ActivityManagerApis.registerProcessObserver(ProcessObserver())
        } catch (tr: Throwable) {
            LOGGER.e(tr, "registerProcessObserver")
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
                LOGGER.e(tr, "registerUidObserver")
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
            LOGGER.d("onUidCachedChanged: uid=%d", uid)

            uidStarts(uid)
        }

        @Throws(RemoteException::class)
        override fun onUidCachedChanged(uid: Int, cached: Boolean) {
            LOGGER.d("onUidCachedChanged: uid=%d, cached=%s", uid, cached.toString())

            if (!cached) {
                uidStarts(uid)
            }
        }

        @Throws(RemoteException::class)
        override fun onUidIdle(uid: Int, disabled: Boolean) {
            LOGGER.d("onUidIdle: uid=%d, disabled=%s", uid, disabled.toString())

            uidStarts(uid)
        }

        @Throws(RemoteException::class)
        override fun onUidGone(uid: Int, disabled: Boolean) {
            LOGGER.d("onUidGone: uid=%d, disabled=%s", uid, disabled.toString())

            uidGone(uid)
        }

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
            private val UID_LIST: MutableList<Int?> = ArrayList<Int?>()
        }
    }
}