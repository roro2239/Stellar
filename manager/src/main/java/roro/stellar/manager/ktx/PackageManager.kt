package roro.stellar.manager.ktx

import android.content.ComponentName
import android.content.pm.PackageManager

/**
 * PackageManager扩展函数
 * PackageManager Extension Functions
 * 
 * 功能说明 Features：
 * - 简化组件启用/禁用操作 - Simplifies component enable/disable operations
 * - 智能检测组件状态 - Intelligently detects component state
 * - 避免不必要的状态更改 - Avoids unnecessary state changes
 */

/**
 * 设置组件启用状态
 * Set component enabled state
 * 
 * @param componentName 组件名称
 * @param enabled true表示启用，false表示禁用
 * 
 * 注意：仅在状态确实需要改变时才执行操作
 */
fun PackageManager.setComponentEnabled(componentName: ComponentName, enabled: Boolean) {
    val oldState = getComponentEnabledSetting(componentName)
    val newState = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    if (newState != oldState) {
        val flags = PackageManager.DONT_KILL_APP
        setComponentEnabledSetting(componentName, newState, flags)
    }
}

/**
 * 检查组件是否启用
 * Check if component is enabled
 * 
 * @param componentName 组件名称
 * @param defaultValue 默认状态时的返回值
 * @return true表示启用，false表示禁用
 */
fun PackageManager.isComponentEnabled(componentName: ComponentName, defaultValue: Boolean = true): Boolean {
    return when (getComponentEnabledSetting(componentName)) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> defaultValue
        else -> false
    }
}

