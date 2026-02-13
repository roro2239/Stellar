package roro.stellar.server.service.info

import roro.stellar.StellarApiConstants
import roro.stellar.server.util.OsUtils

class ServiceInfoProvider(
    private val versionProvider: VersionProvider
) {
    fun getVersion(): Int = StellarApiConstants.SERVER_VERSION

    fun getUid(): Int = OsUtils.uid

    fun getSELinuxContext(): String? = OsUtils.sELinuxContext

    fun getVersionName(): String? = versionProvider.getVersionName()

    fun getVersionCode(): Int = versionProvider.getVersionCode()
}
