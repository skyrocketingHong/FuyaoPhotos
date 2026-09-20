package ing.fuyaoskyrocket.photoinfo.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import ing.fuyaoskyrocket.photoinfo.domain.metadata.LocationFormatting
import ing.fuyaoskyrocket.photoinfo.domain.metadata.PhotoCoordinates
import java.util.Locale
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** ASVS 14.2.3/14.2.4: only coordinates go to the system provider; never log or separately persist coordinates. */
class PhotoGeocoder(private val context: Context) {
    suspend fun resolve(point: PhotoCoordinates): String? {
        if (!Geocoder.isPresent()) return null
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                fun finish(addresses: List<Address>) {
                    val name = addresses.firstOrNull()?.let {
                        LocationFormatting.place(it.locality, it.subAdminArea, it.adminArea, it.countryName, it.countryCode)
                    }?.takeIf { it.isNotBlank() }
                    if (completed.compareAndSet(false, true) && continuation.isActive) continuation.resume(name)
                }
                val geocoder = Geocoder(context, Locale.ENGLISH)
                if (Build.VERSION.SDK_INT >= 33) {
                    try {
                        geocoder.getFromLocation(point.latitude, point.longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = finish(addresses)
                            override fun onError(errorMessage: String?) = finish(emptyList())
                        })
                    } catch (_: Exception) { finish(emptyList()) }
                } else {
                    // A bounded shared executor avoids blocking UI or waiting for old platform Binder calls on timeout.
                    val job = try { legacyExecutor.submit {
                        @Suppress("DEPRECATION")
                        val result = runCatching { geocoder.getFromLocation(point.latitude, point.longitude, 1) }.getOrNull()
                        finish(result.orEmpty())
                    } } catch (_: java.util.concurrent.RejectedExecutionException) {
                        finish(emptyList()); return@suspendCancellableCoroutine
                    }
                    continuation.invokeOnCancellation { job.cancel(true) }
                }
            }
        }
    }
    companion object {
        private val legacyExecutor = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue(2),
            { runnable -> Thread(runnable, "photo-geocoder").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    }
}
