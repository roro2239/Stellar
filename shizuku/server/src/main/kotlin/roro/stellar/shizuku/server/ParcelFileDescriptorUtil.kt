package roro.stellar.shizuku.server

import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.io.OutputStream

/**
 * ParcelFileDescriptor 工具接口
 * 用于解耦 shizuku-server 模块与具体实现
 */
interface ParcelFileDescriptorUtil {
    fun pipeTo(outputStream: OutputStream): ParcelFileDescriptor
    fun pipeFrom(inputStream: InputStream): ParcelFileDescriptor
}
