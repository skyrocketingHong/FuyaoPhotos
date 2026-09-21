package ing.fuyaoskyrocket.photoinfo.presentation

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.data.export.PhotoExporter
import ing.fuyaoskyrocket.photoinfo.data.location.PhotoGeocoder
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoRepository
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoSource
import ing.fuyaoskyrocket.photoinfo.data.settings.SettingsRepository
import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoEditSnapshot
import ing.fuyaoskyrocket.photoinfo.domain.session.EditChanges
import ing.fuyaoskyrocket.photoinfo.platform.CardRenderer
import ing.fuyaoskyrocket.photoinfo.platform.FontRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class SessionPhoto(val source: PhotoSource, val info: PhotoInfo, val style: CardStyle,
    val resolvedLocation: String = "", val locationEdited: Boolean = false) {
    fun snapshot() = PhotoEditSnapshot.capture(source.file.absolutePath, source.info, info, style, resolvedLocation, locationEdited)
}

class EditorViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val photos = PhotoRepository(application)
    private val fonts = FontRepository(application)
    private val renderer = CardRenderer()
    private val fullSizeMutex=Mutex()
    private val exporter = PhotoExporter(application, photos)
    private val settingsRepository = SettingsRepository(application)
    private val geocoder = PhotoGeocoder(application)
    private var drafts = emptyList<SessionPhoto>()
    private var baselines = saved.get<ArrayList<String>>("editBaselines").orEmpty().chunked(2)
        .filter { it.size == 2 }.associate { it[0] to it[1] }
    private var source: PhotoSource? = null
    private var resolvedLocation = ""
    private var locationEdited = false
    private var locationRevision = 0L
    private var noticeId = 0L
    private var locationJob: Job? = null
    private var renderJob: Job? = null
    private var workJob: Job? = null
    private val initialSettings = settingsRepository.read()
    var state by mutableStateOf(EditorState(style = readStyle(), fontName = fonts.displayName,
        hasCustomFont = fonts.hasCustomFont, keepCaptureMetadata = saved["keepMetadata"] ?: initialSettings.exportDefaults.keepExif,
        settings = initialSettings, jpegQuality = saved["jpegQuality"] ?: initialSettings.exportDefaults.jpegQuality,
        keepLocation = saved["keepLocation"] ?: initialSettings.exportDefaults.keepLocation,
        keepCaptureTime = saved["keepCaptureTime"] ?: initialSettings.exportDefaults.keepCaptureTime)); private set

    init {
        val session = saved.get<ArrayList<String>>("session")
        val legacy = saved.get<String>("source")
        if (session != null || legacy != null) {
            state = state.copy(busy = true, importing = true)
            workJob = viewModelScope.launch {
                try {
                    drafts = withContext(Dispatchers.IO) {
                        if (session != null) {
                            require(session.size % PhotoEditSnapshot.FIELD_COUNT == 0 && session.size <= PhotoEditSnapshot.FIELD_COUNT * PhotoEditSnapshot.MAX_PHOTOS)
                            session.chunked(PhotoEditSnapshot.FIELD_COUNT).map { fields ->
                                ensureActive()
                                val edits = PhotoEditSnapshot.restore(fields)
                                val photo = photos.restore(edits.path)
                                SessionPhoto(photo, edits.info(photo.info), edits.style, edits.resolvedLocation, edits.locationEdited)
                            }
                        } else {
                            val photo = photos.restore(requireNotNull(legacy))
                            val values = FieldId.entries.associateWith { saved.get<String>("field.${it.name}") ?: photo.info[it] }
                            listOf(SessionPhoto(photo, PhotoInfo(values), state.style,
                                saved["resolvedLocation"] ?: "", saved["locationEdited"] ?: false))
                        }
                    }
                    // Older sessions had no baseline. Preserve real overrides while allowing an
                    // unchanged default photo to return without a spurious confirmation.
                    baselines = drafts.associate { draft ->
                        val initial = draft.copy(info=draft.source.info.with(FieldId.AUTHOR, state.settings.authorFor(draft.source.info[FieldId.AUTHOR])),
                            style=CardStyle(), resolvedLocation="", locationEdited=false)
                        draft.source.file.name to (baselines[draft.source.file.name]
                            ?: EditChanges.fingerprint(initial.snapshot(), PhotoExporter.DEFAULT_JPEG_QUALITY, true, fonts.selectionKey))
                    }
                    publishCollection()
                    loadSelected((saved.get<Int>("photoIndex") ?: 0).coerceIn(0, drafts.lastIndex))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    forgetSession()
                    drafts = emptyList()
                    state = state.copy(busy = false, importing = false, error = errorMessage(failure))
                } catch (failure: OutOfMemoryError) {
                    state = state.copy(busy = false, importing = false, error = errorMessage(failure))
                }
            }
        }
    }

    fun importPhoto(uri: Uri) = importPhotos(listOf(uri))

    fun importPhotos(uris: List<Uri>) {
        if (state.busy || uris.isEmpty()) return
        val selected = uris.distinct()
        if (selected.size > PhotoEditSnapshot.MAX_PHOTOS) {
            state = state.copy(errorTitle = R.string.error_import_title, error = app.getString(R.string.batch_limit, PhotoEditSnapshot.MAX_PHOTOS)); return
        }
        renderJob?.cancel(); cancelLocation()
        val style = state.style
        val settings = state.settings
        state = state.copy(busy = true, importing = true, error = null, errorTitle = R.string.error_import_title, rendering = false, notice = null, exported = null)
        workJob = viewModelScope.launch {
            val imported = mutableListOf<SessionPhoto>()
            val failures = mutableListOf<String>()
            try {
                selected.forEachIndexed { index, uri ->
                    ensureActive()
                    var pending: PhotoSource? = null
                    try {
                        val photo = withContext(Dispatchers.IO) { photos.import(uri).also { pending = it } }
                        imported += SessionPhoto(photo, photo.info.with(FieldId.AUTHOR, settings.authorFor(photo.info[FieldId.AUTHOR])), style)
                        pending = null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { failures += app.getString(R.string.photo_failure,index+1,errorMessage(failure,PhotoOperation.OPEN)) }
                    finally { pending?.file?.delete() }
                }
                if (imported.isEmpty()) {
                    state = state.copy(busy = false, importing = false, error = failures.joinToString("\n"))
                    renderPreview(); return@launch
                }
                drafts = imported.toList()
                baselines = drafts.associate { it.source.file.name to fingerprint(it) }
                publishCollection()
                // Commit the new private copies before deleting any previous session files.
                saveSnapshots()
                withContext(Dispatchers.IO) { photos.removeOtherDrafts(drafts.map { it.source.file }) }
                loadSelected(0)
                if (failures.isNotEmpty()) state = state.copy(error = failures.joinToString("\n"))
            } catch (cancelled: CancellationException) {
                imported.filter { added -> drafts.none { it.source.file == added.source.file } }.forEach { it.source.file.delete() }
                throw cancelled
            } catch (failure: OutOfMemoryError) {
                imported.filter { added -> drafts.none { it.source.file == added.source.file } }.forEach { it.source.file.delete() }
                state = state.copy(busy = false, importing = false, error = errorMessage(failure))
            }
        }
    }

    private fun publishCollection() {
        state = state.copy(photos = drafts.map { PhotoPageItem(it.source.file.name, it.source.width, it.source.height) },
            photoIndex = 0, sessionId = state.sessionId + 1,
            exportRequiresJpeg = drafts.any { it.source.media.hdrHint || it.source.media.motion != null })
    }

    fun selectPhoto(index: Int) {
        if (index !in drafts.indices || index == state.photoIndex || state.importing || state.exporting || state.closing || (state.busy && !state.loadingPhoto)) return
        persist()
        workJob?.cancel(); renderJob?.cancel(); cancelLocation()
        workJob = viewModelScope.launch { loadSelected(index) }
    }

    private suspend fun loadSelected(index: Int) {
        val draft = drafts[index]
        source = draft.source
        resolvedLocation = draft.resolvedLocation
        locationEdited = draft.locationEdited
        state = state.copy(photoIndex = index, info = draft.info, style = draft.style, original = null, preview = null,
            width = draft.source.width, height = draft.source.height, busy = true, importing = false, loadingPhoto = true,
            rendering = false, previewError = null, hasPhotoGps = draft.source.coordinates != null, locationStatus = LocationStatus.IDLE)
        updateMediaState(draft.source)
        var decoded: Bitmap? = null
        try {
            val bitmap = withContext(Dispatchers.IO) { photos.decode(draft.source, preview = true).also { decoded = it } }
            state = state.copy(original = bitmap, preview = bitmap, busy = false, loadingPhoto = false)
            decoded = null // The visible state now owns the bitmap; never recycle a displayed image.
            persist(); renderPreview()
            if (!locationEdited && draft.info[FieldId.LOCATION].isBlank()) resolveLocation()
            else if (resolvedLocation.isNotBlank()) state = state.copy(locationStatus = LocationStatus.RESOLVED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { state = state.copy(busy = false, loadingPhoto = false, previewError = errorMessage(failure, PhotoOperation.PREVIEW)) }
        catch (failure: OutOfMemoryError) { state = state.copy(busy = false, loadingPhoto = false, previewError = errorMessage(failure, PhotoOperation.PREVIEW)) }
        finally { decoded?.recycle() }
    }

    fun updateField(field: FieldId, value: String, photoId: String? = null) {
        if (state.busy || value.length > 512 || (photoId != null && photoId != source?.file?.name)) return
        state = state.copy(info = state.info.with(field, value))
        if (field == FieldId.LOCATION) { cancelLocation(); locationEdited = true }
        persist(); renderPreview()
    }

    fun resetFields() {
        if (state.busy) return
        cancelLocation(); locationEdited = false
        source?.let { photo ->
            state = state.copy(info = photo.info.with(FieldId.AUTHOR, state.settings.authorFor(photo.info[FieldId.AUTHOR]))
                .with(FieldId.LOCATION, resolvedLocation))
            persist(); renderPreview()
            if (resolvedLocation.isBlank()) resolveLocation()
        }
    }

    fun saveSettings(settings: EditorSettings) {
        if (state.busy || !settings.validFocal || settings.defaultAuthor.length > 512) return
        settingsRepository.save(settings)
        state = state.copy(settings = settingsRepository.read())
        if (!settings.resolvePhotoLocation) { cancelLocation(); state = state.copy(locationStatus = LocationStatus.DISABLED) }
        else if (state.info[FieldId.LOCATION].isBlank() && !locationEdited) resolveLocation()
    }
    fun applyDefaultAuthor() = updateField(FieldId.AUTHOR, state.settings.defaultAuthor)

    private fun cancelLocation() {
        locationRevision++; locationJob?.cancel()
        state = state.copy(locationStatus = LocationStatus.IDLE)
    }
    fun resolveLocation() {
        val photo = source ?: return
        if (state.busy) return
        cancelLocation()
        if (!state.settings.resolvePhotoLocation) { state = state.copy(locationStatus = LocationStatus.DISABLED); return }
        val point = photo.coordinates ?: run { state = state.copy(locationStatus = LocationStatus.NO_GPS); return }
        locationEdited = false
        val revision = locationRevision
        state = state.copy(locationStatus = LocationStatus.RESOLVING)
        locationJob = viewModelScope.launch {
            val place = try { geocoder.resolve(point) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (revision != locationRevision || source?.file != photo.file) return@launch
            if (place == null) { state = state.copy(locationStatus = LocationStatus.UNAVAILABLE); return@launch }
            resolvedLocation = place
            state = state.copy(info = state.info.with(FieldId.LOCATION, place), locationStatus = LocationStatus.RESOLVED)
            persist(); renderPreview()
        }
    }

    fun updateStyle(style: CardStyle, photoId: String? = null) {
        if (state.busy || (photoId != null && photoId != source?.file?.name)) return
        state = state.copy(style = style.sanitized()); persist(); renderPreview()
    }
    fun setJpegQuality(quality: Int) {
        if (state.busy) return
        val value = quality.coerceIn(0, 100)
        saved["jpegQuality"] = value
        state = state.copy(jpegQuality = value)
        refreshChanges()
    }
    fun setKeepMetadata(keep: Boolean) {
        if (state.busy) return
        saved["keepMetadata"] = keep; state = state.copy(keepCaptureMetadata = keep); refreshChanges()
    }
    fun setExportOptions(options: ExportOptions) {
        if (state.busy) return
        val value=options.sanitized()
        saved["jpegQuality"]=value.jpegQuality; saved["keepMetadata"]=value.keepExif
        saved["keepLocation"]=value.keepLocation; saved["keepCaptureTime"]=value.keepCaptureTime
        state=state.copy(jpegQuality=value.jpegQuality,keepCaptureMetadata=value.keepExif,
            keepLocation=value.keepLocation,keepCaptureTime=value.keepCaptureTime)
        refreshChanges()
    }
    fun reportExternalError(message: Int) { state=state.copy(errorTitle=R.string.error_import_title,error=app.getString(message)) }
    fun importFont(uri: Uri) {
        if (state.busy) return
        renderJob?.cancel()
        state = state.copy(busy = true, rendering = false, errorTitle = R.string.error_font_title)
        workJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { fonts.import(uri) }
                state = state.copy(busy = false, fontName = fonts.displayName, hasCustomFont = fonts.hasCustomFont)
                refreshChanges(); renderPreview()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { state = state.copy(busy = false, error = errorMessage(failure, PhotoOperation.FONT)); renderPreview() }
            catch (failure: OutOfMemoryError) { state = state.copy(busy = false, error = errorMessage(failure, PhotoOperation.FONT)); renderPreview() }
        }
    }
    fun resetFont() {
        if (state.busy) return
        fonts.reset(); state = state.copy(fontName = fonts.displayName, hasCustomFont = fonts.hasCustomFont); refreshChanges(); renderPreview()
    }

    /** Full-screen inspection uses original pixels; the editor keeps its inexpensive thumbnail. */
    suspend fun fullResolutionPreview(photoId: String, original: Boolean): Bitmap {
        val photo=source?.takeIf { it.file.name==photoId } ?: throw CancellationException("Photo changed")
        val info=state.info;val style=state.style;val typography=fonts.typography
        var pending: Bitmap?=null
        try {
            val result=fullSizeMutex.withLock {
                if(state.closing || source?.file!=photo.file)throw CancellationException("Photo changed")
                withContext(Dispatchers.Default) {
                    ensureActive()
                    val runtime=Runtime.getRuntime()
                    val available=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())
                    val peak=photo.width.toLong()*photo.height*4*(if(photo.orientation in 2..8)2 else 1)+32L*1024*1024
                    if(peak>available*.8)throw OutOfMemoryError("Original-size preview exceeds available memory")
                    val bitmap=photos.decode(photo,preview=false).also { pending=it }
                    ensureActive()
                    if(!original)renderer.drawInPlace(bitmap,info,style,typography)
                    ensureActive()
                    bitmap
                }
            }
            currentCoroutineContext().ensureActive()
            pending=null
            return result
        } finally { pending?.recycle() }
    }

    fun export(format: ExportFormat, destination: Uri? = null, directory: Uri? = null) {
        if (drafts.isEmpty() || state.busy || (destination != null && drafts.size != 1)) return
        renderJob?.cancel(); cancelLocation(); persist()
        val snapshot = drafts.toList()
        val settings = state.settings
        val typography = fonts.typography
        val options = state.exportOptions.copy(format=format)
        val keepMetadata = options.keepExif
        val jpegQuality = state.jpegQuality
        val fontKey = fonts.selectionKey
        state = state.copy(busy = true, exporting = true, rendering = false, error = null, errorTitle = R.string.error_export_title, notice = null, exportCompleted = 0, exportTotal = snapshot.size)
        workJob = viewModelScope.launch {
            try {
                renderJob?.join()
                val result = runBatch(snapshot, { errorMessage(it, PhotoOperation.SAVE) }, { progress -> state = state.copy(exportCompleted = progress.completed) }) { index, draft ->
                    var info = draft.info
                    if (settings.resolvePhotoLocation && !draft.locationEdited && info[FieldId.LOCATION].isBlank() && draft.source.coordinates != null) {
                        val place = try { geocoder.resolve(draft.source.coordinates) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                        if (place != null) {
                            info = info.with(FieldId.LOCATION, place)
                            drafts = drafts.toMutableList().apply { set(index, draft.copy(info = info, resolvedLocation = place)) }
                        }
                    }
                    val exported = withContext(Dispatchers.IO) {
                        val target = if (directory == null) destination else {
                            val parent = DocumentsContract.buildDocumentUriUsingTree(directory, DocumentsContract.getTreeDocumentId(directory))
                            DocumentsContract.createDocument(app.contentResolver, parent, format.mime, PhotoExporter.filename(format, draft.source.media.motion != null))
                                ?: throw java.io.IOException("Cannot create exported photo")
                        }
                        try { fullSizeMutex.withLock { ExportedPhoto(exporter.export(draft.source, info, draft.style, typography, format, keepMetadata, target, jpegQuality, options), format) } }
                        catch (failure: Throwable) {
                            if (directory != null && target != null) runCatching { DocumentsContract.deleteDocument(app.contentResolver, target) }
                            throw failure
                        }
                    }
                    baselines = baselines + (draft.source.file.name to EditChanges.fingerprint(draft.snapshot(), jpegQuality, keepMetadata, fontKey, options.keepLocation, options.keepCaptureTime))
                    saveBaselines()
                    exported
                }
                val active = drafts[state.photoIndex]
                resolvedLocation = active.resolvedLocation
                state = state.copy(busy = false, exporting = false, info = active.info, exported = result.saved.lastOrNull(),
                    error = result.failures.takeIf { it.isNotEmpty() }?.joinToString("\n") { app.getString(R.string.photo_failure,it.index+1,it.message) },
                    notice = if (result.saved.isEmpty()) null else EditorNotice(++noticeId,
                        if (snapshot.size == 1) app.getString(R.string.export_success)
                        else app.getString(R.string.batch_saved, result.saved.size, result.failures.size), result.saved))
                persist(); renderPreview()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { state = state.copy(busy = false, exporting = false, error = errorMessage(failure, PhotoOperation.SAVE)) }
        }
    }

    /** Explicit exit ends this session; previously published gallery images are untouched. */
    fun closeSession(showProgress: Boolean = true, onClosed: () -> Unit) {
        if (state.closing) return
        state = state.copy(closing = true, busy = true, closingInBackground = !showProgress, notice = null)
        cancelLocation()
        // Do not resurrect discarded photos if the process dies while a native operation finishes.
        forgetSession()
        viewModelScope.launch {
            workJob?.cancelAndJoin(); renderJob?.cancelAndJoin(); locationJob?.cancelAndJoin()
            drafts = emptyList(); source = null; resolvedLocation = ""; locationEdited = false
            state = EditorState(style = state.style, settings = state.settings, fontName = fonts.displayName,
                hasCustomFont = fonts.hasCustomFont, keepCaptureMetadata = state.settings.exportDefaults.keepExif,
                jpegQuality = state.settings.exportDefaults.jpegQuality, keepLocation = state.settings.exportDefaults.keepLocation,
                keepCaptureTime = state.settings.exportDefaults.keepCaptureTime, closing = true, busy = true, closingInBackground = !showProgress)
            fullSizeMutex.withLock { withContext(Dispatchers.IO) { photos.removeOtherDrafts(emptyList()) } }
            state = state.copy(closing = false, busy = false, closingInBackground = false)
            onClosed()
        }
    }

    private fun updateMediaState(photo: PhotoSource) {
        val media = photo.media
        val oldHdr = media.hdrHint && android.os.Build.VERSION.SDK_INT < 34
        state = state.copy(sourceDevice = photo.info[FieldId.DEVICE], sourceModel = photo.captureTags[androidx.exifinterface.media.ExifInterface.TAG_MODEL].orEmpty(), preservationBlocked = media.blocked || oldHdr,
            jpegRequired = media.hdrHint || media.motion != null, motionPhoto = media.motion != null,
            mediaMessage = when {
                media.blocked -> R.string.media_unsupported
                oldHdr -> R.string.hdr_requires_android14
                media.hdrHint && media.motion != null -> R.string.media_hdr_motion
                media.hdrHint -> R.string.media_hdr
                media.motion != null -> R.string.media_motion
                else -> null
            })
    }
    fun clearError() { state = state.copy(error = null) }
    fun clearNotice(id: Long) { if (state.notice?.id == id) state = state.copy(notice = null) }

    private fun renderPreview() {
        renderJob?.cancel()
        val bitmap = state.original ?: return
        val info = state.info; val style = state.style; val typography = fonts.typography
        state = state.copy(rendering = true, previewError = null)
        renderJob = viewModelScope.launch {
            var pending: Bitmap? = null
            try {
                delay(90)
                val rendered = withContext(Dispatchers.Default) { renderer.preview(bitmap, info, style, typography).also { pending = it } }
                state = state.copy(preview = rendered, rendering = false)
                pending = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { state = state.copy(preview = bitmap, rendering = false, previewError = errorMessage(failure, PhotoOperation.PREVIEW)) }
            catch (failure: OutOfMemoryError) { state = state.copy(preview = bitmap, rendering = false, previewError = errorMessage(failure, PhotoOperation.PREVIEW)) }
            finally { pending?.recycle() }
        }
    }
    private fun persist() {
        val index = state.photoIndex
        val active = drafts.getOrNull(index)
        if (active != null && active.source.file == source?.file) {
            drafts = drafts.toMutableList().apply { set(index, active.copy(info = state.info, style = state.style,
                resolvedLocation = resolvedLocation, locationEdited = locationEdited)) }
        }
        saveSnapshots()
        refreshChanges()
        val s = state.style
        saved["style.scale"] = s.scale; saved["style.opacity"] = s.opacity; saved["style.blur"] = s.blur
        saved["style.right"] = s.rightInset; saved["style.bottom"] = s.bottomInset
        saved["style.radius"] = s.cornerRadius; saved["style.textScale"] = s.textScale
    }
    private fun fingerprint(draft: SessionPhoto) = EditChanges.fingerprint(draft.snapshot(), state.jpegQuality, state.keepCaptureMetadata, fonts.selectionKey, state.keepLocation, state.keepCaptureTime)
    private fun refreshChanges() { state = state.copy(hasChanges = drafts.any { baselines[it.source.file.name] != fingerprint(it) }) }
    private fun saveBaselines() { saved["editBaselines"] = ArrayList(baselines.flatMap { listOf(it.key, it.value) }) }
    private fun saveSnapshots() {
        saveBaselines()
        saved["session"] = ArrayList(drafts.flatMap { it.snapshot().fields() })
        saved["photoIndex"] = state.photoIndex
        saved.remove<String>("source")
        FieldId.entries.forEach { saved.remove<String>("field.${it.name}") }
        saved.remove<String>("resolvedLocation"); saved.remove<Boolean>("locationEdited")
    }
    private fun forgetSession() {
        baselines = emptyMap(); saved.remove<ArrayList<String>>("editBaselines")
        saved.remove<ArrayList<String>>("session"); saved.remove<Int>("photoIndex"); saved.remove<String>("source")
        FieldId.entries.forEach { saved.remove<String>("field.${it.name}") }
        saved.remove<String>("resolvedLocation"); saved.remove<Boolean>("locationEdited")
    }
    private fun readStyle() = CardStyle(scale = saved["style.scale"] ?: 1f, opacity = saved["style.opacity"] ?: .6f,
        blur = saved["style.blur"] ?: 25f, rightInset = saved["style.right"] ?: 77f, bottomInset = saved["style.bottom"] ?: 35f,
        cornerRadius = saved["style.radius"] ?: 20f, textScale = saved["style.textScale"] ?: 1f).sanitized()
    private val app get() = getApplication<Application>()
    private fun errorMessage(failure: Throwable, operation: PhotoOperation = PhotoOperation.OPEN): String =
        PhotoFailureMessages.describe(app, failure, operation)
}
