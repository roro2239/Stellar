package roro.stellar.manager.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

data class ScreenConfig(
    val isLandscape: Boolean,
    val gridColumns: Int
)

val LocalScreenConfig = compositionLocalOf {
    ScreenConfig(isLandscape = false, gridColumns = 1)
}

@Composable
fun AdaptiveLayoutProvider(
    portraitColumns: Int = 1,
    landscapeColumns: Int = 2,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gridColumns = if (isLandscape) landscapeColumns else portraitColumns

    val screenConfig = ScreenConfig(
        isLandscape = isLandscape,
        gridColumns = gridColumns
    )

    CompositionLocalProvider(LocalScreenConfig provides screenConfig) {
        content()
    }
}
