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

    const val FLAG_ASK: Int = 0
    const val FLAG_GRANTED: Int = 1
    const val FLAG_DENIED: Int = 2

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
}

