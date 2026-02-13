package roro.stellar.manager.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ParceledListSlice
import android.os.RemoteException
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.PermissionManagerApis
import rikka.hidden.compat.UserManagerApis
import rikka.hidden.compat.util.SystemServiceBinder
import roro.stellar.Stellar
import roro.stellar.StellarBinderWrapper

object StellarSystemApis {

    init {
        SystemServiceBinder.setOnGetBinderListener {
            return@setOnGetBinderListener StellarBinderWrapper(it)
        }
    }

    private val users = arrayListOf<UserInfoCompat>()

    private fun getUsers(): List<UserInfoCompat> =
        if (!Stellar.pingBinder()) {
            listOf(UserInfoCompat(UserHandleCompat.myUserId(), "Owner"))
        } else try {
            UserManagerApis.getUsers(true, true, true).map { UserInfoCompat(it.id, it.name) }
        } catch (_: Throwable) {
            listOf(UserInfoCompat(UserHandleCompat.myUserId(), "Owner"))
        }

    fun getUsers(useCache: Boolean = true): List<UserInfoCompat> {
        synchronized(users) {
            if (!useCache || users.isEmpty()) {
                users.clear()
                users.addAll(getUsers())
            }
            return users
        }
    }

    fun getUserInfo(userId: Int): UserInfoCompat =
        getUsers(useCache = true).firstOrNull { it.id == userId }
            ?: UserInfoCompat(UserHandleCompat.myUserId(), "Unknown")

    fun getInstalledPackages(flags: Long, userId: Int): List<PackageInfo> =
        if (!Stellar.pingBinder()) {
            emptyList()
        } else try {
            PackageManagerApis.getInstalledPackages(flags, userId)?.list ?: emptyList()
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        }

    fun checkPermission(permName: String, pkgName: String, userId: Int): Int =
        if (!Stellar.pingBinder()) {
            PackageManager.PERMISSION_DENIED
        } else try {
            PermissionManagerApis.checkPermission(permName, pkgName, userId)
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        }

    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        if (!Stellar.pingBinder()) {
            throw SecurityException("Stellar service is not available")
        }
        try {
            Stellar.grantRuntimePermission(packageName, permissionName, userId)
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        } catch (e: SecurityException) {
            throw SecurityException("Failed to grant permission: ${e.message}", e)
        }
    }

    fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        if (!Stellar.pingBinder()) {
            throw SecurityException("Stellar service is not available")
        }
        try {
            Stellar.revokeRuntimePermission(packageName, permissionName, userId)
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        }
    }
}

