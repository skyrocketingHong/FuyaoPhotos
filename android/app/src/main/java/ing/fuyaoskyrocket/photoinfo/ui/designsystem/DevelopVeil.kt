package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The develop settle: a brief exposure flash fades out over the photo when its workspace
 * first appears — the print develops. It runs once per workspace (paging between already
 * open photos does not re-trigger it) and is skipped when system animations are removed.
 * The veil only covers the media, so the HDR preview itself is untouched.
 */
@Composable
fun DevelopVeil(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    var arrived by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val animationsAllowed = remember {
        Settings.Global.getFloat(context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val flash by animateFloatAsState(
        targetValue = if (arrived || !animationsAllowed) 0f else 0.16f,
        animationSpec = tween(durationMillis = 320),
        label = "develop",
    )
    LaunchedEffect(Unit) { arrived = true }
    Box(modifier, contentAlignment = contentAlignment) {
        content()
        if (flash > 0f) {
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = flash)))
        }
    }
}
