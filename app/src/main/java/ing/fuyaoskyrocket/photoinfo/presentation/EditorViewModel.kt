package ing.fuyaoskyrocket.photoinfo.presentation

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.data.export.PhotoExporter
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoRepository
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoSource
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardOverflowException
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.domain.model.FieldId
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import ing.fuyaoskyrocket.photoinfo.platform.CardRenderer
import ing.fuyaoskyrocket.photoinfo.platform.FontRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val photos = PhotoRepository(application)
    private val fonts = FontRepository(application)
    private val renderer = CardRenderer()
    private val exporter = PhotoExporter(application, photos)
    private val preferences = application.getSharedPreferences("editor", Context.MODE_PRIVATE)
    private var source: PhotoSource? = null
    private var renderJob: Job? = null
    var state by mutableStateOf(EditorState(
        style = readStyle(), fontName = fonts.displayName, hasCustomFont = fonts.hasCustomFont,
        keepCaptureMetadata = saved["keepMetadata"] ?: true,
    )); private set

    init {
        saved.get<String>("source")?.let { path ->
            state = state.copy(busy = true)
            viewModelScope.launch {
                try {
                    val restored = withContext(Dispatchers.IO) { photos.restore(path) }
                    val bitmap = withContext(Dispatchers.IO) { photos.decode(restored, preview = true) }
                    val values = FieldId.entries.associateWith { saved.get<String>("field.${it.name}") ?: restored.info[it] }
                    source = restored
                    state = state.copy(original = bitmap, preview = bitmap, info = PhotoInfo(values),
                        width = restored.width, height = restored.height, busy = false)
                    renderPreview()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    saved.remove<String>("source")
                    state = state.copy(busy = false, error = errorMessage(failure))
                } catch (failure: OutOfMemoryError) {
                    state = state.copy(busy = false, error = errorMessage(failure))
                }
            }
        }
    }

    fun importPhoto(uri: Uri) {
        if (state.busy) return
        renderJob?.cancel()
        state = state.copy(busy = true, error = null, rendering = false)
        viewModelScope.launch {
            var imported: PhotoSource? = null
            try {
                val loaded = withContext(Dispatchers.IO) { photos.import(uri) }.also { imported = it }
                val bitmap = withContext(Dispatchers.IO) { photos.decode(loaded, preview = true) }
                var info = loaded.info
                if (info[FieldId.AUTHOR].isBlank()) info = info.with(FieldId.AUTHOR, preferences.getString("author", "").orEmpty())
                source = loaded
                state = state.copy(info = info, original = bitmap, preview = bitmap, busy = true,
                    width = loaded.width, height = loaded.height, previewError = null)
                persist()
                withContext(Dispatchers.IO) { photos.removeOtherDrafts(loaded.file) }
                state = state.copy(busy = false)
                renderPreview()
            } catch (cancelled: CancellationException) {
                if (source?.file != imported?.file) imported?.file?.delete()
                throw cancelled
            } catch (failure: Exception) {
                if (source?.file != imported?.file) imported?.file?.delete()
                state = state.copy(busy = false, error = errorMessage(failure))
                renderPreview()
            } catch (failure: OutOfMemoryError) {
                if (source?.file != imported?.file) imported?.file?.delete()
                state = state.copy(busy = false, error = errorMessage(failure))
            }
        }
    }

    fun updateField(field: FieldId, value: String) {
        if (state.busy || value.length > 512) return
        state = state.copy(info = state.info.with(field, value))
        if (field == FieldId.AUTHOR) preferences.edit().putString("author", value).apply()
        persist()
        renderPreview()
    }

    fun resetFields() {
        if (state.busy) return
        source?.let { loaded -> state = state.copy(info = loaded.info); persist(); renderPreview() }
    }

    fun updateStyle(style: CardStyle) {
        if (state.busy) return
        state = state.copy(style = style.sanitized())
        persist()
        renderPreview()
    }

    fun setKeepMetadata(keep: Boolean) {
        if (state.busy) return
        saved["keepMetadata"] = keep
        state = state.copy(keepCaptureMetadata = keep)
    }

    fun importFont(uri: Uri) {
        if (state.busy) return
        renderJob?.cancel()
        state = state.copy(busy = true, rendering = false)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { fonts.import(uri) }
                state = state.copy(busy = false, fontName = fonts.displayName, hasCustomFont = fonts.hasCustomFont)
                renderPreview()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { state = state.copy(busy = false, error = errorMessage(failure)); renderPreview() }
            catch (failure: OutOfMemoryError) { state = state.copy(busy = false, error = errorMessage(failure)); renderPreview() }
        }
    }

    fun resetFont() {
        if (state.busy) return
        fonts.reset()
        state = state.copy(fontName = fonts.displayName, hasCustomFont = false)
        renderPreview()
    }

    fun export(format: ExportFormat, destination: Uri? = null) {
        val photo = source ?: return
        if (!state.canExport) return
        val snapshot = state
        val typeface = fonts.typeface
        state = state.copy(busy = true, exporting = true, error = null)
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    exporter.export(photo, snapshot.info, snapshot.style, typeface,
                        format, snapshot.keepCaptureMetadata, destination)
                }
                state = state.copy(busy = false, exporting = false, exported = ExportedPhoto(uri, format),
                    notice = getApplication<Application>().getString(R.string.export_success))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { state = state.copy(busy = false, exporting = false, error = errorMessage(failure)) }
            catch (failure: OutOfMemoryError) { state = state.copy(busy = false, exporting = false, error = errorMessage(failure)) }
        }
    }

    fun clearError() { state = state.copy(error = null) }
    fun clearNotice() { state = state.copy(notice = null) }

    private fun renderPreview() {
        renderJob?.cancel()
        val bitmap = state.original ?: return
        val info = state.info
        val style = state.style
        val typeface = fonts.typeface
        state = state.copy(rendering = true, previewError = null)
        renderJob = viewModelScope.launch {
            try {
                delay(90)
                val rendered = withContext(Dispatchers.Default) { renderer.preview(bitmap, info, style, typeface) }
                state = state.copy(preview = rendered, rendering = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                state = state.copy(preview = bitmap, rendering = false, previewError = errorMessage(failure))
            } catch (failure: OutOfMemoryError) {
                state = state.copy(preview = bitmap, rendering = false, previewError = errorMessage(failure))
            }
        }
    }

    private fun readStyle() = CardStyle(
        scale = saved["style.scale"] ?: 1f, opacity = saved["style.opacity"] ?: .6f,
        blur = saved["style.blur"] ?: 25f, rightInset = saved["style.right"] ?: 77f,
        bottomInset = saved["style.bottom"] ?: 35f, cornerRadius = saved["style.radius"] ?: 20f,
        textScale = saved["style.textScale"] ?: CardStyle().textScale,
    ).sanitized()

    private fun persist() {
        source?.let { saved["source"] = it.file.absolutePath }
        FieldId.entries.forEach { saved["field.${it.name}"] = state.info[it] }
        val s = state.style
        saved["style.scale"] = s.scale; saved["style.opacity"] = s.opacity
        saved["style.blur"] = s.blur; saved["style.right"] = s.rightInset
        saved["style.bottom"] = s.bottomInset; saved["style.radius"] = s.cornerRadius
        saved["style.textScale"] = s.textScale
    }

    private fun errorMessage(failure: Throwable): String {
        val app = getApplication<Application>()
        return when (failure) {
            is CardOverflowException -> app.getString(R.string.error_overflow)
            is OutOfMemoryError -> app.getString(R.string.error_memory)
            else -> app.getString(R.string.error_operation, failure.localizedMessage ?: failure.javaClass.simpleName)
        }
    }
}
