package roro.stellar.manager.ui.navigation

import androidx.navigation.NavController

fun NavController.safePopBackStack(): Boolean {
    return if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        false
    }
}

