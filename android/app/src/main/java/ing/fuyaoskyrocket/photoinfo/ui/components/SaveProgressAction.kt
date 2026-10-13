package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import ing.fuyaoskyrocket.photoinfo.domain.motion.ParticleMotion
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SavingSymbol(active: Boolean, modifier: Modifier = Modifier, completed: Int? = null, total: Int = 0,
    showsIcon: Boolean = true) {
    val motion = LocalPhotoMotionEnabled.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val running = active && motion && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(active, completed, total, motion) {
        val target = ParticleMotion.progress(completed ?: 0, total)
        if (!active || !motion || target < fraction.value) fraction.snapTo(target)
        else fraction.animateTo(target, tween(PhotoMotionTokens.progressMillis, easing = LinearOutSlowInEasing))
    }
    val inset = animateFloatAsState(if (active && (motion || completed != null)) .58f else 1f,
        if (motion) tween(PhotoMotionTokens.controlMillis) else snap(), label = "save symbol size")
    val color = LocalContentColor.current
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        if (showsIcon) {
            Icon(painterResource(R.drawable.ic_export), null, Modifier.fillMaxSize().graphicsLayer {
                scaleX = inset.value; scaleY = inset.value
            })
        }
        if (active && completed != null) {
            CircularProgressIndicator(progress = { fraction.value }, modifier = Modifier.matchParentSize(),
                color = color, trackColor = color.copy(alpha = .18f), strokeWidth = 1.8.dp)
        } else if (running) {
            CircularProgressIndicator(modifier = Modifier.matchParentSize(), color = color,
                trackColor = color.copy(alpha = .18f), strokeWidth = 1.8.dp)
        }
    }
}

@Composable
fun SaveProgressAction(label: String, saving: Boolean, completed: Int, total: Int,
    enabled: Boolean, onClick: () -> Unit) {
    val count = completed.coerceIn(0, maxOf(1, total))
    val showsCount = saving && (total > 1 || !LocalPhotoMotionEnabled.current)
    val progress = stringResource(R.string.save_progress, count, maxOf(1, total))
    IconButton(onClick, enabled = enabled && !saving, modifier = Modifier.size(56.dp).semantics {
        contentDescription = label
        if (saving) {
            stateDescription = progress
            progressBarRangeInfo = if (total > 1) ProgressBarRangeInfo(count.toFloat(), 0f..total.toFloat()) else ProgressBarRangeInfo.Indeterminate
            liveRegion = LiveRegionMode.Polite
        }
    }, colors = IconButtonDefaults.iconButtonColors(
        disabledContentColor = if (saving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))) {
        Box(Modifier.size(28.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            SavingSymbol(saving, Modifier.size(24.dp), completed.takeIf { total > 1 }, total,
                showsIcon = !showsCount)
            if (showsCount) Text(if (total > 1) "$count" else "$count/${maxOf(1, total)}", maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"))
        }
    }
}
