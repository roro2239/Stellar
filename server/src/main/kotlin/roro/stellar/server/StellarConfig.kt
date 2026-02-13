package roro.stellar.server

import com.google.gson.annotations.SerializedName

class StellarConfig {

    @SerializedName("version")
    var version: Int = LATEST_VERSION
    @SerializedName("packages")
    var packages: MutableMap<Int, PackageEntry> = mutableMapOf()
    @SerializedName("shizukuCompatEnabled")
    var shizukuCompatEnabled: Boolean = true

    class PackageEntry {
        @SerializedName("packages")
        var packages: MutableList<String> = ArrayList()
        @SerializedName("permissions")
        var permissions: MutableMap<String, Int> = mutableMapOf()
    }

    companion object {
        const val LATEST_VERSION: Int = 1
    }
}