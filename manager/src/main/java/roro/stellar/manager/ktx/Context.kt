package roro.stellar.manager.ktx

import android.content.Context
import android.os.UserManager
import roro.stellar.manager.StellarApplication

/**
 * Context扩展函数
 * Context Extension Functions
 * 
 * 提供Context相关的便捷扩展方法
 * Provides convenient extension methods for Context
 */

/**
 * 获取StellarApplication实例
 * Get StellarApplication instance
 * 
 * 从任意Context快速访问Application对象
 * Quick access to Application object from any Context
 */
val Context.application: StellarApplication
    get() {
        return applicationContext as StellarApplication
    }

/**
 * 创建Device Protected Storage上下文（兼容版本）
 * Create Device Protected Storage context (compatible version)
 * 
 * Device Protected Storage在设备锁定前仍可访问
 * Device Protected Storage is accessible before device unlock
 */
fun Context.createDeviceProtectedStorageContextCompat(): Context {
    return createDeviceProtectedStorageContext()
}

/**
 * 在设备锁定时创建Device Protected Storage上下文
 * Create Device Protected Storage context when device is locked
 * 
 * @return 如果用户已解锁则返回当前Context，否则返回Device Protected Storage上下文
 */
fun Context.createDeviceProtectedStorageContextCompatWhenLocked(): Context {
    return if (getSystemService(UserManager::class.java)?.isUserUnlocked != true) {
        createDeviceProtectedStorageContext()
    } else {
        this
    }
}

