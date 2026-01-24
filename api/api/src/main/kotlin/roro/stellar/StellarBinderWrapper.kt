package roro.stellar

import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import java.io.FileDescriptor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method


class StellarBinderWrapper(private val original: IBinder) : IBinder {

    @Throws(RemoteException::class)
    override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {

        val newData = Parcel.obtain()
        try {
            newData.writeInterfaceToken(StellarApiConstants.BINDER_DESCRIPTOR)
            newData.writeStrongBinder(original)
            newData.writeInt(code)
            newData.writeInt(flags)

            newData.appendFrom(data, 0, data.dataSize())

            Stellar.transactRemote(newData, reply, 0)
        } finally {
            newData.recycle()
        }
        return true
    }

    @Throws(RemoteException::class)
    override fun getInterfaceDescriptor(): String? {
        return original.interfaceDescriptor
    }

    override fun pingBinder(): Boolean {
        return original.pingBinder()
    }

    override fun isBinderAlive(): Boolean {
        return original.isBinderAlive
    }

    override fun queryLocalInterface(descriptor: String): IInterface? {
        return null
    }

    @Throws(RemoteException::class)
    override fun dump(fd: FileDescriptor, args: Array<String?>?) {
        original.dump(fd, args)
    }

    @Throws(RemoteException::class)
    override fun dumpAsync(fd: FileDescriptor, args: Array<String?>?) {
        original.dumpAsync(fd, args)
    }

    @Throws(RemoteException::class)
    override fun linkToDeath(recipient: DeathRecipient, flags: Int) {
        original.linkToDeath(recipient, flags)
    }

    override fun unlinkToDeath(recipient: DeathRecipient, flags: Int): Boolean {
        return original.unlinkToDeath(recipient, flags)
    }

    companion object {

        private val SYSTEM_SERVICE_CACHE = HashMap<String?, IBinder?>()

        private val TRANSACT_CODE_CACHE = HashMap<String?, Int?>()

        private val getService: Method? = try {
            val sm = Class.forName("android.os.ServiceManager")
            sm.getMethod("getService", java.lang.String::class.java)
        } catch (e: ClassNotFoundException) {
            Log.w("StellarBinderWrapper", ": " + Log.getStackTraceString(e))
            null
        } catch (e: NoSuchMethodException) {
            Log.w("StellarBinderWrapper", ": " + Log.getStackTraceString(e))
            null
        }

        fun getSystemService(name: String): IBinder? {
            var binder = SYSTEM_SERVICE_CACHE[name]
            if (binder == null) {
                try {
                    binder = getService?.invoke(null, name) as IBinder?
                } catch (e: IllegalAccessException) {
                    Log.w("StellarBinderWrapper", ": " + Log.getStackTraceString(e))
                } catch (e: InvocationTargetException) {
                    Log.w("StellarBinderWrapper", ": " + Log.getStackTraceString(e))
                }
                SYSTEM_SERVICE_CACHE[name] = binder
            }
            return binder
        }
    }
}
