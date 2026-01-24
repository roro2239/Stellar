package roro.stellar.server.api

import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.stellar.server.IRemoteProcess
import roro.stellar.server.util.Logger
import roro.stellar.server.util.ParcelFileDescriptorUtil
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

class RemoteProcessHolder(
    private val process: Process, token: IBinder?
) : IRemoteProcess.Stub() {
    private var `in`: ParcelFileDescriptor? = null

    private var out: ParcelFileDescriptor? = null

    init {
        if (token != null) {
            try {
                val deathRecipient = DeathRecipient {
                    try {
                        if (alive()) {
                            destroy()
                            LOGGER.i("因为所有者已死亡，销毁进程")
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
        if (out == null) {
            try {
                out = ParcelFileDescriptorUtil.pipeTo(process.outputStream)
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }
        return out
    }

    override fun getInputStream(): ParcelFileDescriptor? {
        if (`in` == null) {
            try {
                `in` = ParcelFileDescriptorUtil.pipeFrom(process.inputStream)
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }
        return `in`
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
        try {
            this.exitValue()
            return false
        } catch (e: IllegalThreadStateException) {
            return true
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
                        Thread.sleep(
                            min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100)
                        )
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
        private val LOGGER = Logger("RemoteProcessHolder")
    }
}