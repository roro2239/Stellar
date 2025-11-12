package roro.stellar;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Stellar 常用操作辅助类
 * Stellar Helper Utility Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>检查 Stellar 安装状态 - Check Stellar installation status</li>
 * <li>打开管理器应用 - Open manager app</li>
 * <li>获取服务信息 - Get service information</li>
 * </ul>
 */
public class StellarHelper {

    private static final String SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api";
    private static final String STELLAR_MANAGER_PACKAGE_NAME = "roro.stellar.manager";

    /**
     * 检查 Stellar 管理器应用是否已安装
     * Check if Stellar manager app is installed
     *
     * @param context Context
     * @return true 表示已安装 - true if installed
     */
    public static boolean isManagerInstalled(@NonNull Context context) {
        try {
            context.getPackageManager().getPackageInfo(STELLAR_MANAGER_PACKAGE_NAME, 0);
            return true;
        } catch (Exception e) {
            try {
                context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE_NAME, 0);
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    /**
     * 打开 Stellar 管理器应用
     * Open Stellar manager app
     *
     * @param context Context
     * @return true 表示成功打开 - true if successfully opened
     */
    public static boolean openManager(@NonNull Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(STELLAR_MANAGER_PACKAGE_NAME);
            if (intent == null) {
                intent = context.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME);
            }
            if (intent != null) {
                context.startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            // 忽略异常 - Ignore exception
        }
        return false;
    }

    /**
     * Stellar 服务信息
     * Stellar Service Information
     */
    public static class ServiceInfo {
        /** 服务 UID - Service UID */
        public final int uid;
        /** 服务版本 - Service version */
        public final int version;
        /** SELinux 上下文 - SELinux context */
        public final String seLinuxContext;

        public ServiceInfo(int uid, int version, String seLinuxContext) {
            this.uid = uid;
            this.version = version;
            this.seLinuxContext = seLinuxContext;
        }

        /**
         * 检查服务是否以 root 身份运行
         * Check if service is running as root
         *
         * @return true 表示 uid 为 0（root）
         */
        public boolean isRoot() {
            return uid == 0;
        }

        /**
         * 检查服务是否以 shell (adb) 身份运行
         * Check if service is running as shell (adb)
         *
         * @return true 表示 uid 为 2000（shell）
         */
        public boolean isAdb() {
            return uid == 2000;
        }

        @NonNull
        @Override
        public String toString() {
            return "ServiceInfo{" +
                    "uid=" + uid +
                    ", version=" + version +
                    ", seLinuxContext='" + seLinuxContext + '\'' +
                    '}';
        }
    }

    /**
     * 获取服务信息
     * Get service information
     * 
     * @return 服务信息，如果服务未运行则返回 null - Service info, or null if service is not running
     */
    @Nullable
    public static ServiceInfo getServiceInfo() {
        if (!Stellar.pingBinder()) {
            return null;
        }

        try {
            return new ServiceInfo(
                Stellar.getUid(),
                Stellar.getVersion(),
                Stellar.getSELinuxContext()
            );
        } catch (Exception e) {
            return null;
        }
    }
}

