package roro.stellar.server.shizuku

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.stellar.server.IRemoteProcess as IStellarRemoteProcess
import moe.shizuku.server.IRemoteProcess as IShizukuRemoteProcess

/**
 * Stellar IRemoteProcess 到 Shizuku IRemoteProcess 的适配器
 */
class StellarRemoteProcessAdapter(
    private val stellarProcess: IStellarRemoteProcess
) : IShizukuRemoteProcess.Stub() {

    override fun getOutputStream(): ParcelFileDescriptor? {
        return stellarProcess.outputStream
    }

    override fun getInputStream(): ParcelFileDescriptor? {
        return stellarProcess.inputStream
    }

    override fun getErrorStream(): ParcelFileDescriptor? {
        return stellarProcess.errorStream
    }

    override fun waitFor(): Int {
        return stellarProcess.waitFor()
    }

    override fun exitValue(): Int {
        return stellarProcess.exitValue()
    }

    override fun destroy() {
        stellarProcess.destroy()
    }

    @Throws(RemoteException::class)
    override fun alive(): Boolean {
        return stellarProcess.alive()
    }

    @Throws(RemoteException::class)
    override fun waitForTimeout(timeout: Long, unitName: String?): Boolean {
        return stellarProcess.waitForTimeout(timeout, unitName)
    }
}
