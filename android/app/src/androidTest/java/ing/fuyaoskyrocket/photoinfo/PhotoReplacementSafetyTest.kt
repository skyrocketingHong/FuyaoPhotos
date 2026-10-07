package ing.fuyaoskyrocket.photoinfo

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ing.fuyaoskyrocket.photoinfo.data.settings.SettingsRepository
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings
import ing.fuyaoskyrocket.photoinfo.domain.model.FieldId
import ing.fuyaoskyrocket.photoinfo.presentation.EditorViewModel
import ing.fuyaoskyrocket.photoinfo.presentation.MetadataEditViewModel
import ing.fuyaoskyrocket.photoinfo.presentation.MetadataViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.CRC32

@RunWith(AndroidJUnit4::class)
class PhotoReplacementSafetyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Test fun failedPixelDecodeKeepsCardDraftAndDoesNotDispatchReplacement() = withFixtures { app, good, bad, store ->
        val saved = SavedStateHandle()
        lateinit var model: EditorViewModel
        onMain {
            SettingsRepository(app).save(EditorSettings(resolvePhotoLocation = false))
            model = EditorViewModel(app, saved)
            store.put("cards", model)
            model.importPhoto(Uri.fromFile(good))
        }
        awaitIdle { !model.state.busy && !model.state.rendering }
        var oldId = ""
        var oldPath = ""
        var snapshot: ArrayList<String>? = null
        var replacements = 0
        onMain {
            model.updateField(FieldId.AUTHOR, "Keep this draft")
            oldId = model.state.photos.single().id
            oldPath = model.state.photos.single().path
            snapshot = saved["session"]
            model.importPhotos(listOf(Uri.fromFile(bad))) { replacements++ }
        }
        awaitIdle { !model.state.busy && !model.state.rendering }
        onMain {
            assertEquals(0, replacements)
            assertEquals(oldId, model.state.photos.single().id)
            assertEquals("Keep this draft", model.state.info[FieldId.AUTHOR])
            assertTrue(model.state.hasChanges)
            assertNotNull(model.state.original)
            assertNotNull(model.state.error)
            assertEquals(snapshot, saved.get<ArrayList<String>>("session"))
        }
        assertTrue(File(oldPath).isFile)
    }

    @Test fun failedPixelDecodeKeepsMetadataSourceAndUnsavedOptions() = withFixtures { app, good, bad, store ->
        val saved = SavedStateHandle()
        val editSaved = SavedStateHandle()
        lateinit var model: MetadataViewModel
        lateinit var edits: MetadataEditViewModel
        val context = instrumentation.targetContext
        fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        onMain {
            model = MetadataViewModel(app, saved)
            edits = MetadataEditViewModel(app, editSaved)
            store.put("metadata", model)
            store.put("edits", edits)
            edits.setSourceAccessPolicy { id, path ->
                !model.state.busy && model.state.current?.let { it.id == id && it.file.absolutePath == path } == true
            }
            model.importPhotos(listOf(uri(good)))
        }
        awaitIdle { !model.state.busy }
        var oldId = ""
        var oldPath = ""
        var replacements = 0
        onMain {
            val photo = requireNotNull(model.state.current)
            oldId = photo.id
            oldPath = photo.file.absolutePath
            edits.change(photo, edits.options(photo).copy(keepExif = false))
            assertTrue(edits.hasChanges(oldId))
            model.importPhotos(listOf(uri(bad))) { replacements++; edits.discard(setOf(oldId)) }
            edits.change(photo, edits.options(photo).copy(keepExif = true))
            assertTrue(edits.hasChanges(oldId))
        }
        awaitIdle { !model.state.busy }
        onMain {
            assertEquals(0, replacements)
            assertEquals(oldId, model.state.current?.id)
            assertTrue(edits.hasChanges(oldId))
            assertNotNull(editSaved.get<List<String>>("metadata.edit.$oldId"))
            assertEquals(listOf(oldPath), saved.get<ArrayList<String>>("metadata.paths"))
            assertNotNull(model.state.error)
            edits.discard(setOf(oldId))
            val oldPhoto = requireNotNull(model.state.current)
            edits.change(oldPhoto, edits.options(oldPhoto).copy(keepExif = false))
            edits.save(oldPhoto, edits.options(oldPhoto))
            assertFalse(edits.busy)
            assertFalse(edits.hasChanges(oldId))
            assertNull(editSaved.get<List<String>>("metadata.edit.$oldId"))
            assertNull(editSaved.get<List<String>>("metadata.baseline.$oldId"))
        }
        assertTrue(File(oldPath).isFile)
    }

    private fun awaitIdle(ready: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var idle = false
            onMain { idle = ready() }
            if (idle) return
            SystemClock.sleep(20)
        }
        fail("Photo operation did not complete")
    }

    private fun withFixtures(test: (IsolatedApplication, File, File, ViewModelStore) -> Unit) {
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "camera/import-safety-${UUID.randomUUID()}").apply { mkdirs() }
        val app = IsolatedApplication(context, root)
        val store = ViewModelStore()
        val good = File(root, "good.png")
        val bad = File(root, "bad.png")
        try {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            try { good.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            val data = good.readBytes()
            var offset = 8
            var corrupted = false
            while (offset + 12 <= data.size) {
                val count = ByteBuffer.wrap(data, offset, 4).int
                if (String(data, offset + 4, 4, Charsets.US_ASCII) == "IDAT") {
                    data.fill(0, offset + 8, offset + 8 + count)
                    val crc = CRC32().apply { update(data, offset + 4, count + 4) }.value.toInt()
                    ByteBuffer.wrap(data, offset + 8 + count, 4).putInt(crc)
                    corrupted = true
                }
                offset += count + 12
            }
            assertTrue(corrupted)
            bad.writeBytes(data)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(bad.absolutePath, bounds)
            assertEquals(32, bounds.outWidth)
            val invalid = BitmapFactory.decodeFile(bad.absolutePath)
            try { assertNull("Fixture must fail pixel decoding after successful bounds decoding", invalid) }
            finally { invalid?.recycle() }
            test(app, good, bad, store)
        } finally {
            onMain { store.clear() }
            app.cleanPreferences()
            root.deleteRecursively()
        }
    }

    private class IsolatedApplication(private val original: Context, private val root: File) : Application() {
        private val preferences = mutableSetOf<String>()
        init { attachBaseContext(original) }
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val key = "${root.name}-$name"
            preferences += key
            return original.getSharedPreferences(key, mode)
        }
        fun cleanPreferences() { preferences.forEach(original::deleteSharedPreferences) }
    }
}
