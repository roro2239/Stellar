package roro.stellar.manager.ui.navigation

import androidx.navigation.NavController

fun NavController.safePopBackStack(): Boolean =
    if (previousBackStackEntry != null) popBackStack() else false
