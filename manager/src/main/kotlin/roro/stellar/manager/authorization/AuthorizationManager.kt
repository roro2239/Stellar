package roro.stellar.manager.authorization

import android.content.pm.PackageInfo
import android.os.Parcel
import rikka.parcelablelist.ParcelableListSlice
import roro.stellar.Stellar
import roro.stellar.server.ServerConstants

object AuthorizationManager {

    const val FLAG_ASK: Int = 0
    const val FLAG_GRANTED: Int = 1
    const val FLAG_DENIED: Int = 2

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
}

