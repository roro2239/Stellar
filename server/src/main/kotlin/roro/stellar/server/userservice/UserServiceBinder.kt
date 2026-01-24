package roro.stellar.server.userservice

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log

class UserServiceBinder(
    private val userBinder: IBinder
) : Binder() {

    companion object {
        private const val TAG = "UserServiceBinder"
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            UserServiceConstants.TRANSACTION_DESTROY -> {
                handleDestroy(reply)
                true
            }
            UserServiceConstants.TRANSACTION_IS_ALIVE -> {
                handleIsAlive(reply)
                true
            }
            UserServiceConstants.TRANSACTION_GET_UID -> {
                handleGetUid(reply)
                true
            }
            UserServiceConstants.TRANSACTION_GET_PID -> {
                handleGetPid(reply)
                true
            }
            else -> {
                data.setDataPosition(0)
                userBinder.transact(code, data, reply, flags)
            }
        }
    }

    private fun handleDestroy(reply: Parcel?) {
        reply?.writeNoException()
        Thread {
            Thread.sleep(100)
            System.exit(0)
        }.start()
    }

    private fun handleIsAlive(reply: Parcel?): Boolean {
        reply?.writeNoException()
        reply?.writeInt(1)
        return true
    }

    private fun handleGetUid(reply: Parcel?) {
        reply?.writeNoException()
        reply?.writeInt(android.os.Process.myUid())
    }

    private fun handleGetPid(reply: Parcel?) {
        reply?.writeNoException()
        reply?.writeInt(android.os.Process.myPid())
    }

    override fun pingBinder(): Boolean {
        return userBinder.pingBinder()
    }

    override fun isBinderAlive(): Boolean {
        return userBinder.isBinderAlive
    }
}
