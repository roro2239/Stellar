package roro.stellar.server.service.info

import android.os.Build
import android.os.SELinux
import android.system.Os
import roro.stellar.StellarApiConstants
import roro.stellar.server.util.OsUtils

class ServiceInfoProvider(
    private val versionProvider: VersionProvider
) {
    fun getVersion(): Int {
        return StellarApiConstants.SERVER_VERSION
    }

    fun getUid(): Int {
        return Os.getuid()
    }

    fun getSELinuxContext(): String? {
        return try {
            SELinux.getContext()
        } catch (tr: Throwable) {
            null
        }
    }

    fun getVersionName(): String? {
        return versionProvider.getVersionName()
    }

    fun getVersionCode(): Int {
        return versionProvider.getVersionCode()
    }
}
