package ing.fuyaoskyrocket.photoinfo

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.platform.PhotoIntents
import ing.fuyaoskyrocket.photoinfo.presentation.ExportedPhoto
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoIntentsTest {
    private val first=Uri.parse("content://gallery/images/1")
    private val second=Uri.parse("content://gallery/images/2")
    @Test fun singleMultipleAndClipDataUseTheSameValidatedImageList() {
        assertNull(PhotoIntents.sharedImages(Intent(Intent.ACTION_MAIN)))
        val single=Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM,first)
        assertEquals(listOf(first),PhotoIntents.sharedImages(single))
        val multiple=Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM,arrayListOf(first,second,first))
        assertEquals(listOf(first,second),PhotoIntents.sharedImages(multiple))
        val clip=Intent(Intent.ACTION_SEND).setType("image/png").apply { clipData=ClipData.newRawUri("image",first) }
        assertEquals(listOf(first),PhotoIntents.sharedImages(clip))
    }
    @Test fun malformedUntrustedAndOversizedPayloadsAreRejected() {
        for(uri in listOf("file:///data/private.jpg","https://example.com/a.jpg","content:///missing")) {
            assertThrows(IllegalArgumentException::class.java) {
                PhotoIntents.sharedImages(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM,Uri.parse(uri)))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { PhotoIntents.sharedImages(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,first)) }
        assertThrows(IllegalArgumentException::class.java) { PhotoIntents.sharedImages(Intent(Intent.ACTION_SEND).setType("image/jpeg")) }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoIntents.sharedImages(Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(List(51){ first })))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoIntents.sharedImages(Intent(Intent.ACTION_SEND).setType("image/jpeg").apply {
                clipData=ClipData.newIntent("nested",Intent(Intent.ACTION_VIEW,first))
            })
        }
    }
    @Test fun openAndShareGrantReadAccessOnlyToTheExportedUris() {
        val photos=listOf(ExportedPhoto(first,ExportFormat.JPEG),ExportedPhoto(second,ExportFormat.PNG))
        val open=PhotoIntents.open(photos.first())
        assertEquals(Intent.ACTION_VIEW,open.action);assertEquals(first,open.data);assertEquals("image/jpeg",open.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,open.flags)
        assertEquals(first,open.clipData!!.getItemAt(0).uri)
        val share=PhotoIntents.share(photos)
        assertEquals(Intent.ACTION_SEND_MULTIPLE,share.action);assertEquals("image/*",share.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,share.flags)
        assertEquals(2,share.clipData!!.itemCount)
    }
}
