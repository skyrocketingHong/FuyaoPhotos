package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import ing.fuyaoskyrocket.photoinfo.domain.model.PredictiveBackStyle
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens

/** NavHost seeks these transitions with the framework back gesture and restores them on cancellation. */
internal fun navigationEnter(style: PredictiveBackStyle, motion: Boolean, pop: Boolean = false): EnterTransition {
    if (!motion || style == PredictiveBackStyle.NONE) return EnterTransition.None
    val spec = tween<Float>(PhotoMotionTokens.containerMillis)
    return when (style) {
        PredictiveBackStyle.SYSTEM -> fadeIn(tween(PhotoMotionTokens.controlMillis))
        PredictiveBackStyle.SLIDE -> slideInHorizontally(tween(PhotoMotionTokens.containerMillis)) { if (pop) -it / 4 else it } + fadeIn(spec)
        PredictiveBackStyle.SCALE -> scaleIn(spec, initialScale = if (pop) .94f else .88f) + fadeIn(spec)
        PredictiveBackStyle.NONE -> EnterTransition.None
    }
}

internal fun navigationExit(style: PredictiveBackStyle, motion: Boolean, pop: Boolean = false): ExitTransition {
    if (!motion || style == PredictiveBackStyle.NONE) return ExitTransition.None
    val spec = tween<Float>(PhotoMotionTokens.containerMillis)
    return when (style) {
        PredictiveBackStyle.SYSTEM -> fadeOut(tween(PhotoMotionTokens.controlMillis))
        PredictiveBackStyle.SLIDE -> slideOutHorizontally(tween(PhotoMotionTokens.containerMillis)) { if (pop) it else -it / 4 } + fadeOut(spec)
        PredictiveBackStyle.SCALE -> scaleOut(spec, targetScale = if (pop) .88f else 1.04f) + fadeOut(spec)
        PredictiveBackStyle.NONE -> ExitTransition.None
    }
}
