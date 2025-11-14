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

/**
 * Stellar远程进程包装类
 * Stellar Remote Process Wrapper
 *
 *
 * 功能说明 Features：
 *
 *  * 在Stellar服务端执行进程 - Execute process on Stellar service side
 *  * 提供标准进程输入输出流访问 - Provides standard process I/O stream access
 *  * 支持进程生命周期管理 - Supports process lifecycle management
 *  * 自动管理Binder引用 - Automatically manages Binder references
 *
 *
 *
 * 使用示例 Usage Example：
 * <pre>
 * StellarRemoteProcess process = Stellar.newProcess(
 * new String[]{"sh", "-c", "ls -la"}, null, null
 * );
 *
 * // 读取输出
 * InputStream in = process.getInputStream();
 * // 写入输入
 * OutputStream out = process.getOutputStream();
 *
 * // 等待完成
 * int exitCode = process.waitFor();
</pre> *
 *
 *
 * 注意事项 Notes：
 *
 *  * 从版本11开始，进程在调用者死亡时自动终止 - From version 11, process terminates when caller dies
 *  * 需要在不同线程读写流以避免死锁 - Read/write streams in different threads to avoid deadlock
 *  * Binder引用会被自动持有直到进程结束 - Binder reference is held until process ends
 *
 */
class StellarRemoteProcess : Process, Parcelable {
    /** 远程进程接口 Remote process interface  */
    private var remote: IRemoteProcess?

    /** 输出流（写入进程） Output stream (write to process)  */
    private var os: OutputStream? = null

    /** 输入流（从进程读取） Input stream (read from process)  */
    private var `is`: InputStream? = null

    /**
     * 构造远程进程包装器
     *
     * @param remote 远程进程接口
     */
    internal constructor(remote: IRemoteProcess?) {
        this.remote = remote
        try {
            // 监听进程死亡事件
            this.remote!!.asBinder().linkToDeath({
                this.remote = null
                Log.v(TAG, "remote process is dead")

                // 从缓存中移除
                CACHE.remove(this@StellarRemoteProcess)
            }, 0)
        } catch (e: RemoteException) {
            Log.e(TAG, "linkToDeath", e)
        }

        // 必须持有Binder对象的引用，防止被GC
        CACHE.add(this)
    }

    /**
     * 获取进程的标准输出流（用于写入数据到进程）
     * Get process standard output stream (for writing data to process)
     *
     * @return 输出流 Output stream
     */
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

    /**
     * 获取进程的标准输入流（用于从进程读取数据）
     * Get process standard input stream (for reading data from process)
     *
     * @return 输入流 Input stream
     */
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

    /**
     * 获取进程的错误输出流
     * Get process error stream
     *
     * @return 错误输出流 Error stream
     */
    override fun getErrorStream(): InputStream {
        try {
            return ParcelFileDescriptor.AutoCloseInputStream(remote!!.getErrorStream())
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    /**
     * 等待进程结束
     * Wait for process to finish
     *
     * @return 进程退出码 Process exit code
     * @throws InterruptedException 如果等待被中断
     */
    @Throws(InterruptedException::class)
    override fun waitFor(): Int {
        try {
            return remote!!.waitFor()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    /**
     * 获取进程退出值（仅当进程已结束）
     * Get process exit value (only when process has finished)
     *
     * @return 退出码 Exit code
     * @throws IllegalThreadStateException 如果进程尚未结束
     */
    override fun exitValue(): Int {
        try {
            return remote!!.exitValue()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    /**
     * 销毁进程
     * Destroy process
     */
    override fun destroy() {
        try {
            remote!!.destroy()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    /**
     * 检查进程是否存活
     * Check if process is alive
     *
     * @return true表示进程仍在运行 true if process is still running
     */
    fun alive(): Boolean {
        try {
            return remote!!.alive()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    /**
     * 等待进程结束（带超时）
     * Wait for process to finish with timeout
     *
     * @param timeout 超时时长 Timeout duration
     * @param unit 时间单位 Time unit
     * @return true表示进程在超时前结束 true if process finished before timeout
     * @throws InterruptedException 如果等待被中断
     */
    @Throws(InterruptedException::class)
    fun waitForTimeout(timeout: Long, unit: TimeUnit): Boolean {
        try {
            return remote!!.waitForTimeout(timeout, unit.toString())
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    /**
     * 获取远程进程的Binder对象
     * Get remote process Binder object
     *
     * @return Binder对象
     */
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
        /** 进程缓存，用于持有Binder引用 Process cache for holding Binder references  */
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