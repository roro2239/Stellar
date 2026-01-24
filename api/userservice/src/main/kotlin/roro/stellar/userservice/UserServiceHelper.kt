package roro.stellar.userservice

import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException

object UserServiceHelper {

    private const val TRANSACTION_DESTROY = 0x00FFFFF1
    private const val TRANSACTION_IS_ALIVE = 0x00FFFFF2
    private const val TRANSACTION_GET_UID = 0x00FFFFF3
    private const val TRANSACTION_GET_PID = 0x00FFFFF4

    @JvmStatic
    @Throws(RemoteException::class)
    fun destroy(binder: IBinder) {
        transactVoid(binder, TRANSACTION_DESTROY)
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun isAlive(binder: IBinder): Boolean {
        return transactInt(binder, TRANSACTION_IS_ALIVE) != 0
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun getUid(binder: IBinder): Int {
        return transactInt(binder, TRANSACTION_GET_UID)
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun getPid(binder: IBinder): Int {
        return transactInt(binder, TRANSACTION_GET_PID)
    }

    private fun transactVoid(binder: IBinder, code: Int) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            binder.transact(code, data, reply, 0)
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun transactInt(binder: IBinder, code: Int): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            binder.transact(code, data, reply, 0)
            reply.readException()
            return reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
