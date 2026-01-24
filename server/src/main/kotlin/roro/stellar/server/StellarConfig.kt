package roro.stellar.server

import com.google.gson.annotations.SerializedName

class StellarConfig {

    @SerializedName("version")
    var version: Int = LATEST_VERSION
    @SerializedName("packages")
    var packages: MutableMap<Int, PackageEntry> = mutableMapOf()

    class PackageEntry() {
        @SerializedName("packages")
        var packages: MutableList<String> = ArrayList()
        @SerializedName("permissions")
        var permissions: MutableMap<String, Int> = mutableMapOf()
    }

    constructor()

    companion object {
        const val LATEST_VERSION: Int = 1
    }
}