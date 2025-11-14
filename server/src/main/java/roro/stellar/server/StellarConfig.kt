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
    /** 配置版本 Configuration version  */
    @SerializedName("version")
    var version: Int = LATEST_VERSION

    /** 包权限列表 Package permission list  */
    @SerializedName("packages")
    var packages: MutableList<PackageEntry> = ArrayList<PackageEntry>()

    /**
     * 包权限条目
     * Package Permission Entry
     *
     *
     * 按UID分组存储应用包权限
     */
    class PackageEntry(
        /** 应用UID Application UID  */
        @field:SerializedName("uid") val uid: Int,
        /** 权限标志 Permission flags  */
        @field:SerializedName("flags") var flags: Int
    ) : ConfigPackageEntry() {
        /** 包名列表 Package names  */
        @SerializedName("packages")
        var packages = ArrayList<String?>()

        override val isAllowed: Boolean
            /**
             * 是否已授权
             * Whether allowed
             */
            get() = (flags and ConfigManager.FLAG_ALLOWED) != 0

        override val isDenied: Boolean
            /**
             * 是否已拒绝
             * Whether denied
             */
            get() = (flags and ConfigManager.FLAG_DENIED) != 0
    }

    /** 默认构造函数 Default constructor  */
    constructor()

    /**
     * 从包列表构造配置
     * Construct configuration from package list
     *
     * @param packages 包权限列表
     */
    constructor(packages: MutableList<PackageEntry>) {
        this.version = LATEST_VERSION
        this.packages = packages
    }

    companion object {
        /** 最新配置版本 Latest configuration version  */
        const val LATEST_VERSION: Int = 2
    }
}