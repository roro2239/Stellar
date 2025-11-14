package roro.stellar

import android.content.Context
import roro.stellar.Stellar.pingBinder
import roro.stellar.Stellar.sELinuxContext
import roro.stellar.Stellar.uid
import roro.stellar.Stellar.version

/**
 * Stellar 常用操作辅助类
 * Stellar Helper Utility Class
 *
 *
 * 功能说明 Features：
 *
 *  * 检查 Stellar 安装状态 - Check Stellar installation status
 *  * 打开管理器应用 - Open manager app
 *  * 获取服务信息 - Get service information
 *
 */
object StellarHelper {
    private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    private const val STELLAR_MANAGER_PACKAGE_NAME = "roro.stellar.manager"

    /**
     * 检查 Stellar 管理器应用是否已安装
     * Check if Stellar manager app is installed
     *
     * @param context Context
     * @return true 表示已安装 - true if installed
     */
    fun isManagerInstalled(context: Context): Boolean {
        try {
            context.packageManager.getPackageInfo(STELLAR_MANAGER_PACKAGE_NAME, 0)
            return true
        } catch (_: Exception) {
            try {
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
                return true
            } catch (_: Exception) {
                return false
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
    fun openManager(context: Context): Boolean {
        try {
            var intent = context.packageManager.getLaunchIntentForPackage(
                STELLAR_MANAGER_PACKAGE_NAME
            )
            if (intent == null) {
                intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
            }
            if (intent != null) {
                context.startActivity(intent)
                return true
            }
        } catch (_: Exception) {
            // 忽略异常 - Ignore exception
        }
        return false
    }

    val serviceInfo: ServiceInfo?
        /**
         * 获取服务信息
         * Get service information
         *
         * @return 服务信息，如果服务未运行则返回 null - Service info, or null if service is not running
         */
        get() {
            if (!pingBinder()) {
                return null
            }

            return try {
                ServiceInfo(
                    uid,
                    version,
                    sELinuxContext
                )
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Stellar 服务信息
     * Stellar Service Information
     */
    class ServiceInfo(
        /** 服务 UID - Service UID  */
        val uid: Int,
        /** 服务版本 - Service version  */
        val version: Int,
        /** SELinux 上下文 - SELinux context  */
        val seLinuxContext: String?
    ) {
        val isRoot: Boolean
            /**
             * 检查服务是否以 root 身份运行
             * Check if service is running as root
             *
             * @return true 表示 uid 为 0（root）
             */
            get() = uid == 0

        val isAdb: Boolean
            /**
             * 检查服务是否以 shell (adb) 身份运行
             * Check if service is running as shell (adb)
             *
             * @return true 表示 uid 为 2000（shell）
             */
            get() = uid == 2000

        override fun toString(): String {
            return "ServiceInfo{" +
                    "uid=" + uid +
                    ", version=" + version +
                    ", seLinuxContext='" + seLinuxContext + '\'' +
                    '}'
        }
    }
}