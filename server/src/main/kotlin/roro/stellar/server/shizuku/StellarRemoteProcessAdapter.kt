package roro.stellar.server.shizuku

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.stellar.server.IRemoteProcess as IStellarRemoteProcess
import moe.shizuku.server.IRemoteProcess as IShizukuRemoteProcess

class StellarRemoteProcessAdapter(
    private val stellarProcess: IStellarRemoteProcess
) : IShizukuRemoteProcess.Stub() {

    override fun getOutputStream(): ParcelFileDescriptor? = stellarProcess.outputStream

    override fun getInputStream(): ParcelFileDescriptor? = stellarProcess.inputStream

    override fun getErrorStream(): ParcelFileDescriptor? = stellarProcess.errorStream

    override fun waitFor(): Int = stellarProcess.waitFor()

    override fun exitValue(): Int = stellarProcess.exitValue()

    override fun destroy() = stellarProcess.destroy()

    @Throws(RemoteException::class)
    override fun alive(): Boolean = stellarProcess.alive()

    @Throws(RemoteException::class)
    override fun waitForTimeout(timeout: Long, unitName: String?): Boolean =
        stellarProcess.waitForTimeout(timeout, unitName)
}
