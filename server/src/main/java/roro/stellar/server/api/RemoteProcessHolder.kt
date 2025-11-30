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

/**
 * 远程进程持有者
 * Remote Process Holder
 *
 *
 * 功能说明 Features：
 *
 *  * 封装Process为AIDL接口 - Wraps Process as AIDL interface
 *  * 提供跨进程的进程控制能力 - Provides cross-process process control
 *  * 管理进程的输入输出流 - Manages process I/O streams
 *  * 监听客户端死亡并自动清理 - Monitors client death and auto cleanup
 *
 *
 *
 * 工作原理 How It Works：
 *
 *  * 将Process的InputStream/OutputStream转换为ParcelFileDescriptor - Converts Process I/O streams to ParcelFileDescriptor
 *  * 通过Binder传递文件描述符到客户端 - Passes file descriptors to client via Binder
 *  * 当客户端死亡时自动销毁进程 - Auto destroys process when client dies
 *
 */
class RemoteProcessHolder(
    /** 本地进程对象 Local process object  */
    private val process: Process, token: IBinder?
) : IRemoteProcess.Stub() {
    /** 进程输入流的文件描述符 Input stream file descriptor  */
    private var `in`: ParcelFileDescriptor? = null

    /** 进程输出流的文件描述符 Output stream file descriptor  */
    private var out: ParcelFileDescriptor? = null

    /**
     * 构造远程进程持有者
     * Construct remote process holder
     *
     * @param process 要包装的进程
     * @param token 客户端Binder令牌（用于监听客户端死亡）
     */
    init {
        // 监听客户端死亡，自动清理进程
        if (token != null) {
            try {
                val deathRecipient = DeathRecipient {
                    try {
                        if (alive()) {
                            destroy()
                            LOGGER.i("destroy process because the owner is dead")
                        }
                    } catch (e: Throwable) {
                        LOGGER.w(e, "failed to destroy process")
                    }
                }
                token.linkToDeath(deathRecipient, 0)
            } catch (e: Throwable) {
                LOGGER.w(e, "linkToDeath")
            }
        }
    }

    /**
     * 获取进程的标准输出流（用于写入数据到进程）
     * Get process standard output stream (for writing to process)
     */
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

    /**
     * 获取进程的标准输入流（用于读取进程输出）
     * Get process standard input stream (for reading process output)
     */
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

    /**
     * 获取进程的错误流
     * Get process error stream
     */
    override fun getErrorStream(): ParcelFileDescriptor? {
        try {
            return ParcelFileDescriptorUtil.pipeFrom(process.errorStream)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
    }

    /**
     * 等待进程结束
     * Wait for process to finish
     *
     * @return 进程退出码
     */
    override fun waitFor(): Int {
        try {
            return process.waitFor()
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        }
    }

    /**
     * 获取进程退出码
     * Get process exit value
     *
     * @return 退出码
     * @throws IllegalThreadStateException 如果进程尚未结束
     */
    override fun exitValue(): Int {
        return process.exitValue()
    }

    /**
     * 销毁进程
     * Destroy process
     */
    override fun destroy() {
        process.destroy()
    }

    /**
     * 检查进程是否存活
     * Check if process is alive
     *
     * @return true表示进程仍在运行
     */
    @Throws(RemoteException::class)
    override fun alive(): Boolean {
        try {
            this.exitValue()
            return false
        } catch (e: IllegalThreadStateException) {
            return true
        }
    }

    /**
     * 等待进程结束（带超时）
     * Wait for process to finish (with timeout)
     *
     * @param timeout 超时时间
     * @param unitName 时间单位名称
     * @return true表示进程在超时前结束
     */
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