package ing.fuyaoskyrocket.photoinfo.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoEditSnapshot
import ing.fuyaoskyrocket.photoinfo.presentation.ExportedPhoto

/** External intents supply bounded image/package content URIs, never nested intents or file paths. */
object PhotoIntents {
    fun sharedImages(intent: Intent): List<Uri>? {
        val packageMime = ing.fuyaoskyrocket.photoinfo.domain.media.FuyaoPhotosPackage.MIME
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = requireNotNull(intent.data)
            require(uri.scheme == "content" && !uri.authority.isNullOrBlank())
            require(intent.type == packageMime || uri.lastPathSegment?.endsWith(".fuyaophotos", true) == true)
            return listOf(uri)
        }
        if(intent.action !in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE)) return null
        require(intent.type?.startsWith("image/")==true || intent.type == packageMime)
        val streams=if(intent.action==Intent.ACTION_SEND)
            IntentCompat.getParcelableExtra(intent,Intent.EXTRA_STREAM,Uri::class.java)?.let(::listOf)
        else IntentCompat.getParcelableArrayListExtra(intent,Intent.EXTRA_STREAM,Uri::class.java)
        val values=streams ?: intent.clipData?.let { clip ->
            require(clip.itemCount in 1..PhotoEditSnapshot.MAX_PHOTOS)
            List(clip.itemCount) { requireNotNull(clip.getItemAt(it).uri) }
        }.orEmpty()
        // ASVS 2.2.1/5.2.1: validate before import; PhotoRepository also caps bytes and decoded pixels.
        require(values.size in 1..PhotoEditSnapshot.MAX_PHOTOS)
        require(values.all { it.scheme=="content" && !it.authority.isNullOrBlank() })
        if(intent.action==Intent.ACTION_SEND) require(values.size==1)
        return values.distinct()
    }
    fun open(photo: ExportedPhoto): Intent = Intent(Intent.ACTION_VIEW).apply {
        require(photo.uri.scheme=="content")
        setDataAndType(photo.uri,photo.mime)
        clipData=ClipData.newRawUri("Photo",photo.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    fun share(photos: List<ExportedPhoto>): Intent {
        require(photos.isNotEmpty() && photos.all { it.uri.scheme=="content" })
        val uris=ArrayList(photos.flatMap { listOfNotNull(it.uri,it.movieUri) })
        require(uris.all { it.scheme=="content" })
        return Intent(if(uris.size==1)Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type=if(photos.any { it.movieUri!=null }) "*/*" else photos.map { it.mime }.distinct().singleOrNull() ?: "*/*"
            if(uris.size==1)putExtra(Intent.EXTRA_STREAM,uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
            clipData=ClipData.newRawUri("Photo",uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent);true
    } catch (_: android.content.ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }
}
