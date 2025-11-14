package roro.stellar.manager.authorization

import android.content.pm.PackageInfo
import android.os.Parcel
import rikka.parcelablelist.ParcelableListSlice
import roro.stellar.Stellar
import roro.stellar.server.ServerConstants

/**
 * 授权管理器
 * Authorization Manager
 * 
 * 功能说明 Features：
 * - 管理应用的Stellar API权限 - Manages Stellar API permissions for apps
 * - 获取请求权限的应用列表 - Gets list of apps requesting permissions
 * - 授予或撤销应用权限 - Grants or revokes app permissions
 * - 检查应用权限状态 - Checks app permission status
 * 
 * 权限模型 Permission Model：
 * - FLAG_ALLOWED: 已授权
 * - FLAG_DENIED: 已拒绝
 * - MASK_PERMISSION: 权限掩码
 */
object AuthorizationManager {

    /** 权限标志：已授权 Permission flag: allowed */
    private const val FLAG_ALLOWED = 1 shl 1
    
    /** 权限标志：已拒绝 Permission flag: denied */
    private const val FLAG_DENIED = 1 shl 2
    
    /** 权限掩码 Permission mask */
    private const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED

    /**
     * 从服务端获取应用列表
     * Get application list from server
     * 
     * @param userId 用户ID（-1表示所有用户）
     * @return 应用信息列表
     */
    private fun getApplications(userId: Int): List<PackageInfo> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("com.stellar.server.IStellarService")
            data.writeInt(userId)
            try {
                Stellar.binder!!.transact(ServerConstants.BINDER_TRANSACTION_getApplications, data, reply, 0)
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
            reply.readException()
            @Suppress("UNCHECKED_CAST")
            (ParcelableListSlice.CREATOR.createFromParcel(reply) as ParcelableListSlice<PackageInfo>).list!!
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 获取所有请求权限的应用包
     * Get all packages requesting permissions
     * 
     * @return 应用包信息列表
     */
    fun getPackages(): List<PackageInfo> {
        val packages: MutableList<PackageInfo> = ArrayList()
        packages.addAll(getApplications(-1))
        return packages
    }

    /**
     * 检查应用是否已授权
     * Check if app is granted
     * 
     * @param packageName 包名
     * @param uid 应用UID
     * @return true表示已授权
     */
    fun granted(packageName: String, uid: Int): Boolean {
        return (Stellar.getFlagsForUid(uid, MASK_PERMISSION) and FLAG_ALLOWED) == FLAG_ALLOWED
    }

    /**
     * 授权应用
     * Grant permission to app
     * 
     * @param packageName 包名
     * @param uid 应用UID
     */
    fun grant(packageName: String, uid: Int) {
        Stellar.updateFlagsForUid(uid, MASK_PERMISSION, FLAG_ALLOWED)
    }

    /**
     * 撤销应用授权
     * Revoke app permission
     * 
     * @param packageName 包名
     * @param uid 应用UID
     */
    fun revoke(packageName: String, uid: Int) {
        Stellar.updateFlagsForUid(uid, MASK_PERMISSION, 0)
    }
}

