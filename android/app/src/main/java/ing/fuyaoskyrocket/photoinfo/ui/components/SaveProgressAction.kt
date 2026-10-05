package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
fun SavingSymbol(active: Boolean, modifier: Modifier = Modifier, completed: Int? = null, total: Int = 0) {
    val motion = LocalPhotoMotionEnabled.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val running = active && motion && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val rotation = if (running) {
        rememberInfiniteTransition(label = "save activity").animateFloat(0f, 360f,
            infiniteRepeatable(tween(PhotoMotionTokens.rotationMillis, easing = LinearEasing)), label = "save orbit")
    } else rememberUpdatedState(0f)
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(active, completed, total, motion) {
        val target = ParticleMotion.progress(completed ?: 0, total)
        if (!active || !motion || target < fraction.value) fraction.snapTo(target)
        else fraction.animateTo(target, tween(PhotoMotionTokens.progressMillis, easing = LinearOutSlowInEasing))
    }
    val inset = animateFloatAsState(if (active && (motion || completed != null)) .58f else 1f,
        if (motion) tween(PhotoMotionTokens.controlMillis) else snap(), label = "save symbol size")
    val color = LocalContentColor.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_export), null, Modifier.fillMaxSize().graphicsLayer {
            scaleX = inset.value; scaleY = inset.value
        })
        if (active && (motion || completed != null)) Canvas(Modifier.matchParentSize()) {
            val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
            val gap = stroke.width / 2
            val bounds = size.copy(width = size.width - stroke.width, height = size.height - stroke.width)
            val origin = androidx.compose.ui.geometry.Offset(gap, gap)
            drawArc(color.copy(alpha = .18f), -90f, 360f, false, origin, bounds, style = stroke)
            // Rotation conveys work within the current item; only completed items set the sweep.
            val sweep = if (completed == null) 90f else maxOf(if (motion) 12f else 0f, fraction.value * 360f)
            drawArc(color, rotation.value - 90f, sweep, false, origin, bounds, style = stroke)
        }
    }
}

@Composable
fun SaveProgressAction(label: String, saving: Boolean, completed: Int, total: Int,
    enabled: Boolean, onClick: () -> Unit) {
    val count = completed.coerceIn(0, maxOf(1, total))
    val progress = stringResource(R.string.save_progress, count, maxOf(1, total))
    IconButton(onClick, enabled = enabled && !saving, modifier = Modifier.size(56.dp).semantics {
        contentDescription = label
        if (saving) {
            stateDescription = progress
            progressBarRangeInfo = if (total > 0) ProgressBarRangeInfo(count.toFloat(), 0f..total.toFloat()) else ProgressBarRangeInfo.Indeterminate
            liveRegion = LiveRegionMode.Polite
        }
    }, colors = IconButtonDefaults.iconButtonColors(
        disabledContentColor = if (saving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SavingSymbol(saving, Modifier.size(28.dp), completed, total)
            if (saving) Text("$count/${maxOf(1, total)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
