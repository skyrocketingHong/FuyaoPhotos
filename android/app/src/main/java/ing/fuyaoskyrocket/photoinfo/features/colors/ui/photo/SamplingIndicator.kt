package ing.fuyaoskyrocket.photoinfo.features.colors.ui.photo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor

@Composable
internal fun SamplingIndicator(sampledColor: SampledColor, layout: ImageLayout) {
    Canvas(Modifier.fillMaxSize()) {
        val point = bitmapToViewport(Offset(sampledColor.sourceX + 0.5f, sampledColor.sourceY + 0.5f), layout)
        drawCircle(Color.Black.copy(alpha = 0.7f), 7.dp.toPx(), point, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 7.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
    }
}
