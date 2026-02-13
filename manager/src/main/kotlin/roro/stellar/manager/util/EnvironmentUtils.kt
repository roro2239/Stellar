package roro.stellar.manager.util

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemProperties
import java.io.File

object EnvironmentUtils {

    @JvmStatic
    fun isWatch(context: Context): Boolean =
        context.getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_WATCH

    fun isRooted(): Boolean =
        System.getenv("PATH")?.split(File.pathSeparatorChar)?.any { File("$it/su").exists() } == true

    fun getAdbTcpPort(): Int =
        SystemProperties.getInt("service.adb.tcp.port", -1)
            .takeIf { it != -1 }
            ?: SystemProperties.getInt("persist.adb.tcp.port", -1)
}

