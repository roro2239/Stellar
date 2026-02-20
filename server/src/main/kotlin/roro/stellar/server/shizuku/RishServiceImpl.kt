package roro.stellar.server.shizuku

import android.os.Binder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.system.Os
import rikka.rish.RishConfig
import rikka.rish.RishConstants
import rikka.rish.RishHost
import java.util.concurrent.ConcurrentHashMap

class RishServiceImpl {

    private val hosts = ConcurrentHashMap<Int, RishHost>()
    private val isRoot = Os.getuid() == 0

    init {
        RishConfig.init(ShizukuApiConstants.BINDER_DESCRIPTOR, 30000)
    }

    fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            RishConfig.getTransactionCode(RishConfig.TRANSACTION_createHost) -> {
                if (reply == null || (flags and android.os.IBinder.FLAG_ONEWAY) != 0) return true
                data.enforceInterface(RishConfig.getInterfaceToken())
                val tty = data.readByte()
                val stdin = data.readFileDescriptor()
                val stdout = data.readFileDescriptor()
                val stderr: ParcelFileDescriptor? = if ((tty.toInt() and RishConstants.ATTY_ERR) == 0) data.readFileDescriptor() else null
                val args = data.createStringArray() ?: emptyArray()
                var env = data.createStringArray()
                val dir = data.readString() ?: ""
                val preserveEnv = env?.contains("RISH_PRESERVE_ENV=1") == true
                val denyEnv = env?.contains("RISH_PRESERVE_ENV=0") == true
                if (!isRoot && !preserveEnv || denyEnv) env = null
                val host = RishHost(args, env, dir, tty, stdin, stdout, stderr)
                host.start()
                hosts[Binder.getCallingPid()] = host
                reply.writeNoException()
                true
            }
            RishConfig.getTransactionCode(RishConfig.TRANSACTION_setWindowSize) -> {
                data.enforceInterface(RishConfig.getInterfaceToken())
                val size = data.readLong()
                hosts[Binder.getCallingPid()]?.setWindowSize(size)
                reply?.writeNoException()
                true
            }
            RishConfig.getTransactionCode(RishConfig.TRANSACTION_getExitCode) -> {
                data.enforceInterface(RishConfig.getInterfaceToken())
                val exitCode = hosts[Binder.getCallingPid()]?.exitCode ?: -1
                reply?.writeNoException()
                reply?.writeInt(exitCode)
                true
            }
            else -> false
        }
    }
}
