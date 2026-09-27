package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            Icon(painterResource(icon), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
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
