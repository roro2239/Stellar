package roro.stellar.manager

import android.os.Bundle
import roro.stellar.StellarProvider

/**
 * Stellar Manager ContentProvider
 * 
 * 功能说明 Features：
 * - 继承StellarProvider，接收来自服务端的Binder - Extends StellarProvider to receive Binder from server
 * - 处理跨进程Binder传递 - Handles cross-process Binder transfer
 * - 验证调用参数有效性 - Validates call parameters
 * 
 * 使用场景 Use Cases：
 * - 在Manifest中声明，允许服务端发送Binder - Declared in Manifest to allow server to send Binder
 * - 作为Manager应用的Binder接收入口 - Serves as Binder receiving entry for Manager app
 */
class StellarManagerProvider : StellarProvider() {

    /**
     * Provider初始化
     * Provider initialization
     */
    override fun onCreate(): Boolean {
        return super.onCreate()
    }

    /**
     * 处理ContentProvider调用
     * Handle ContentProvider call
     * 
     * @param method 方法名
     * @param arg 参数
     * @param extras 额外数据
     * @return 返回结果
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // 验证extras不为null
        if (extras == null) return null
        return super.call(method, arg, extras)
    }
}

