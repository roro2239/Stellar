package roro.stellar.server.util

import android.content.pm.PackageInfo

object ProviderDiscovery {
    private const val AUTHORITY_SEPARATOR = ";"

    fun hasProvider(packageInfo: PackageInfo, suffix: String): Boolean {
        val packageName = packageInfo.packageName ?: return false
        val expectedAuthority = "$packageName$suffix"
        return packageInfo.providers?.any { provider ->
            provider.authority
                ?.split(AUTHORITY_SEPARATOR)
                ?.any { it.trim() == expectedAuthority } == true
        } == true
    }

    fun hasStellarProvider(packageInfo: PackageInfo): Boolean = hasProvider(packageInfo, ".stellar")

    fun hasShizukuProvider(packageInfo: PackageInfo): Boolean = hasProvider(packageInfo, ".shizuku")
}
