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

@Composable
fun SavingSymbol(active: Boolean, modifier: Modifier = Modifier) {
    val motion = LocalPhotoMotionEnabled.current
    val alpha = if (active && motion) {
        val transition = rememberInfiniteTransition(label = "saving")
        val value by transition.animateFloat(.45f, 1f,
            infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "saving symbol")
        value
    } else 1f
    Icon(painterResource(R.drawable.ic_export), null,
        modifier.graphicsLayer { this.alpha = alpha })
}

@Composable
fun SaveProgressAction(label: String, saving: Boolean, completed: Int, total: Int,
    enabled: Boolean, onClick: () -> Unit) {
    val progress = stringResource(R.string.save_progress, completed, maxOf(1, total))
    IconButton(onClick, enabled = enabled && !saving, modifier = Modifier.size(56.dp).semantics {
        contentDescription = label
        if (saving) {
            stateDescription = progress
            progressBarRangeInfo = ProgressBarRangeInfo(completed.toFloat(), 0f..maxOf(1, total).toFloat())
            liveRegion = LiveRegionMode.Polite
        }
    }, colors = IconButtonDefaults.iconButtonColors(
        disabledContentColor = if (saving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SavingSymbol(saving, Modifier.size(if (saving) 22.dp else 28.dp))
            if (saving) Text("$completed/${maxOf(1, total)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
