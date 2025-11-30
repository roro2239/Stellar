package roro.stellar.server

import com.google.gson.annotations.SerializedName

/**
 * Stellar配置类
 * Stellar Configuration Class
 *
 *
 * 功能说明 Features：
 *
 *  * 存储应用权限配置信息 - Stores app permission configuration
 *  * 支持JSON序列化和反序列化 - Supports JSON serialization/deserialization
 *  * 管理多个应用包的权限状态 - Manages permission status for multiple app packages
 *
 *
 *
 * 配置格式 Configuration Format：
 *
 *  * version: 配置版本号
 *  * packages: 包权限列表（按UID分组）
 *
 */
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

    /** 默认构造函数 Default constructor  */
    constructor()

    companion object {
        /** 最新配置版本 Latest configuration version  */
        const val LATEST_VERSION: Int = 1
    }
}