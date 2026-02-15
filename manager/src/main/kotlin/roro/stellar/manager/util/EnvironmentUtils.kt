package roro.stellar.manager.util

import android.os.SystemProperties
import java.io.File

object EnvironmentUtils {

    fun isRooted(): Boolean =
        System.getenv("PATH")?.split(File.pathSeparatorChar)?.any { File("$it/su").exists() } == true

    fun getAdbTcpPort(): Int =
        SystemProperties.getInt("service.adb.tcp.port", -1)
            .takeIf { it != -1 }
            ?: SystemProperties.getInt("persist.adb.tcp.port", -1)
}

