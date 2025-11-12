package roro.stellar.manager.ui.navigation

import androidx.navigation.NavController

/**
 * 安全的返回导航
 * 
 * 在执行返回导航前检查导航栈状态，避免在导航栈为空时崩溃。
 */
fun NavController.safePopBackStack(): Boolean {
    return if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        false
    }
}

