package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.PhotoColorInfo
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor
import ing.fuyaoskyrocket.photoinfo.features.colors.ui.theme.FuyaoDimensions
import ing.fuyaoskyrocket.photoinfo.features.colors.ui.theme.FuyaoSpacing

/** Material 3 supporting panel for the magnifier, image controls, and color values. */
@Composable
internal fun ColorResultPanel(
    bitmap: Bitmap?,
    sampledColor: SampledColor?,
    photoColorInfo: PhotoColorInfo?,
    hdrDisplayEnabled: Boolean,
    onShowPhotoInfo: () -> Unit,
    onHdrDisplayEnabledChange: (Boolean) -> Unit,
    expandColorValues: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val canToggleHdr = photoColorInfo?.hasHdrContent == true
    val hdrStateDescription = when {
        !canToggleHdr -> stringResource(R.string.cp_hdr_action_unavailable)
        hdrDisplayEnabled -> stringResource(R.string.cp_hdr_action_on)
        else -> stringResource(R.string.cp_hdr_action_off)
    }

    val cardShape = if (expandColorValues) {
        MaterialTheme.shapes.large.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        )
    } else {
        MaterialTheme.shapes.large
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = cardShape,
    ) {
        Column(
            modifier = if (expandColorValues) {
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = FuyaoSpacing.compact,
                        top = FuyaoSpacing.compact,
                        end = FuyaoSpacing.compact,
                    )
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(FuyaoSpacing.compact)
            },
        ) {
            ResultHeader(
                bitmap = bitmap,
                sampledColor = sampledColor,
                photoColorInfo = photoColorInfo,
                canToggleHdr = canToggleHdr,
                hdrDisplayEnabled = hdrDisplayEnabled,
                hdrStateDescription = hdrStateDescription,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                onShowPhotoInfo = onShowPhotoInfo,
                onHdrDisplayEnabledChange = onHdrDisplayEnabledChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(FuyaoSpacing.extraSmall))
            ColorValueContent(
                selectedTabIndex = selectedTabIndex,
                sampledColor = sampledColor,
                modifier = if (expandColorValues) Modifier.weight(1f) else Modifier,
            )
        }
    }
}

@Composable
private fun ResultHeader(
    bitmap: Bitmap?,
    sampledColor: SampledColor?,
    photoColorInfo: PhotoColorInfo?,
    canToggleHdr: Boolean,
    hdrDisplayEnabled: Boolean,
    hdrStateDescription: String,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onShowPhotoInfo: () -> Unit,
    onHdrDisplayEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < 280.dp
        val magnifierSize = if (compact) {
            FuyaoDimensions.compactMagnifierSize
        } else {
            FuyaoDimensions.magnifierSize
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(magnifierSize, 100.dp)),
                horizontalArrangement = Arrangement.spacedBy(
                    if (compact) FuyaoSpacing.small else FuyaoSpacing.compact,
                ),
                verticalAlignment = Alignment.Top,
            ) {
                SamplingMagnifier(
                    bitmap = bitmap,
                    sampledColor = sampledColor,
                    contentDescription = stringResource(R.string.cp_sampling_magnifier_description),
                    modifier = Modifier.size(magnifierSize),
                )
                SampleCoordinatesAndActions(
                    sampledColor = sampledColor,
                    photoColorInfo = photoColorInfo,
                    canToggleHdr = canToggleHdr,
                    hdrDisplayEnabled = hdrDisplayEnabled,
                    hdrStateDescription = hdrStateDescription,
                    onShowPhotoInfo = onShowPhotoInfo,
                    onHdrDisplayEnabledChange = onHdrDisplayEnabledChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
            ColorValueTabRow(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onTabSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SampleCoordinatesAndActions(
    sampledColor: SampledColor?,
    photoColorInfo: PhotoColorInfo?,
    canToggleHdr: Boolean,
    hdrDisplayEnabled: Boolean,
    hdrStateDescription: String,
    onShowPhotoInfo: () -> Unit,
    onHdrDisplayEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SampleInfoLine(
                label = stringResource(R.string.cp_sample_coordinates_label),
                value = sampledColor?.let {
                    stringResource(R.string.cp_sample_coordinates, it.sourceX, it.sourceY)
                } ?: stringResource(R.string.cp_sample_coordinates_empty),
            )
            if (photoColorInfo != null) {
                val positionText = sampledColor?.let {
                    val xPercent = if (photoColorInfo.width > 1) {
                        it.sourceX * 100.0 / (photoColorInfo.width - 1)
                    } else {
                        0.0
                    }
                    val yPercent = if (photoColorInfo.height > 1) {
                        it.sourceY * 100.0 / (photoColorInfo.height - 1)
                    } else {
                        0.0
                    }
                    stringResource(R.string.cp_sample_relative_position, xPercent, yPercent)
                } ?: stringResource(R.string.cp_sample_relative_position_empty)
                SampleInfoLine(
                    label = stringResource(R.string.cp_sample_relative_position_label),
                    value = positionText,
                )
                SampleInfoLine(
                    label = stringResource(R.string.cp_sample_image_summary_label),
                    value = stringResource(
                        R.string.cp_sample_image_summary,
                        photoColorInfo.width,
                        photoColorInfo.height,
                        sampledColor?.sourceColorSpaceName ?: photoColorInfo.colorSpaceName,
                    ),
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ResultIconAction(
                iconResource = R.drawable.cp_ic_info_square,
                contentDescription = stringResource(R.string.cp_photo_info_action),
                enabled = photoColorInfo != null,
                selected = false,
                onClick = onShowPhotoInfo,
            )
            ResultIconAction(
                iconResource = R.drawable.cp_ic_hdr_viewfinder_rectangular,
                contentDescription = stringResource(R.string.cp_photo_hdr),
                stateDescription = hdrStateDescription,
                enabled = canToggleHdr,
                selected = canToggleHdr && hdrDisplayEnabled,
                onClick = { onHdrDisplayEnabledChange(!hdrDisplayEnabled) },
            )
        }
    }
}

@Composable
private fun SampleInfoLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FuyaoSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ResultIconAction(
    @DrawableRes iconResource: Int,
    contentDescription: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
) {
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
            contentColor = iconColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = iconColor.copy(alpha = 0.38f),
        ),
        modifier = modifier
            .size(FuyaoDimensions.resultActionButtonSize)
            .semantics {
                stateDescription?.let { this.stateDescription = it }
            },
    ) {
        Icon(
            painter = painterResource(iconResource),
            contentDescription = contentDescription,
            modifier = Modifier.size(FuyaoDimensions.resultActionIconSize),
        )
    }
}
