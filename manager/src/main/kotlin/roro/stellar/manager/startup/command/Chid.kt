package roro.stellar.manager.startup.command

import roro.stellar.manager.application
import java.io.File

object Chid {

    private val chidFile = File(application.applicationInfo.nativeLibraryDir, "libchid.so")

    val path: String = chidFile.absolutePath
}
