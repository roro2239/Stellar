package roro.stellar.server.api

import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.stellar.server.IRemotePtyProcess
import rikka.rish.RishHost

class RemotePtyProcessHolder(
    private val host: RishHost,
    token: IBinder?
) : IRemotePtyProcess.Stub() {

    private val ptmxFd = ParcelFileDescriptor.fromFd(host.ptmx)

    init {
        token?.linkToDeath({ destroy() }, 0)
    }

    override fun getPtyFd(): ParcelFileDescriptor = ptmxFd

    override fun resize(size: Long) = host.setWindowSize(size)

    override fun waitFor(): Int {
        while (host.exitCode == Int.MAX_VALUE) Thread.sleep(50)
        return host.exitCode
    }

    override fun destroy() = ptmxFd.close()
}
