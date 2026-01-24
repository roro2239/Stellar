package roro.stellar

import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.os.RemoteException
import android.util.ArraySet
import android.util.Log
import com.stellar.server.IRemoteProcess
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.TimeUnit

class StellarRemoteProcess : Process, Parcelable {
    private var remote: IRemoteProcess?

    private var os: OutputStream? = null

    private var `is`: InputStream? = null

    internal constructor(remote: IRemoteProcess?) {
        this.remote = remote
        try {
            this.remote!!.asBinder().linkToDeath({
                this.remote = null
                Log.v(TAG, "")

                CACHE.remove(this@StellarRemoteProcess)
            }, 0)
        } catch (e: RemoteException) {
            Log.e(TAG, "", e)
        }

        CACHE.add(this)
    }

    override fun getOutputStream(): OutputStream {
        if (os == null) {
            try {
                os = ParcelFileDescriptor.AutoCloseOutputStream(remote!!.getOutputStream())
            } catch (e: RemoteException) {
                throw RuntimeException(e)
            }
        }
        return os!!
    }

    override fun getInputStream(): InputStream {
        if (`is` == null) {
            try {
                `is` = ParcelFileDescriptor.AutoCloseInputStream(remote!!.getInputStream())
            } catch (e: RemoteException) {
                throw RuntimeException(e)
            }
        }
        return `is`!!
    }

    override fun getErrorStream(): InputStream {
        try {
            return ParcelFileDescriptor.AutoCloseInputStream(remote!!.getErrorStream())
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    @Throws(InterruptedException::class)
    override fun waitFor(): Int {
        try {
            return remote!!.waitFor()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    override fun exitValue(): Int {
        try {
            return remote!!.exitValue()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    override fun destroy() {
        try {
            remote!!.destroy()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    fun alive(): Boolean {
        try {
            return remote!!.alive()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    @Throws(InterruptedException::class)
    fun waitForTimeout(timeout: Long, unit: TimeUnit): Boolean {
        try {
            return remote!!.waitForTimeout(timeout, unit.toString())
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    fun asBinder(): IBinder? {
        return remote!!.asBinder()
    }

    private constructor(`in`: Parcel) {
        remote = IRemoteProcess.Stub.asInterface(`in`.readStrongBinder())
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStrongBinder(remote!!.asBinder())
    }

    companion object {
        private val CACHE: MutableSet<StellarRemoteProcess?> =
            Collections.synchronizedSet<StellarRemoteProcess?>(
                ArraySet<StellarRemoteProcess?>()
            )

        private const val TAG = "StellarRemoteProcess"

        @JvmField
        val CREATOR: Creator<StellarRemoteProcess?> = object : Creator<StellarRemoteProcess?> {
            override fun createFromParcel(`in`: Parcel): StellarRemoteProcess {
                return StellarRemoteProcess(`in`)
            }

            override fun newArray(size: Int): Array<StellarRemoteProcess?> {
                return arrayOfNulls(size)
            }
        }
    }
}
