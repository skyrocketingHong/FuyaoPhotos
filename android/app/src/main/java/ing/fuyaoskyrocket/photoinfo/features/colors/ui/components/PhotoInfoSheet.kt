package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.PhotoColorInfo
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoLayout
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoPageColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoInfoSheet(info: PhotoColorInfo, hdrDisplayEnabled: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = FuyaoLayout.readable) {
        FuyaoPageColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), topInset = 0.dp) {
            Text(stringResource(R.string.cp_photo_info_sheet_title), style = MaterialTheme.typography.headlineSmall)
            PhotoColorInfoContent(info, hdrDisplayEnabled)
        }
    }
}

@Composable
private fun PhotoColorInfoContent(info: PhotoColorInfo, hdrDisplayEnabled: Boolean) {
    val gamut = stringResource(when {
        info.isWideGamut -> R.string.cp_photo_gamut_wide
        info.isSrgb -> R.string.cp_photo_gamut_srgb
        else -> R.string.cp_photo_gamut_custom
    })
    val hdr = stringResource(if (info.hasGainmap) R.string.cp_photo_hdr_ultra_hdr else R.string.cp_photo_hdr_none)
    val display = stringResource(when {
        info.hasGainmap && hdrDisplayEnabled -> R.string.cp_photo_display_hdr
        info.isWideGamut -> R.string.cp_photo_display_wide_gamut
        else -> R.string.cp_photo_display_sdr
    })
    ColorValuePanel(buildList {
        add(ColorValueItem(stringResource(R.string.cp_photo_resolution), "${info.width} × ${info.height}"))
        add(ColorValueItem(stringResource(R.string.cp_photo_pixel_format), info.bitmapConfig))
        add(ColorValueItem(stringResource(R.string.cp_photo_color_space), "${info.colorSpaceName} · ${info.colorModel}"))
        add(ColorValueItem(stringResource(R.string.cp_photo_gamut), gamut))
        add(ColorValueItem(stringResource(R.string.cp_photo_component_range), info.componentRange))
        add(ColorValueItem(stringResource(R.string.cp_photo_hdr), hdr))
        add(ColorValueItem(stringResource(R.string.cp_photo_white_point), info.whitePoint))
        add(ColorValueItem(stringResource(R.string.cp_photo_display_mode), display))
        info.primaries?.let { add(ColorValueItem(stringResource(R.string.cp_photo_primaries), it, fullWidth = true)) }
        info.gainmap?.let { gainmap ->
            add(ColorValueItem(stringResource(R.string.cp_photo_gainmap), stringResource(R.string.cp_photo_gainmap_value,
                gainmap.ratioMin, gainmap.ratioMax, gainmap.gamma, gainmap.hdrTransitionRatio, gainmap.fullHdrRatio),
                fullWidth = true))
        }
    })
}
