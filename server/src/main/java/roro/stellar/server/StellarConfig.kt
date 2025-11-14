package roro.stellar.server;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Stellar配置类
 * Stellar Configuration Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>存储应用权限配置信息 - Stores app permission configuration</li>
 * <li>支持JSON序列化和反序列化 - Supports JSON serialization/deserialization</li>
 * <li>管理多个应用包的权限状态 - Manages permission status for multiple app packages</li>
 * </ul>
 * 
 * <p>配置格式 Configuration Format：</p>
 * <ul>
 * <li>version: 配置版本号</li>
 * <li>packages: 包权限列表（按UID分组）</li>
 * </ul>
 */
public class StellarConfig {

    /** 最新配置版本 Latest configuration version */
    public static final int LATEST_VERSION = 2;

    /** 配置版本 Configuration version */
    @SerializedName("version")
    public int version = LATEST_VERSION;

    /** 包权限列表 Package permission list */
    @SerializedName("packages")
    public List<PackageEntry> packages = new ArrayList<>();

    /**
     * 包权限条目
     * Package Permission Entry
     * 
     * <p>按UID分组存储应用包权限</p>
     */
    public static class PackageEntry extends ConfigPackageEntry {

        /** 应用UID Application UID */
        @SerializedName("uid")
        public final int uid;

        /** 权限标志 Permission flags */
        @SerializedName("flags")
        public int flags;

        /** 包名列表 Package names */
        @SerializedName("packages")
        public List<String> packages;

        /**
         * 构造包权限条目
         * Construct package permission entry
         * 
         * @param uid 应用UID
         * @param flags 权限标志
         */
        public PackageEntry(int uid, int flags) {
            this.uid = uid;
            this.flags = flags;
            this.packages = new ArrayList<>();
        }

        /**
         * 是否已授权
         * Whether allowed
         */
        @Override
        public boolean isAllowed() {
            return (flags & ConfigManager.FLAG_ALLOWED) != 0;
        }

        /**
         * 是否已拒绝
         * Whether denied
         */
        @Override
        public boolean isDenied() {
            return (flags & ConfigManager.FLAG_DENIED) != 0;
        }
    }

    /** 默认构造函数 Default constructor */
    public StellarConfig() {
    }

    /**
     * 从包列表构造配置
     * Construct configuration from package list
     * 
     * @param packages 包权限列表
     */
    public StellarConfig(@NonNull List<PackageEntry> packages) {
        this.version = LATEST_VERSION;
        this.packages = packages;
    }
}

