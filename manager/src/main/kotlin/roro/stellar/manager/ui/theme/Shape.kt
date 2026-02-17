package roro.stellar.manager.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import roro.stellar.manager.ui.components.G2RoundedCorners.g2

@Immutable
data class AppShapes(
    val iconMedium18: ContinuousRoundedRectangle = ContinuousRoundedRectangle(18.dp, continuity = g2),
    val iconSmall: ContinuousRoundedRectangle = ContinuousRoundedRectangle(12.dp, continuity = g2),
    val cardLarge: ContinuousRoundedRectangle = ContinuousRoundedRectangle(24.dp, continuity = g2),
    val cardMedium24: ContinuousRoundedRectangle = ContinuousRoundedRectangle(24.dp, continuity = g2),
    val cardMedium: ContinuousRoundedRectangle = ContinuousRoundedRectangle(16.dp, continuity = g2),
    val buttonMedium: ContinuousRoundedRectangle = ContinuousRoundedRectangle(16.dp, continuity = g2),
    val buttonSmall14: ContinuousRoundedRectangle = ContinuousRoundedRectangle(14.dp, continuity = g2),
    val inputField: ContinuousRoundedRectangle = ContinuousRoundedRectangle(16.dp, continuity = g2),
    val dialog: ContinuousRoundedRectangle = ContinuousRoundedRectangle(28.dp, continuity = g2),
    val dialogContent: ContinuousRoundedRectangle = ContinuousRoundedRectangle(16.dp, continuity = g2),
    val tag: ContinuousRoundedRectangle = ContinuousRoundedRectangle(6.dp, continuity = g2),
)

object AppShape {
    val shapes = AppShapes()
}

