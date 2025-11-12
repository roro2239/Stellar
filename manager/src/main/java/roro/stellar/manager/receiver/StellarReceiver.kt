package roro.stellar.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.model.ServiceStatus
import roro.stellar.manager.ui.features.starter.SelfStarterService

/**
 * Stellar广播接收器
 * Stellar Broadcast Receiver
 * 
 * 功能说明 Features：
 * - 接收系统广播事件 - Receives system broadcast events
 * - 处理无线ADB自动启动 - Handles wireless ADB auto startup
 * - 检查服务运行状态 - Checks service running status
 * - 启动Stellar服务 - Starts Stellar service
 * 
 * 工作流程 Workflow：
 * - 1. 接收广播
 * - 2. 检查服务是否已运行
 * - 3. 检查无线ADB开机启动设置
 * - 4. 验证WiFi连接并启用无线ADB
 * - 5. 启动Stellar服务
 * 
 * 注意事项 Notes：
 * - 仅在服务未运行时处理
 * - 需要无线ADB开机启动选项已启用
 * - 需要WiFi连接
 */
class StellarReceiver : BroadcastReceiver() {
    /** 无线ADB辅助类 Wireless ADB helper */
    private val adbWirelessHelper = AdbWirelessHelper()

    /**
     * 接收广播
     * Receive broadcast
     * 
     * @param context 上下文
     * @param intent 广播Intent
     */
    override fun onReceive(context: Context, intent: Intent) {
        // 检查服务是否已运行
        if (!ServiceStatus().isRunning) {
            // 检查无线ADB开机启动选项
            val startOnBootWirelessIsEnabled = StellarSettings.getPreferences()
                .getBoolean(StellarSettings.KEEP_START_ON_BOOT_WIRELESS, false)
            if (startOnBootWirelessIsEnabled) {
                // 验证WiFi并启用无线ADB
                val wirelessAdbStatus = adbWirelessHelper.validateThenEnableWirelessAdb(
                    context.contentResolver, context
                )
                if (wirelessAdbStatus) {
                    // 启动Stellar服务
                    val intentService = Intent(context, SelfStarterService::class.java)
                    context.startService(intentService)
                }
            }
        }
    }
}

