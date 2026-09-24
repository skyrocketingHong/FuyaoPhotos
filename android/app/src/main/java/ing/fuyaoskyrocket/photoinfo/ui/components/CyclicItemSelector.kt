package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun CyclicItemSelector(labels:List<String>,selected:Int,enabled:Boolean,modifier:Modifier,onSelect:(Int)->Unit) {
    val count=labels.size
    require(count>1 && selected in labels.indices)
    val middle=count*50
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val scope=rememberCoroutineScope()
    val rowHeight=with(LocalDensity.current) { maxOf(56f,48f*fontScale).dp }
    BoxWithConstraints(modifier) {
        val rowHeightPx=with(LocalDensity.current) { rowHeight.roundToPx() }
        val centerOffsetPx=with(LocalDensity.current) {
            ((maxHeight-rowHeight)/2).coerceAtLeast(0.dp).roundToPx()
        }
        val precedingRows=(centerOffsetPx+rowHeightPx-1)/rowHeightPx
        val firstOffsetPx=precedingRows*rowHeightPx-centerOffsetPx
        val state=rememberLazyListState(
            initialFirstVisibleItemIndex=middle+selected-precedingRows,
            initialFirstVisibleItemScrollOffset=firstOffsetPx)
        val centered by remember(state) {
            derivedStateOf {
                val layout=state.layoutInfo
                val center=layout.viewportSize.height/2
                layout.visibleItemsInfo.minByOrNull { abs(it.offset+it.size/2-center) }?.index
            }
        }
        var lastReported by remember { mutableIntStateOf(selected) }
        LaunchedEffect(state,count,rowHeightPx,centerOffsetPx) {
            state.scrollToItem(middle+currentSelected-precedingRows,firstOffsetPx)
            snapshotFlow { if(state.isScrollInProgress) null else centered }
                .distinctUntilChanged().collect { index ->
                    if(index!=null) {
                        val value=index%count
                        if(value!=currentSelected && value!=lastReported) {
                            lastReported=value
                            select(value)
                        }
                        if(index !in middle-count..middle+count*2) {
                            state.scrollToItem(middle+value-precedingRows,firstOffsetPx)
                        }
                    }
                }
        }
        LaunchedEffect(selected,centerOffsetPx) {
            lastReported=selected
            val at=centered
            if(!state.isScrollInProgress && at!=null && at%count!=selected) {
                state.scrollToItem(middle+selected-precedingRows,firstOffsetPx)
            }
        }
        Box(Modifier.fillMaxWidth().height(rowHeight)
            .align(Alignment.Center)
            .background(MaterialTheme.colorScheme.secondaryContainer,MaterialTheme.shapes.large))
        LazyColumn(state=state,userScrollEnabled=enabled,
            flingBehavior=rememberSnapFlingBehavior(state,snapPosition=SnapPosition.Center),
            modifier=Modifier.fillMaxSize().graphicsLayer { compositingStrategy=CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val edge=(rowHeightPx*1.5f/size.height).coerceIn(.12f,.35f)
                    drawRect(Brush.verticalGradient(
                        0f to Color.Transparent,edge to Color.Black,
                        (1f-edge) to Color.Black,1f to Color.Transparent),blendMode=BlendMode.DstIn)
                }.selectableGroup()) {
            items(count*101,key={ it }) { position ->
                val active=position==(centered ?: middle+selected)
                Box(Modifier.fillMaxWidth().height(rowHeight)
                    .selectable(selected=active,enabled=enabled,role=Role.RadioButton) {
                        scope.launch { state.animateScrollToItem(position-precedingRows,firstOffsetPx) }
                    }.padding(horizontal=8.dp),contentAlignment=Alignment.Center) {
                    Text(labels[position%count],style=if(active)MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
