package roro.stellar.manager.ui.navigation.components

import androidx.compose.runtime.compositionLocalOf

data class NavigationState(
    val selectedIndex: Int,
    val onItemClick: (Int) -> Unit
)

val LocalNavigationState = compositionLocalOf<NavigationState?> { null }
