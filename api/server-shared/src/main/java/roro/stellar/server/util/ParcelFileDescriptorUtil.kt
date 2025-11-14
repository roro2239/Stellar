package roro.stellar.server.util

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


/**
 * ParcelFileDescriptor工具类
 * ParcelFileDescriptor Utility Class
 *
 *
 * 功能说明 Features：
 *
 *  * 创建跨进程的文件描述符管道 - Creates cross-process file descriptor pipes
 *  * 支持从InputStream创建可读的ParcelFileDescriptor - Supports creating readable ParcelFileDescriptor from InputStream
 *  * 支持向OutputStream创建可写的ParcelFileDescriptor - Supports creating writable ParcelFileDescriptor to OutputStream
 *  * 自动处理数据传输 - Automatically handles data transfer
 *
 *
 *
 * 使用场景 Use Cases：
 *
 *  * 跨进程传输进程输入输出流 - Transfer process I/O streams across processes
 *  * 远程进程通信 - Remote process communication
 *
 */
object ParcelFileDescriptorUtil {
    /**
     * 从InputStream创建ParcelFileDescriptor（用于读取）
     * Create ParcelFileDescriptor from InputStream (for reading)
     *
     * @param inputStream 输入流
     * @return 可读的ParcelFileDescriptor
     * @throws IOException 如果创建管道失败
     */
    @Throws(IOException::class)
    fun pipeFrom(inputStream: InputStream): ParcelFileDescriptor? {
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        // 启动后台线程将inputStream的数据写入管道
        TransferThread(inputStream, ParcelFileDescriptor.AutoCloseOutputStream(writeSide))
            .start()

        return readSide
    }

    /**
     * 创建ParcelFileDescriptor写入OutputStream
     * Create ParcelFileDescriptor to write to OutputStream
     *
     * @param outputStream 输出流
     * @return 可写的ParcelFileDescriptor
     * @throws IOException 如果创建管道失败
     */
    @Throws(IOException::class)
    fun pipeTo(outputStream: OutputStream): ParcelFileDescriptor? {
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        // 启动后台线程将管道数据写入outputStream
        TransferThread(ParcelFileDescriptor.AutoCloseInputStream(readSide), outputStream)
            .start()

        return writeSide
    }

    /**
     * 数据传输线程
     * Data Transfer Thread
     *
     *
     * 负责在后台线程中持续传输数据，直到输入流结束
     */
    class TransferThread(val mIn: InputStream, val mOut: OutputStream) :
        Thread("ParcelFileDescriptor Transfer Thread") {
        /**
         * 构造传输线程
         * Construct transfer thread
         *
         * @param mIn 输入流
         * @param mOut 输出流
         */
        init {
            setDaemon(true) // 设为守护线程，不阻止JVM退出
        }

        /**
         * 线程执行方法
         * Thread execution method
         *
         *
         * 持续从输入流读取数据并写入输出流，直到输入流结束
         *
         * Continuously reads from input stream and writes to output stream until EOF
         */
        override fun run() {
            val buf = ByteArray(8192)
            var len: Int

            try {
                // 持续读取并写入数据
                while ((mIn.read(buf).also { len = it }) > 0) {
                    mOut.write(buf, 0, len)
                    mOut.flush()
                }
            } catch (e: IOException) {
                Log.e("TransferThread", Log.getStackTraceString(e))
            } finally {
                // 确保流被关闭
                try {
                    mIn.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                try {
                    mOut.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}