package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
internal fun PendingPhotoEffect(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val radius = animateDpAsState(if (active) 7.dp else 0.dp, tween(180), label = "preview blur")
    val light = animateFloatAsState(if (active) 1f else 0f, tween(180), label = "preview light")
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier) {
        Box(Modifier.fillMaxSize().blur(radius.value), content = content)
        if (light.value > 0f) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = light.value }
                    .background(Brush.radialGradient(listOf(
                        accent.copy(alpha = .12f),
                        MaterialTheme.colorScheme.surface.copy(alpha = .10f),
                        Color.Transparent,
                    ))),
            )
        }
    }
}
