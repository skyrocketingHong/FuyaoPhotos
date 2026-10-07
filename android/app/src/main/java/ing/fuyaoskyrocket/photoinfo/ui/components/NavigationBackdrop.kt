package ing.fuyaoskyrocket.photoinfo.ui.components

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

val LocalNavigationBackdrop = staticCompositionLocalOf<NavigationBackdrop?> { null }

/** Records only explicitly registered SDR backgrounds and controls, never the HDR viewport. */
@Stable
class NavigationBackdrop internal constructor(val enabled: Boolean, surface: Color) : Backdrop {
    internal var surface by mutableStateOf(surface)
    internal data class Source(val priority: Int, val order: Long)
    internal val sources = mutableStateMapOf<LayerBackdrop, Source>()
    internal var nextOrder = 0L
    override val isCoordinatesDependent = true

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        drawRect(surface)
        sources.entries.sortedWith(compareBy({ it.value.priority }, { it.value.order })).forEach { (source, _) ->
            with(source) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }
}

@Composable
fun rememberNavigationBackdrop(enabled: Boolean = true): NavigationBackdrop {
    val available = enabled && Build.VERSION.SDK_INT >= 31
    val surface = MaterialTheme.colorScheme.surface
    val backdrop = remember(available) { NavigationBackdrop(available, surface) }
    SideEffect { backdrop.surface = surface }
    return backdrop
}

@Composable
fun Modifier.navigationBackdropSource(priority: Int = 1): Modifier {
    val owner = LocalNavigationBackdrop.current ?: return this
    if (!owner.enabled) return this
    val layer = rememberLayerBackdrop()
    DisposableEffect(owner, layer, priority) {
        owner.sources[layer] = NavigationBackdrop.Source(priority, owner.nextOrder++)
        onDispose { owner.sources.remove(layer) }
    }
    return layerBackdrop(layer)
}
