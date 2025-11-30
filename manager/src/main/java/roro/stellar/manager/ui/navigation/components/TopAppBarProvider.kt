package roro.stellar.manager.ui.navigation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

@OptIn(ExperimentalMaterial3Api::class)
val LocalTopAppBarState = compositionLocalOf<TopAppBarState?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarProvider(
    content: @Composable () -> Unit
) {
    val topAppBarState = rememberTopAppBarState()
    
    CompositionLocalProvider(
        LocalTopAppBarState provides topAppBarState
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCurrentTopAppBarState(): TopAppBarState {
    return LocalTopAppBarState.current ?: rememberTopAppBarState()
}

