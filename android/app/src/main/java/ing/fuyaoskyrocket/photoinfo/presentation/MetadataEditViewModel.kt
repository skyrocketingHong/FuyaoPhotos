package ing.fuyaoskyrocket.photoinfo.presentation

import android.app.Application
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ing.fuyaoskyrocket.photoinfo.data.export.PhotoExporter
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoRepository
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import ing.fuyaoskyrocket.photoinfo.platform.CardTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MetadataEditViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val drafts = androidx.compose.runtime.mutableStateMapOf<String, ExportOptions>()
    private val baselines = mutableMapOf<String, ExportOptions>()
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var saved by mutableStateOf(false); private set

    init {
        savedState.get<List<String>>("metadata.edit.ids").orEmpty().takeLast(150).forEach { id ->
            savedState.get<List<String>>("metadata.edit.$id")?.let { drafts[id] = ExportOptions.restore(it) }
            savedState.get<List<String>>("metadata.baseline.$id")?.let { baselines[id] = ExportOptions.restore(it) }
        }
    }

    fun options(photo: OriginalPhoto): ExportOptions {
        val baseline = baselines.getOrPut(photo.id) { ExportOptions(
            format = if (photo.bitDepth > 8) ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat.HEIC
                else ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat.JPEG, keepLocation = true) }
        return drafts[photo.id] ?: baseline
    }
    fun change(photo: OriginalPhoto, options: ExportOptions) {
        this.options(photo)
        drafts[photo.id] = options
        savedState["metadata.edit.${photo.id}"] = options.fields()
        val ids = (savedState.get<List<String>>("metadata.edit.ids").orEmpty() - photo.id + photo.id)
        ids.dropLast(150).forEach { savedState.remove<List<String>>("metadata.edit.$it"); savedState.remove<List<String>>("metadata.baseline.$it"); drafts.remove(it); baselines.remove(it) }
        savedState["metadata.edit.ids"] = ids.takeLast(150)
    }
    fun hasChanges(id: String?): Boolean = id != null && drafts[id] != null && drafts[id] != baselines[id]

    fun dismissResult() { error = null; saved = false }

    fun save(photo: OriginalPhoto, options: ExportOptions, directory: Uri? = null) {
        if (busy) return
        busy = true
        error = null
        saved = false
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val snapshot = File.createTempFile("metadata-edit-", ".photo", app.cacheDir)
                    try {
                        photo.file.inputStream().use { input -> snapshot.outputStream().use(input::copyTo) }
                        val repository = PhotoRepository(app)
                        val source = repository.inspect(snapshot)
                        PhotoExporter(app, repository).export(source, PhotoInfo(), CardStyle(),
                            CardTypography.uniform(Typeface.DEFAULT), options.format, options.keepExif,
                            jpegQuality = options.jpegQuality, options = options, pairDirectory = directory)
                    } finally { snapshot.delete() }
                }
                saved = true
                baselines[photo.id] = options
                savedState["metadata.baseline.${photo.id}"] = options.fields()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = PhotoFailureMessages.describe(getApplication(), failure, PhotoOperation.SAVE) }
            catch (failure: OutOfMemoryError) { error = PhotoFailureMessages.describe(getApplication(), failure, PhotoOperation.SAVE) }
            finally { busy = false }
        }
    }
}
