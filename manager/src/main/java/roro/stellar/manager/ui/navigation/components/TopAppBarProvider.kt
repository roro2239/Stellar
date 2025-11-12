package roro.stellar.manager.ui.navigation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * 顶部应用栏状态的CompositionLocal
 * 
 * 提供全局的TopAppBarState访问，确保整个应用的顶部栏状态一致性。
 */
@OptIn(ExperimentalMaterial3Api::class)
val LocalTopAppBarState = compositionLocalOf<TopAppBarState?> { null }

/**
 * 统一的顶部应用栏状态提供者
 * 
 * 为整个应用提供统一的TopAppBarState管理。
 */
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

/**
 * 获取当前的TopAppBarState
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCurrentTopAppBarState(): TopAppBarState {
    return LocalTopAppBarState.current ?: rememberTopAppBarState()
}

