package roro.stellar.server.util

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object ParcelFileDescriptorUtil {
    @Throws(IOException::class)
    fun pipeFrom(inputStream: InputStream): ParcelFileDescriptor? {
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        TransferThread(inputStream, ParcelFileDescriptor.AutoCloseOutputStream(writeSide))
            .start()

        return readSide
    }

    @Throws(IOException::class)
    fun pipeTo(outputStream: OutputStream): ParcelFileDescriptor? {
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        TransferThread(ParcelFileDescriptor.AutoCloseInputStream(readSide), outputStream)
            .start()

        return writeSide
    }

    class TransferThread(val mIn: InputStream, val mOut: OutputStream) :
        Thread("ParcelFileDescriptor Transfer Thread") {
        init {
            setDaemon(true)
        }

        override fun run() {
            val buf = ByteArray(8192)
            var len: Int

            try {
                while ((mIn.read(buf).also { len = it }) > 0) {
                    mOut.write(buf, 0, len)
                    mOut.flush()
                }
            } catch (e: IOException) {
                Log.e("TransferThread", "传输线程错误: " + Log.getStackTraceString(e))
            } finally {
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