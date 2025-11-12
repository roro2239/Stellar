package roro.stellar.manager.ui.components

import com.kyant.capsule.continuities.G2Continuity
import com.kyant.capsule.continuities.G2ContinuityProfile

object G2RoundedCorners {
    val g2 = G2Continuity(
        profile = G2ContinuityProfile.RoundedRectangle.copy(
            extendedFraction = 0.5,
            arcFraction = 0.5,
            bezierCurvatureScale = 1.1,
            arcCurvatureScale = 1.1
        ),
        capsuleProfile = G2ContinuityProfile.Capsule.copy(
            extendedFraction = 0.25,
            arcFraction = 0.25
        )
    )
}

