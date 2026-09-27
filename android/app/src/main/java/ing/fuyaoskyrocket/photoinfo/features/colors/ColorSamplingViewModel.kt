package ing.fuyaoskyrocket.photoinfo.features.colors

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoRepository
import ing.fuyaoskyrocket.photoinfo.features.colors.data.photo.describePhoto
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.color.samplePixel
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.PhotoColorInfo
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor
import ing.fuyaoskyrocket.photoinfo.features.colors.presentation.model.PhotoViewportTransform
import ing.fuyaoskyrocket.photoinfo.presentation.OriginalPhoto
import ing.fuyaoskyrocket.photoinfo.presentation.PhotoFailureMessages
import ing.fuyaoskyrocket.photoinfo.presentation.PhotoOperation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal class ColorSamplingViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    var bitmap by mutableStateOf<Bitmap?>(null); private set
    var info by mutableStateOf<PhotoColorInfo?>(null); private set
    var sample by mutableStateOf<SampledColor?>(null); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var hdr by mutableStateOf(saved.get<Boolean>("color.hdr") ?: true); private set
    var transform by mutableStateOf(PhotoViewportTransform(
        saved.get<Float>("color.zoom") ?: 1f,
        Offset(saved.get<Float>("color.panX") ?: 0f, saved.get<Float>("color.panY") ?: 0f))); private set
    private var currentID: String? = null
    private var work: Job? = null
    private val requests = MutableStateFlow<Pair<Int, Int>?>(null)

    init {
        viewModelScope.launch {
            requests.filterNotNull().collectLatest { point ->
                val source = bitmap ?: return@collectLatest
                try {
                    val result = withContext(Dispatchers.Default) { samplePixel(source,
                        point.first.coerceIn(0, source.width - 1), point.second.coerceIn(0, source.height - 1)) }
                    if (bitmap === source) {
                        sample = result
                        error = null
                        saved["color.x"] = result.sourceX
                        saved["color.y"] = result.sourceY
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = PhotoFailureMessages.describe(getApplication(), failure, PhotoOperation.OPEN) }
            }
        }
    }

    fun load(photo: OriginalPhoto?) {
        if (currentID == photo?.id && bitmap != null) return
        work?.cancel()
        val restore = saved.get<String>("color.photo") == photo?.id
        currentID = photo?.id
        saved["color.photo"] = photo?.id
        bitmap = null; info = null; sample = null; requests.value = null; error = null
        if (!restore) updateTransform(PhotoViewportTransform())
        if (photo == null) { busy = false; return }
        busy = true
        work = viewModelScope.launch {
            var unpublished: Bitmap? = null
            try {
                val image = withContext(Dispatchers.IO) {
                    val repository = PhotoRepository(getApplication())
                    repository.decode(repository.inspect(photo.file), preview = false).also { unpublished = it }
                }
                ensureActive()
                bitmap = image
                info = describePhoto(image)
                unpublished = null
                sampleAt(if (restore) saved.get<Int>("color.x") ?: image.width / 2 else image.width / 2,
                    if (restore) saved.get<Int>("color.y") ?: image.height / 2 else image.height / 2)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = PhotoFailureMessages.describe(getApplication(), failure, PhotoOperation.OPEN) }
            catch (failure: OutOfMemoryError) { error = PhotoFailureMessages.describe(getApplication(), failure, PhotoOperation.OPEN) }
            finally { unpublished?.recycle(); if (currentID == photo.id) busy = false }
        }
    }

    fun sampleAt(x: Int, y: Int) { requests.value = x to y }
    fun setHDR(enabled: Boolean) { hdr = enabled; saved["color.hdr"] = enabled }
    fun updateTransform(value: PhotoViewportTransform) {
        transform = value
        saved["color.zoom"] = value.zoom; saved["color.panX"] = value.pan.x; saved["color.panY"] = value.pan.y
    }
}
