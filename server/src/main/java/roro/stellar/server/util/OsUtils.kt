package roro.stellar.server.util

import android.os.SELinux
import android.system.Os

object OsUtils {
    val uid: Int = Os.getuid()

    val pid: Int = Os.getpid()

    val sELinuxContext: String?

    init {
        var context: String? = try {
            SELinux.getContext()
        } catch (_: Throwable) {
            null
        }
        sELinuxContext = context
    }
}