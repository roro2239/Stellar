package roro.stellar.shizuku.server

import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import moe.shizuku.server.IRemoteProcess
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Shizuku 兼容的远程进程持有器
 * 实现 moe.shizuku.server.IRemoteProcess 接口
 */
class ShizukuRemoteProcessHolder(
    private val process: Process,
    token: IBinder?,
    private val pfdUtil: ParcelFileDescriptorUtil
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
                            Log.i(TAG, "进程所有者已死亡，销毁进程")
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "销毁进程失败", e)
                    }
                }
                token.linkToDeath(deathRecipient, 0)
            } catch (e: Throwable) {
                Log.w(TAG, "linkToDeath 失败", e)
            }
        }
    }

    override fun getOutputStream(): ParcelFileDescriptor? {
        if (outputPfd == null) {
            try {
                outputPfd = pfdUtil.pipeTo(process.outputStream)
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }
        return outputPfd
    }

    override fun getInputStream(): ParcelFileDescriptor? {
        if (inputPfd == null) {
            try {
                inputPfd = pfdUtil.pipeFrom(process.inputStream)
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }
        return inputPfd
    }

    override fun getErrorStream(): ParcelFileDescriptor? {
        try {
            return pfdUtil.pipeFrom(process.errorStream)
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
        private const val TAG = "ShizukuRemoteProcess"
    }
}