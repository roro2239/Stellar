package roro.stellar.manager.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

data class ScreenConfig(
    val isLandscape: Boolean,
    val gridColumns: Int
)

val LocalScreenConfig = compositionLocalOf {
    ScreenConfig(isLandscape = false, gridColumns = 1)
}

private const val SMALL_SCREEN_THRESHOLD_DP = 300
private const val DESIGN_WIDTH_DP = 360f

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

    val screenWidthDp = configuration.screenWidthDp
    val currentDensity = LocalDensity.current

    val adjustedDensity = if (screenWidthDp < SMALL_SCREEN_THRESHOLD_DP) {
        val scale = screenWidthDp / DESIGN_WIDTH_DP
        Density(
            density = currentDensity.density * scale,
            fontScale = currentDensity.fontScale * scale
        )
    } else {
        currentDensity
    }

    CompositionLocalProvider(
        LocalScreenConfig provides screenConfig,
        LocalDensity provides adjustedDensity
    ) {
        content()
    }
}
