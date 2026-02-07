package roro.stellar.server.shizuku

import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import moe.shizuku.server.IRemoteProcess
import roro.stellar.server.util.Logger
import roro.stellar.server.util.ParcelFileDescriptorUtil
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Shizuku 兼容的远程进程持有器
 * 实现 moe.shizuku.server.IRemoteProcess 接口
 */
class ShizukuRemoteProcessHolder(
    private val process: Process,
    token: IBinder?
) : IRemoteProcess.Stub() {

    private var inputPfd: ParcelFileDescriptor? = null
    private var outputPfd: ParcelFileDescriptor? = null

    init {
        if (token != null) {
            try {
                val deathRecipient = DeathRecipient {
                    try {
                        if (alive()) {
                            destroy()
                            LOGGER.i("进程所有者已死亡，销毁进程")
                        }
                    } catch (e: Throwable) {
                        LOGGER.w(e, "销毁进程失败")
                    }
                }
                token.linkToDeath(deathRecipient, 0)
            } catch (e: Throwable) {
                LOGGER.w(e, "linkToDeath 失败")
            }
        }
    }

    override fun getOutputStream(): ParcelFileDescriptor? {
        if (outputPfd == null) {
            try {
                outputPfd = ParcelFileDescriptorUtil.pipeTo(process.outputStream)
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }
        return outputPfd
    }

    override fun getInputStream(): ParcelFileDescriptor? {
        if (inputPfd == null) {
            try {
                inputPfd = ParcelFileDescriptorUtil.pipeFrom(process.inputStream)
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }
        return inputPfd
    }

    override fun getErrorStream(): ParcelFileDescriptor? {
        try {
            return ParcelFileDescriptorUtil.pipeFrom(process.errorStream)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
    }

    override fun waitFor(): Int {
        try {
            return process.waitFor()
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        }
    }

    override fun exitValue(): Int {
        return process.exitValue()
    }

    override fun destroy() {
        process.destroy()
    }

    @Throws(RemoteException::class)
    override fun alive(): Boolean {
        return try {
            exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
    }

    @Throws(RemoteException::class)
    override fun waitForTimeout(timeout: Long, unitName: String?): Boolean {
        val unit = TimeUnit.valueOf(unitName!!)
        val startTime = System.nanoTime()
        var rem = unit.toNanos(timeout)

        do {
            try {
                exitValue()
                return true
            } catch (ex: IllegalThreadStateException) {
                if (rem > 0) {
                    try {
                        Thread.sleep(min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100))
                    } catch (e: InterruptedException) {
                        throw IllegalStateException()
                    }
                }
            }
            rem = unit.toNanos(timeout) - (System.nanoTime() - startTime)
        } while (rem > 0)
        return false
    }

    companion object {
        private val LOGGER = Logger("ShizukuRemoteProcess")
    }
}
