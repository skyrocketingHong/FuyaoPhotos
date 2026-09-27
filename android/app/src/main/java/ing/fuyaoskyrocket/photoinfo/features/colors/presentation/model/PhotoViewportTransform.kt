package ing.fuyaoskyrocket.photoinfo.features.colors.presentation.model

import androidx.compose.ui.geometry.Offset

/**
 * Atomic viewport transform: zoom and pan are always committed together so that
 * image matrix, sampling coordinates, and indicator drawing share one state.
 *
 * Lives in `presentation/model` so that [ing.fuyaoskyrocket.photoinfo.features.colors.presentation.ColorPickerViewModel]
 * can own it without depending on the `ui` layer.
 */
internal data class PhotoViewportTransform(
    val zoom: Float = 1f,
    val pan: Offset = Offset.Zero,
)
