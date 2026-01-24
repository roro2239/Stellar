package roro.stellar.manager.ui.features.starter

import roro.stellar.manager.application
import java.io.File

object Chid {

    private val chidFile = File(application.applicationInfo.nativeLibraryDir, "libchid.so")

    val path: String = chidFile.absolutePath

    val exists: Boolean
        get() = chidFile.exists()

    fun commandForUid(uid: Int): String = "$path $uid"

    fun commandForUid(uid: Int, command: String): String = "$path $uid $command"

    fun commandForUidGid(uid: Int, gid: Int): String = "$path $uid,$gid"

    fun commandForUidGid(uid: Int, gid: Int, command: String): String {
        return "$path $uid,$gid $command"
    }

    fun commandForShell(): String = commandForUid(2000)

    fun commandForShell(command: String): String = commandForUid(2000, command)
}
