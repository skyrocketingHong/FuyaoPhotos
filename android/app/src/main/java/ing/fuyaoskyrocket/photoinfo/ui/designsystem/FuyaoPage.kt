package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FuyaoPageIntro(title: String, description: String, @DrawableRes icon: Int,
    actions: @Composable ColumnScope.() -> Unit = {}) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(FuyaoSpacing.cardInset), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ViewfinderMark(painterResource(icon))
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}

/** Camera-viewfinder framing for page intros: bracket corners around the page symbol. */
@Composable
private fun ViewfinderMark(icon: Painter, modifier: Modifier = Modifier) {
    val bracket = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 2.dp.toPx()
            val arm = 11.dp.toPx()
            val stroke = 2.dp.toPx()
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(bracket, Offset(x, y), Offset(x + dx * arm, y), stroke, StrokeCap.Round)
                drawLine(bracket, Offset(x, y), Offset(x, y + dy * arm), stroke, StrokeCap.Round)
            }
            corner(inset, inset, 1f, 1f)
            corner(size.width - inset, inset, -1f, 1f)
            corner(inset, size.height - inset, 1f, -1f)
            corner(size.width - inset, size.height - inset, -1f, -1f)
        }
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun FuyaoPageColumn(modifier: Modifier = Modifier, topInset: Dp = LocalPaneTopInset.current,
    content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        FuyaoScrollEdge(Modifier.widthIn(max = FuyaoLayout.readable).fillMaxSize(), topInset,
            scrollOffset = { scroll.value.toFloat() }) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(start = FuyaoSpacing.content, end = FuyaoSpacing.content,
                    top = topInset + FuyaoSpacing.content, bottom = bottomInset + FuyaoSpacing.content),
                verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.content), content = content)
        }
    }
}

@Composable
fun FuyaoPageList(modifier: Modifier = Modifier, topInset: Dp = LocalPaneTopInset.current, content: LazyListScope.() -> Unit) {
    val scroll = rememberLazyListState()
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    FuyaoScrollEdge(modifier, topInset, scrollOffset = {
        if (scroll.firstVisibleItemIndex > 0) Float.MAX_VALUE else scroll.firstVisibleItemScrollOffset.toFloat()
    }) {
        LazyColumn(Modifier.fillMaxSize(), state = scroll,
            contentPadding = PaddingValues(start = FuyaoSpacing.content, end = FuyaoSpacing.content,
                top = topInset + FuyaoSpacing.content, bottom = bottomInset + FuyaoSpacing.content),
            verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.content), content = content)
    }
}

/**
 * Photo workspace page: the preview component stays pinned at the top and carries its own
 * backdrop, [bar] floats over the list start, and list rows scroll beneath that bar through the
 * progressive scroll edge without ever passing the photo.
 */
@Composable
fun FixedPhotoPreviewListPage(
    modifier: Modifier = Modifier,
    topInset: Dp = LocalPaneTopInset.current,
    preview: @Composable () -> Unit,
    bar: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    var barHeight by remember { mutableIntStateOf(0) }
    val barSpacing = FuyaoSpacing.content
    val barArea = with(density) { barHeight.toDp() + barSpacing }
    val barAreaPx = with(density) { barArea.toPx() }
    val scroll = rememberLazyListState()
    Column(modifier) {
        Column(Modifier.padding(top = topInset + FuyaoSpacing.content,
            start = FuyaoSpacing.content, end = FuyaoSpacing.content)) {
            preview()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            FuyaoScrollEdge(Modifier.fillMaxSize(), topInset = barArea, scrollOffset = {
                // Padding is part of the first item's offset, so normalize by the pinned bar area:
                // the blur grows as rows rise into the bar, not only after they pass it.
                if (scroll.firstVisibleItemIndex > 0) Float.MAX_VALUE
                else (scroll.firstVisibleItemScrollOffset + barAreaPx).toFloat().coerceAtLeast(0f)
            }) {
                LazyColumn(Modifier.fillMaxSize(), state = scroll,
                    contentPadding = PaddingValues(start = FuyaoSpacing.content, end = FuyaoSpacing.content,
                        top = barArea, bottom = bottomInset + FuyaoSpacing.content),
                    verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.content), content = content)
            }
            if (bar != null) {
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(horizontal = FuyaoSpacing.content)
                    .onSizeChanged { barHeight = it.height }) {
                    bar()
                }
            }
        }
    }
}
