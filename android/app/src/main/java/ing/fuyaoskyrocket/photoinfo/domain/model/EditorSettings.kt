package ing.fuyaoskyrocket.photoinfo.domain.model

data class EditorSettings(
    val defaultAuthor: String = "",
    val resolvePhotoLocation: Boolean = true,
    val fallbackMainFocal: String = "",
    val lenses: List<ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile> = emptyList(),
    val exportDefaults: ExportOptions = ExportOptions(),
    val hevcEncoder: ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind = ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.X265,
    val metadataSharesCards: Boolean = false,
    val workspace: WorkspaceSettings = WorkspaceSettings(
        sharing = if (metadataSharesCards) PhotoSharing.PARTIAL else PhotoSharing.INDEPENDENT),
    val preferLensPixelCount: Boolean = false,
) {
    val mainFocalMm: Double? get() = fallbackMainFocal.toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..200.0 }
    val validFocal: Boolean get() = fallbackMainFocal.isBlank() || mainFocalMm != null
    fun authorFor(exifAuthor: String): String = exifAuthor.ifBlank { defaultAuthor.trim() }
    fun imageSizeFor(photoValue: String, lensMegapixels: Double?): String =
        lensMegapixels?.takeIf { preferLensPixelCount && it.isFinite() && it > 0 && it <= 1000 }
            ?.let { ing.fuyaoskyrocket.photoinfo.domain.metadata.MetadataFormatting.number(it, 2) + "MP" } ?: photoValue

    fun initialCardInfo(photo: PhotoInfo, lensMegapixels: Double?): PhotoInfo = photo
        .with(FieldId.AUTHOR, authorFor(photo[FieldId.AUTHOR]))
        .with(FieldId.IMAGE_SIZE, imageSizeFor(photo[FieldId.IMAGE_SIZE], lensMegapixels))
}
