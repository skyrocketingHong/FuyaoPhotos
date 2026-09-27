package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.PhotoColorInfo
import ing.fuyaoskyrocket.photoinfo.features.colors.ui.theme.FuyaoSpacing

/**
 * Modal bottom sheet showing detailed photo color information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoInfoSheet(
    info: PhotoColorInfo,
    hdrDisplayEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        sheetMaxWidth = 640.dp,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = FuyaoSpacing.large),
            verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.compact),
        ) {
            item(contentType = "title") {
                Text(
                    text = stringResource(R.string.cp_photo_info_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            item(contentType = "photo_parameters") {
                PhotoColorInfoContent(
                    info = info,
                    hdrDisplayEnabled = hdrDisplayEnabled,
                )
            }
        }
    }
}

@Composable
private fun PhotoColorInfoContent(
    info: PhotoColorInfo,
    hdrDisplayEnabled: Boolean,
) {
    val gamutDescription = when {
        info.isWideGamut -> stringResource(R.string.cp_photo_gamut_wide)
        info.isSrgb -> stringResource(R.string.cp_photo_gamut_srgb)
        else -> stringResource(R.string.cp_photo_gamut_custom)
    }
    val hdrDescription = if (info.hasGainmap) {
        stringResource(R.string.cp_photo_hdr_ultra_hdr)
    } else {
        stringResource(R.string.cp_photo_hdr_none)
    }
    val displayMode = when {
        info.hasGainmap && hdrDisplayEnabled -> stringResource(R.string.cp_photo_display_hdr)
        info.isWideGamut -> stringResource(R.string.cp_photo_display_wide_gamut)
        else -> stringResource(R.string.cp_photo_display_sdr)
    }
    Column(verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.small)) {
        PhotoInfoRow(
            leftLabel = stringResource(R.string.cp_photo_resolution),
            leftValue = "${info.width} × ${info.height}",
            rightLabel = stringResource(R.string.cp_photo_pixel_format),
            rightValue = info.bitmapConfig,
        )
        PhotoInfoRow(
            leftLabel = stringResource(R.string.cp_photo_color_space),
            leftValue = "${info.colorSpaceName} · ${info.colorModel}",
            rightLabel = stringResource(R.string.cp_photo_gamut),
            rightValue = gamutDescription,
        )
        PhotoInfoRow(
            leftLabel = stringResource(R.string.cp_photo_component_range),
            leftValue = info.componentRange,
            rightLabel = stringResource(R.string.cp_photo_hdr),
            rightValue = hdrDescription,
        )
        PhotoInfoRow(
            leftLabel = stringResource(R.string.cp_photo_white_point),
            leftValue = info.whitePoint ?: "—",
            rightLabel = stringResource(R.string.cp_photo_display_mode),
            rightValue = displayMode,
        )
        if (info.primaries != null) {
            PhotoInfoValue(
                label = stringResource(R.string.cp_photo_primaries),
                value = info.primaries,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        info.gainmap?.let { gainmap ->
            PhotoInfoValue(
                label = stringResource(R.string.cp_photo_gainmap),
                value = stringResource(
                    R.string.cp_photo_gainmap_value,
                    gainmap.ratioMin,
                    gainmap.ratioMax,
                    gainmap.gamma,
                    gainmap.hdrTransitionRatio,
                    gainmap.fullHdrRatio,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PhotoInfoRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 360.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.small)) {
                PhotoInfoValue(
                    label = leftLabel,
                    value = leftValue,
                    modifier = Modifier.fillMaxWidth(),
                )
                PhotoInfoValue(
                    label = rightLabel,
                    value = rightValue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FuyaoSpacing.small),
            ) {
                PhotoInfoValue(
                    label = leftLabel,
                    value = leftValue,
                    modifier = Modifier.weight(1f),
                )
                PhotoInfoValue(
                    label = rightLabel,
                    value = rightValue,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PhotoInfoValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(FuyaoSpacing.compact)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
