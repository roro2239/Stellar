package roro.stellar.server.monitor

import android.content.pm.ApplicationInfo
import android.os.FileObserver
import roro.stellar.server.ServerConstants
import roro.stellar.server.util.Logger
import java.io.File
import kotlin.system.exitProcess

object PackageMonitor {
    private val LOGGER = Logger("PackageMonitor")

    @Suppress("DEPRECATION")
    fun registerPackageRemovedReceiver(ai: ApplicationInfo) {
        val externalDataDir = File("/storage/emulated/0/Android/data/${ServerConstants.MANAGER_APPLICATION_ID}")

        if (externalDataDir.exists()) {
            val dirObserver = object : FileObserver(
                externalDataDir.absolutePath,
                ALL_EVENTS
            ) {
                override fun onEvent(event: Int, path: String?) {
                    LOGGER.w("外部存储目录事件: event=${eventToString(event)}, path=$path, exists=${externalDataDir.exists()}")

                    if (event and DELETE_SELF != 0 || event and MOVED_FROM != 0) {
                        if (!externalDataDir.exists()) {
                            LOGGER.w("管理器应用外部存储目录已被删除，正在退出...")
                            exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)
                        }
                    }
                }
            }
            dirObserver.startWatching()
        } else {
            LOGGER.w("外部存储目录不存在，将在下次启动时创建")
        }
    }

    private fun eventToString(event: Int): String {
        val events = mutableListOf<String>()
        if (event and FileObserver.ACCESS != 0) events.add("ACCESS")
        if (event and FileObserver.MODIFY != 0) events.add("MODIFY")
        if (event and FileObserver.ATTRIB != 0) events.add("ATTRIB")
        if (event and FileObserver.CLOSE_WRITE != 0) events.add("CLOSE_WRITE")
        if (event and FileObserver.CLOSE_NOWRITE != 0) events.add("CLOSE_NOWRITE")
        if (event and FileObserver.OPEN != 0) events.add("OPEN")
        if (event and FileObserver.MOVED_FROM != 0) events.add("MOVED_FROM")
        if (event and FileObserver.MOVED_TO != 0) events.add("MOVED_TO")
        if (event and FileObserver.CREATE != 0) events.add("CREATE")
        if (event and FileObserver.DELETE != 0) events.add("DELETE")
        if (event and FileObserver.DELETE_SELF != 0) events.add("DELETE_SELF")
        if (event and FileObserver.MOVE_SELF != 0) events.add("MOVE_SELF")
        return if (events.isEmpty()) "UNKNOWN($event)" else events.joinToString("|")
    }
}
