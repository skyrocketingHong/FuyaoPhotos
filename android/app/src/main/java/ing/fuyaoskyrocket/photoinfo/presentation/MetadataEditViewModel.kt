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
import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoDraftAccessPolicy
import ing.fuyaoskyrocket.photoinfo.platform.CardTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MetadataEditViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val drafts = androidx.compose.runtime.mutableStateMapOf<String, ExportOptions>()
    private val baselines = mutableMapOf<String, ExportOptions>()
    private val sourceAccess = PhotoDraftAccessPolicy()
    private var saveJob: Job? = null
    private var savingId: String? = null
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var saved by mutableStateOf(false); private set
    var savedPackage by mutableStateOf(false); private set

    init {
        savedState.get<List<String>>("metadata.edit.ids").orEmpty().takeLast(150).forEach { id ->
            savedState.get<List<String>>("metadata.edit.$id")?.let { drafts[id] = ExportOptions.restore(it) }
            savedState.get<List<String>>("metadata.baseline.$id")?.let { baselines[id] = ExportOptions.restore(it) }
        }
    }

    fun setSourceAccessPolicy(policy: (id: String, path: String) -> Boolean) = sourceAccess.updateSourcePolicy(policy)
    private fun sourceIsEditable(photo: OriginalPhoto): Boolean = sourceAccess.allows(photo.id, photo.file.absolutePath)

    fun options(photo: OriginalPhoto): ExportOptions {
        val initial = { ExportOptions(
            format = if (photo.bitDepth > 8) ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat.HEIC
                else ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat.JPEG, keepLocation = true) }
        if (sourceAccess.isInvalidated(photo.id)) return initial()
        val baseline = baselines.getOrPut(photo.id, initial)
        return drafts[photo.id] ?: baseline
    }
    fun change(photo: OriginalPhoto, options: ExportOptions) {
        if (busy || !sourceIsEditable(photo)) return
        this.options(photo)
        drafts[photo.id] = options
        savedState["metadata.edit.${photo.id}"] = options.fields()
        val ids = (savedState.get<List<String>>("metadata.edit.ids").orEmpty() - photo.id + photo.id)
        ids.dropLast(150).forEach { savedState.remove<List<String>>("metadata.edit.$it"); savedState.remove<List<String>>("metadata.baseline.$it"); drafts.remove(it); baselines.remove(it) }
        savedState["metadata.edit.ids"] = ids.takeLast(150)
    }
    fun hasChanges(id: String?): Boolean = id != null && drafts[id] != null && drafts[id] != baselines[id]

    fun discard(ids: Set<String>) {
        sourceAccess.invalidate(ids)
        if (savingId?.let { it in ids } == true) saveJob?.cancel()
        ids.forEach { id ->
            drafts.remove(id)
            baselines.remove(id)
            savedState.remove<List<String>>("metadata.edit.$id")
            savedState.remove<List<String>>("metadata.baseline.$id")
        }
        savedState["metadata.edit.ids"] = savedState.get<List<String>>("metadata.edit.ids").orEmpty().filterNot { it in ids }
        saved = false
        savedPackage = false
        error = null
    }

    fun dismissResult() { error = null; saved = false }

    fun save(photo: OriginalPhoto, options: ExportOptions, directory: Uri? = null) {
        if (busy || !sourceIsEditable(photo)) return
        busy = true
        savingId = photo.id
        error = null
        saved = false
        savedPackage = options.separateLivePhoto && photo.motion != null
        saveJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val snapshot = File.createTempFile("metadata-edit-", ".photo", app.cacheDir)
                    try {
                        photo.file.inputStream().use { input -> snapshot.outputStream().use(input::copyTo) }
                        ensureActive()
                        val repository = PhotoRepository(app)
                        val source = repository.inspect(snapshot)
                        PhotoExporter(app, repository).export(source, PhotoInfo(), CardStyle(),
                            CardTypography.uniform(Typeface.DEFAULT), options.format, options.keepExif,
                            jpegQuality = options.jpegQuality, options = options, pairDirectory = directory)
                    } finally { snapshot.delete() }
                }
                if (sourceIsEditable(photo)) {
                    saved = true
                    baselines[photo.id] = options
                    savedState["metadata.baseline.${photo.id}"] = options.fields()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (sourceIsEditable(photo)) error = PhotoFailureMessages.describe(getApplication(), failure, PhotoOperation.SAVE) }
            catch (failure: OutOfMemoryError) { if (sourceIsEditable(photo)) error = PhotoFailureMessages.describe(getApplication(), failure, PhotoOperation.SAVE) }
            finally { savingId = null; busy = false }
        }
    }
}
