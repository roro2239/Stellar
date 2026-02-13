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
    private var inputStream: InputStream? = null

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

    private constructor(parcel: Parcel) {
        remote = IRemoteProcess.Stub.asInterface(parcel.readStrongBinder())
    }

    private fun requireRemote(): IRemoteProcess = remote ?: throw IllegalStateException("Process is dead")

    override fun getOutputStream(): OutputStream {
        if (os == null) {
            os = ParcelFileDescriptor.AutoCloseOutputStream(requireRemote().getOutputStream())
        }
        return os!!
    }

    override fun getInputStream(): InputStream {
        if (inputStream == null) {
            inputStream = ParcelFileDescriptor.AutoCloseInputStream(requireRemote().getInputStream())
        }
        return inputStream!!
    }

    override fun getErrorStream(): InputStream =
        ParcelFileDescriptor.AutoCloseInputStream(requireRemote().getErrorStream())

    @Throws(InterruptedException::class)
    override fun waitFor(): Int = requireRemote().waitFor()

    override fun exitValue(): Int = requireRemote().exitValue()

    override fun destroy() = requireRemote().destroy()

    fun alive(): Boolean = requireRemote().alive()

    @Throws(InterruptedException::class)
    fun waitForTimeout(timeout: Long, unit: TimeUnit): Boolean =
        requireRemote().waitForTimeout(timeout, unit.toString())

    fun asBinder(): IBinder? = remote?.asBinder()

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStrongBinder(remote!!.asBinder())
    }

    companion object {
        private const val TAG = "StellarRemoteProcess"

        private val CACHE: MutableSet<StellarRemoteProcess?> =
            Collections.synchronizedSet(ArraySet())

        @JvmField
        val CREATOR: Creator<StellarRemoteProcess?> = object : Creator<StellarRemoteProcess?> {
            override fun createFromParcel(parcel: Parcel): StellarRemoteProcess = StellarRemoteProcess(parcel)
            override fun newArray(size: Int): Array<StellarRemoteProcess?> = arrayOfNulls(size)
        }
    }
}
