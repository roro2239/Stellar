package roro.stellar.manager.ui.features.starter

import roro.stellar.manager.StellarSettings
import roro.stellar.manager.application
import java.io.File

object Starter {

    private val starterFile = File(application.applicationInfo.nativeLibraryDir, "libstellar.so")

    val userCommand: String = starterFile.absolutePath

    val adbCommand = "adb shell $userCommand"

    val internalCommand: String
        get() {
            val baseCommand = "$userCommand --apk=${application.applicationInfo.sourceDir}"
            val dropPrivileges = StellarSettings.getPreferences()
                .getBoolean(StellarSettings.DROP_PRIVILEGES, false)
            return if (dropPrivileges) {
                "${Chid.path} 2000 $baseCommand"
            } else {
                baseCommand
            }
        }
}

