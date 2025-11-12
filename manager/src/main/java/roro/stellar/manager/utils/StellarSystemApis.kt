package roro.stellar.manager.utils

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

/**
 * Stellar系统API封装
 * Stellar System APIs Wrapper
 * 
 * 功能说明 Features：
 * - 封装Android隐藏的系统API - Wraps Android hidden system APIs
 * - 通过Stellar服务访问系统功能 - Accesses system features via Stellar service
 * - 提供用户管理、包管理、权限管理接口 - Provides user, package, permission management interfaces
 * - 自动处理跨进程调用 - Auto handles cross-process calls
 * 
 * 主要功能 Main Features：
 * - 用户信息获取 - User information retrieval
 * - 应用包信息查询 - App package info query
 * - 权限状态管理 - Permission status management
 * 
 * 注意事项 Notes：
 * - 需要Stellar服务运行
 * - 使用StellarBinderWrapper包装系统服务Binder
 */
object StellarSystemApis {

    init {
        // 设置Binder监听器，自动包装为StellarBinderWrapper
        SystemServiceBinder.setOnGetBinderListener {
            return@setOnGetBinderListener StellarBinderWrapper(it)
        }
    }

    /** 用户列表缓存 User list cache */
    private val users = arrayListOf<UserInfoCompat>()

    /**
     * 获取用户列表（内部实现）
     * Get user list (internal implementation)
     * 
     * @return 用户信息列表
     */
    private fun getUsers(): List<UserInfoCompat> {
        return if (!Stellar.pingBinder()) {
            // Stellar服务未运行，返回当前用户
            arrayListOf(UserInfoCompat(UserHandleCompat.myUserId(), "Owner"))
        } else try {
            // 通过UserManagerApis获取所有用户
            val list = UserManagerApis.getUsers(true, true, true)
            val users: MutableList<UserInfoCompat> = ArrayList<UserInfoCompat>()
            for (ui in list) {
                users.add(UserInfoCompat(ui.id, ui.name))
            }
            return users
        } catch (tr: Throwable) {
            // 失败时返回当前用户
            arrayListOf(UserInfoCompat(UserHandleCompat.myUserId(), "Owner"))
        }
    }

    /**
     * 获取用户列表
     * Get user list
     * 
     * @param useCache 是否使用缓存
     * @return 用户信息列表
     */
    fun getUsers(useCache: Boolean = true): List<UserInfoCompat> {
        synchronized(users) {
            if (!useCache || users.isEmpty()) {
                users.clear()
                users.addAll(getUsers())
            }
            return users
        }
    }

    /**
     * 获取指定用户信息
     * Get user info by ID
     * 
     * @param userId 用户ID
     * @return 用户信息，不存在时返回默认值
     */
    fun getUserInfo(userId: Int): UserInfoCompat {
        return getUsers(useCache = true).firstOrNull { it.id == userId } ?: UserInfoCompat(
            UserHandleCompat.myUserId(),
            "Unknown"
        )
    }

    fun getInstalledPackages(flags: Long, userId: Int): List<PackageInfo> {
        return if (!Stellar.pingBinder()) {
            ArrayList()
        } else try {
            val listSlice: ParceledListSlice<PackageInfo>? =
                PackageManagerApis.getInstalledPackages(
                    flags,
                    userId
                )
            return if (listSlice != null) {
                listSlice.list
            } else ArrayList()
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        }
    }

    fun checkPermission(permName: String, pkgName: String, userId: Int): Int {
        return if (!Stellar.pingBinder()) {
            PackageManager.PERMISSION_DENIED
        } else try {
            PermissionManagerApis.checkPermission(permName, pkgName, userId)
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        }
    }

    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        if (!Stellar.pingBinder()) {
            return
        }
        try {
            PermissionManagerApis.grantRuntimePermission(packageName, permissionName, userId)
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        }
    }

    fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        if (!Stellar.pingBinder()) {
            return
        }
        try {
            PermissionManagerApis.revokeRuntimePermission(packageName, permissionName, userId)
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        }
    }
}

