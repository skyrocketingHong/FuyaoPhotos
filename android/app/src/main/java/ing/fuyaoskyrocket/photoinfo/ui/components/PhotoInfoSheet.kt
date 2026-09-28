package ing.fuyaoskyrocket.photoinfo.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.metadata.MetadataFormatting
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoDetails
import ing.fuyaoskyrocket.photoinfo.presentation.OriginalPhoto
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FixedPhotoPreviewListPage
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoPageList
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.SectionHeading
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.LocalPaneTopInset
import androidx.compose.ui.unit.Dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private data class DetailRow(@StringRes val label: Int, val value: String)
private data class DetailGroup(@StringRes val title: Int, val rows: List<DetailRow>,
    @StringRes val description: Int? = null)

enum class PhotoInfoDisplay { ALL, SUMMARY, FACTS }

@Composable
fun PhotoInfoContent(photo: OriginalPhoto, controls: OriginalPreviewState, hdrAvailable: Boolean,
    busy: Boolean, modifier: Modifier = Modifier, topInset: Dp = LocalPaneTopInset.current,
    display: PhotoInfoDisplay = PhotoInfoDisplay.ALL, header: (@Composable () -> Unit)? = null,
    afterSummary: (@Composable () -> Unit)? = null, belowBar: (@Composable () -> Unit)? = null) {
    val details = photo.details
    val context = LocalContext.current
    val groups = detailGroups(context, details)
    val kind = details.mimeType?.let { imageKind(context, it) }
    val size = details.byteCount?.let { android.text.format.Formatter.formatFileSize(context, it) }
    val subtitle = listOfNotNull(kind, size).joinToString(" · ")
    key(photo.id) {
    if (display == PhotoInfoDisplay.FACTS) {
        FuyaoPageList(modifier, topInset) {
            if (header != null) item("page-intro") { header() }
            detailGroups(groups)
        }
    } else {
        // The preview component is pinned and owns its backdrop; detail rows scroll beneath the
        // pinned bar through the progressive edge and never pass the photo.
        FixedPhotoPreviewListPage(modifier, topInset,
            preview = { OriginalPhotoSummary(photo, controls, subtitle, hdrAvailable, busy) },
            bar = afterSummary) {
            if (header != null) item("page-intro") { header() }
            if (belowBar != null) item("below-bar") { belowBar() }
            if (display != PhotoInfoDisplay.SUMMARY) detailGroups(groups)
        }
    }
    }
}

private fun LazyListScope.detailGroups(groups: List<DetailGroup>) {
    groups.forEach { group ->
        item(group.title) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading(stringResource(group.title), group.description?.let { stringResource(it) },
                    Modifier.padding(horizontal = FuyaoSpacing.cardInset))
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(horizontal = FuyaoSpacing.cardInset, vertical = 8.dp)) {
                        group.rows.forEachIndexed { index, row -> DetailValueRow(row, index < group.rows.lastIndex) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailValueRow(row: DetailRow, divider: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                stringResource(row.label),
                Modifier.weight(.4f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                row.value,
                Modifier.weight(.6f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
            )
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
    }
}

private fun detailGroups(context: Context, details: PhotoDetails): List<DetailGroup> {
    val exif = details.exif
    fun raw(tag: String) = exif[tag]?.trim()?.takeIf(String::isNotEmpty)
    fun number(tag: String, places: Int = 2): String? = raw(tag)?.let { parseExifNumber(it) }
        ?.let { MetadataFormatting.number(it, places) }?.takeIf(String::isNotEmpty)
    val file = buildList {
        details.mimeType?.takeIf(String::isNotBlank)?.let { add(DetailRow(R.string.photo_info_kind, imageKind(context, it))) }
        details.byteCount?.takeIf { it > 0L }?.let {
            add(DetailRow(R.string.photo_info_file_size, android.text.format.Formatter.formatFileSize(context, it)))
        }
        if (details.width > 0 && details.height > 0) add(DetailRow(R.string.photo_info_dimensions,
            context.getString(R.string.photo_info_dimension_value, details.width, details.height)))
        val xResolution = number(ExifInterface.TAG_X_RESOLUTION)
        val yResolution = number(ExifInterface.TAG_Y_RESOLUTION)
        if (xResolution != null && yResolution != null) {
            val unit = when (raw(ExifInterface.TAG_RESOLUTION_UNIT)?.toIntOrNull()) {
                2 -> context.getString(R.string.photo_info_dpi)
                3 -> context.getString(R.string.photo_info_dpcm)
                else -> null
            }
            val value = context.getString(R.string.photo_info_resolution_value, xResolution, yResolution)
            add(DetailRow(R.string.photo_info_resolution, if (unit == null) value else "$value $unit"))
        }
        details.colorSpace?.takeIf(String::isNotBlank)?.let { add(DetailRow(R.string.photo_info_color_space, it)) }
        raw(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { add(DetailRow(R.string.photo_info_captured, formatExifDate(it))) }
        raw(ExifInterface.TAG_DATETIME)?.let { add(DetailRow(R.string.photo_info_image_date, formatExifDate(it))) }
    }
    val camera = buildList {
        raw(ExifInterface.TAG_MAKE)?.let { add(DetailRow(R.string.photo_info_make, it)) }
        raw(ExifInterface.TAG_MODEL)?.let { add(DetailRow(R.string.photo_info_model, it)) }
        raw(ExifInterface.TAG_LENS_MAKE)?.let { add(DetailRow(R.string.photo_info_lens_make, it)) }
        raw(ExifInterface.TAG_LENS_MODEL)?.let { add(DetailRow(R.string.photo_info_lens_model, it)) }
        raw(ExifInterface.TAG_ARTIST)?.let { add(DetailRow(R.string.photo_info_creator, it)) }
    }
    val capture = buildList {
        number(ExifInterface.TAG_APERTURE_VALUE, 4)?.let { add(DetailRow(R.string.photo_info_aperture_value, it)) }
        number(ExifInterface.TAG_F_NUMBER)?.let { add(DetailRow(R.string.photo_info_f_number, "f/$it")) }
        raw(ExifInterface.TAG_EXPOSURE_TIME)?.let { parseExifNumber(it) }?.let { MetadataFormatting.exposure(it) }
            ?.takeIf(String::isNotEmpty)?.let { add(DetailRow(R.string.photo_info_exposure, it)) }
        number(ExifInterface.TAG_FOCAL_LENGTH)?.let { add(DetailRow(R.string.photo_info_focal_length,
            context.getString(R.string.photo_info_mm, it))) }
        number(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.let { add(DetailRow(R.string.photo_info_focal_35,
            context.getString(R.string.photo_info_mm, it))) }
        raw(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { add(DetailRow(R.string.photo_info_iso, it)) }
        raw(ExifInterface.TAG_EXPOSURE_PROGRAM)?.toIntOrNull()?.let { value ->
            val label = when (value) {
                1 -> R.string.photo_info_program_manual
                2 -> R.string.photo_info_program_normal
                3 -> R.string.photo_info_program_aperture
                4 -> R.string.photo_info_program_shutter
                5 -> R.string.photo_info_program_creative
                6 -> R.string.photo_info_program_action
                7 -> R.string.photo_info_program_portrait
                8 -> R.string.photo_info_program_landscape
                else -> null
            }
            add(DetailRow(R.string.photo_info_exposure_program, label?.let { context.getString(it) } ?: value.toString()))
        }
        raw(ExifInterface.TAG_METERING_MODE)?.toIntOrNull()?.let { value ->
            val label = when (value) {
                1 -> R.string.photo_info_meter_average
                2 -> R.string.photo_info_meter_center
                3 -> R.string.photo_info_meter_spot
                4 -> R.string.photo_info_meter_multispot
                5 -> R.string.photo_info_meter_pattern
                6 -> R.string.photo_info_meter_partial
                else -> null
            }
            add(DetailRow(R.string.photo_info_metering, label?.let { context.getString(it) } ?: value.toString()))
        }
        raw(ExifInterface.TAG_WHITE_BALANCE)?.toIntOrNull()?.let { value ->
            val label = when (value) {
                0 -> R.string.photo_info_auto
                1 -> R.string.photo_info_manual
                else -> null
            }
            add(DetailRow(R.string.photo_info_white_balance, label?.let { context.getString(it) } ?: value.toString()))
        }
        raw(ExifInterface.TAG_FLASH)?.toIntOrNull()?.let { value ->
            add(DetailRow(R.string.photo_info_flash, context.getString(
                if (value and 1 == 1) R.string.photo_info_flash_fired else R.string.photo_info_flash_not_fired)))
        }
    }
    val location = buildList {
        details.coordinates?.let { point ->
            add(DetailRow(R.string.photo_info_latitude, degreeText(point.latitude, true)))
            add(DetailRow(R.string.photo_info_longitude, degreeText(point.longitude, false)))
        }
    }
    val report = details.report?.sections?.map { section ->
        DetailGroup(section.title, section.rows.map { row ->
            DetailRow(row.label, row.text ?: row.value?.let(context::getString).orEmpty())
        })
    }.orEmpty()
    return (listOf(
        DetailGroup(R.string.photo_info_file, file, R.string.photo_info_file_description),
        DetailGroup(R.string.photo_info_camera, camera, R.string.photo_info_camera_description),
        DetailGroup(R.string.photo_info_capture, capture, R.string.photo_info_capture_description),
        DetailGroup(R.string.photo_info_location, location, R.string.photo_info_location_description),
    ) + report).filter { it.rows.isNotEmpty() }
}

private fun imageKind(context: Context, mime: String): String {
    val format = when (mime.lowercase(Locale.ROOT)) {
        "image/jpeg" -> "JPEG"
        "image/heic", "image/heif" -> "HEIF"
        "image/avif" -> "AVIF"
        "image/png" -> "PNG"
        else -> return mime
    }
    return context.getString(R.string.photo_info_image_kind, format)
}

/** The import-time reason this photo cannot preserve its original data, shown where the badge points. */
@Composable
internal fun exportBlockingNotice(state: EditorState): String? {
    if (state.photos.isEmpty()) return null
    if (state.previewError != null) return state.previewError
    if (!state.preservationBlocked) return null
    return state.mediaMessage?.let { stringResource(it) } ?: stringResource(R.string.media_unsupported)
}

private fun parseExifNumber(value: String): Double? {
    val parts = value.split('/')
    val number = if (parts.size == 2) {
        val denominator = parts[1].toDoubleOrNull() ?: return null
        if (denominator == 0.0) return null
        (parts[0].toDoubleOrNull() ?: return null) / denominator
    } else value.toDoubleOrNull() ?: return null
    return number.takeIf { it.isFinite() }
}

private fun formatExifDate(raw: String): String = runCatching {
    LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss"))
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
}.getOrDefault(raw)

private fun degreeText(value: Double, latitude: Boolean): String {
    val hundredths = (abs(value) * 360_000.0).roundToLong()
    val degrees = hundredths / 360_000
    val minutes = (hundredths % 360_000) / 6_000
    val seconds = hundredths % 6_000
    val direction = if (latitude) { if (value < 0) "S" else "N" } else { if (value < 0) "W" else "E" }
    return String.format(Locale.ROOT, "%d° %d′ %02d.%02d″ %s", degrees, minutes, seconds / 100, seconds % 100, direction)
}
