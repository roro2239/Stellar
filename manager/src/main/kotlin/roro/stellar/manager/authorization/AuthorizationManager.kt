package roro.stellar.manager.authorization

import android.content.pm.PackageInfo
import android.os.Parcel
import rikka.parcelablelist.ParcelableListSlice
import roro.stellar.Stellar
import roro.stellar.manager.domain.apps.AppType
import roro.stellar.server.ServerConstants

object AuthorizationManager {

    const val FLAG_ASK: Int = 0
    const val FLAG_GRANTED: Int = 1
    const val FLAG_DENIED: Int = 2

    private const val SHIZUKU_META_DATA_KEY = "moe.shizuku.client.V3_SUPPORT"
    private const val STELLAR_PERMISSION_KEY = "roro.stellar.permissions"

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

    fun getPackages(): List<PackageInfo> {
        val packages: MutableList<PackageInfo> = ArrayList()
        packages.addAll(getApplications(-1))
        return packages
    }

    /**
     * 判断应用类型
     * 优先检查是否为 Stellar 原生应用，否则为 Shizuku 兼容应用
     */
    fun getAppType(packageInfo: PackageInfo): AppType {
        val metaData = packageInfo.applicationInfo?.metaData ?: return AppType.SHIZUKU

        // 检查是否有 Stellar 权限声明
        val stellarPermission = metaData.getString(STELLAR_PERMISSION_KEY, "")
        if (stellarPermission.split(",").contains("stellar")) {
            return AppType.STELLAR
        }

        // 检查是否有 Shizuku 支持标记
        val shizukuSupport = metaData.get(SHIZUKU_META_DATA_KEY)
        if (shizukuSupport == true || shizukuSupport == "true") {
            return AppType.SHIZUKU
        }

        // 默认为 Stellar 应用（已有权限记录的应用）
        return AppType.STELLAR
    }
}

