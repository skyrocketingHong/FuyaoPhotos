package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoIconButton
import kotlinx.coroutines.delay

@Composable
fun EditorPreviewPane(state:EditorState,original:Boolean,onOriginal:()->Unit,onEnlarge:()->Unit,modifier:Modifier=Modifier,bottomSafe:Boolean=false) {
    var delayedRendering by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy,state.rendering) {
        delayedRendering=false
        if(state.rendering&&!state.busy) { delay(200);delayedRendering=true }
    }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PhotoPreview(if(original)state.original else state.preview,Modifier.fillMaxSize())
            // Overlay feedback never changes the image's Fit bounds or the inspector's height.
            if(state.busy||delayedRendering)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if(state.exporting)Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.inverseSurface) {
                Text(stringResource(R.string.exporting),Modifier.padding(12.dp),color=MaterialTheme.colorScheme.inverseOnSurface,style=MaterialTheme.typography.bodySmall)
            }
        }
        Surface(color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(if(bottomSafe)WindowInsets.navigationBars.only(WindowInsetsSides.Bottom) else WindowInsets(0,0,0,0)).heightIn(min=48.dp).padding(start=16.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${state.width} × ${state.height}",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(original,onClick=onOriginal,label={ Text(stringResource(R.string.original)) })
                FuyaoIconButton(R.drawable.ic_expand,stringResource(R.string.enlarge),onEnlarge,enabled=state.preview!=null)
            }
        }
    }
}
