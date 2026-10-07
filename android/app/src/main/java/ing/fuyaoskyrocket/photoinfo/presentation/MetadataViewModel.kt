package ing.fuyaoskyrocket.photoinfo.presentation

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoRepository
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoSource
import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoEditSnapshot
import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoSessionKind
import ing.fuyaoskyrocket.photoinfo.platform.MotionClipSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MetadataPhotos(
    val photos: List<PhotoPageItem> = emptyList(),
    val photoIndex: Int = 0,
    val current: OriginalPhoto? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

open class MetadataViewModel(application: Application, private val saved: SavedStateHandle, kind: PhotoSessionKind) : AndroidViewModel(application) {
    constructor(application: Application, saved: SavedStateHandle) : this(application, saved, PhotoSessionKind.METADATA)
    private val repository = PhotoRepository(application, kind)
    private var sources = emptyList<PhotoSource>()
    private var work: Job? = null
    var state by mutableStateOf(MetadataPhotos()); private set

    init {
        val paths = saved.get<ArrayList<String>>("metadata.paths").orEmpty()
        val names = saved.get<ArrayList<String>>("metadata.names").orEmpty()
        if (paths.isNotEmpty()) {
            state = state.copy(busy = true)
            work = viewModelScope.launch {
                try {
                    require(paths.size <= PhotoEditSnapshot.MAX_PHOTOS)
                    sources = withContext(Dispatchers.IO) {
                        paths.mapIndexed { index, path ->
                            ensureActive()
                            repository.restore(path).let { source ->
                                source.copy(details = source.details.copy(displayName = names.getOrNull(index)?.takeIf(String::isNotBlank)))
                            }
                        }
                    }
                    publish()
                    load((saved.get<Int>("metadata.index") ?: 0).coerceIn(sources.indices))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { report(failure) }
                catch (failure: OutOfMemoryError) { report(failure) }
            }
        }
    }

    fun importPhotos(uris: List<Uri>, onReplacing: () -> Unit = {}) {
        if (state.busy || uris.isEmpty()) return
        val selected = uris.distinct()
        if (selected.size > PhotoEditSnapshot.MAX_PHOTOS) {
            state = state.copy(error = getApplication<Application>().getString(ing.fuyaoskyrocket.photoinfo.R.string.batch_limit, PhotoEditSnapshot.MAX_PHOTOS))
            return
        }
        if (selected.any { it.scheme != "content" || it.authority.isNullOrBlank() }) {
            state = state.copy(error = getApplication<Application>().getString(ing.fuyaoskyrocket.photoinfo.R.string.shared_photo_invalid))
            return
        }
        work?.cancel()
        state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            val imported = mutableListOf<PhotoSource>()
            val failures = mutableListOf<String>()
            var preparedPreview: Bitmap? = null
            try {
                withContext(Dispatchers.IO) {
                    for (uri in selected) {
                        ensureActive()
                        try { imported += repository.import(uri) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { failures += message(failure) }
                    }
                }
                if (imported.isEmpty()) {
                    state = state.copy(busy = false, error = failures.distinct().joinToString("\n"))
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    repository.decode(imported.first(), preview = true).also { preparedPreview = it }
                }
                ensureActive()
                if (sources.isNotEmpty()) onReplacing()
                sources = imported.toList()
                publish()
                saved["metadata.paths"] = ArrayList(sources.map { it.file.absolutePath })
                saved["metadata.names"] = ArrayList(sources.map { it.details.displayName.orEmpty() })
                val preview = requireNotNull(preparedPreview)
                preparedPreview = null
                load(0, preview, markReady = false)
                withContext(Dispatchers.IO) { repository.removeOtherDrafts(sources.map { it.file }) }
                state = state.copy(busy = false)
                if (failures.isNotEmpty()) state = state.copy(error = failures.distinct().joinToString("\n"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { report(failure) }
            catch (failure: OutOfMemoryError) { report(failure) }
            finally {
                preparedPreview?.recycle()
                imported.filter { it !in sources }.forEach { it.file.delete() }
            }
        }
    }

    fun selectPhoto(index: Int) {
        if (state.busy || index !in sources.indices || index == state.photoIndex) return
        work?.cancel()
        work = viewModelScope.launch { load(index) }
    }

    fun clearError() { state = state.copy(error = null) }

    fun closeSession() {
        val previous = work
        previous?.cancel()
        saved.remove<ArrayList<String>>("metadata.paths")
        saved.remove<ArrayList<String>>("metadata.names")
        saved.remove<Int>("metadata.index")
        state = MetadataPhotos(busy = true)
        work = viewModelScope.launch {
            try {
                previous?.cancelAndJoin()
                sources = emptyList()
                withContext(Dispatchers.IO) { repository.removeOtherDrafts(emptyList()) }
                state = MetadataPhotos()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { state = MetadataPhotos(error = message(failure)) }
        }
    }

    private fun publish() {
        state = state.copy(photos = sources.map { PhotoPageItem(it.file.name, it.width, it.height, it.file.absolutePath, it.media.bitDepth) })
    }

    private suspend fun load(index: Int, preparedPreview: Bitmap? = null, markReady: Boolean = true) {
        val photo = sources[index]
        val motion = photo.media.motion?.let { MotionClipSource(photo.file, it.offset, it.length) }
        val original = OriginalPhoto(photo.file.name, photo.file, null, photo.details,
            photo.media.hdrHint, photo.media.portraitTail != null, motion, photo.media.bitDepth)
        saved["metadata.index"] = index
        state = state.copy(photoIndex = index, current = original, busy = true)
        var pending: Bitmap? = preparedPreview
        try {
            val bitmap = preparedPreview ?: withContext(Dispatchers.IO) { repository.decode(photo, preview = true).also { pending = it } }
            currentCoroutineContext().ensureActive()
            state = state.copy(current = original.copy(bitmap = bitmap), busy = !markReady)
            pending = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { report(failure) }
        catch (failure: OutOfMemoryError) { report(failure) }
        finally { pending?.recycle() }
    }

    private fun message(failure: Throwable): String =
        PhotoFailureMessages.describe(getApplication(), failure, PhotoOperation.OPEN)

    private fun report(failure: Throwable) { state = state.copy(busy = false, error = message(failure)) }

    override fun onCleared() {
        work?.cancel()
        sources.forEach { it.file.delete() }
        super.onCleared()
    }
}
