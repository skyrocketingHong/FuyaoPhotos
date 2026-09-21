package ing.fuyaoskyrocket.photoinfo

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ing.fuyaoskyrocket.photoinfo.data.settings.SettingsRepository
import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.presentation.EditorViewModel
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorSessionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun awaitReady(vm: EditorViewModel) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            onMain { ready = !vm.state.busy && !vm.state.rendering }
            if (ready) return
            SystemClock.sleep(20)
        }
        fail("Editor did not finish loading")
    }

    @Test fun swipingRestorationAndExplicitExitKeepPhotoEditsIsolated() {
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "session-test-${UUID.randomUUID()}").apply { mkdirs() }
        val app = IsolatedApplication(context, root)
        val state = SavedStateHandle()
        val store = ViewModelStore()
        lateinit var vm: EditorViewModel
        var firstId = ""
        val originals = (0..1).map { index ->
            File(root, "original-$index.jpg").also { file ->
                val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(if(index==0) Color.GRAY else Color.BLUE) }
                try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) } }
                finally { bitmap.recycle() }
            }
        }
        try {
            SettingsRepository(app).save(EditorSettings(defaultAuthor = "Default", resolvePhotoLocation = false))
            onMain {
                vm = EditorViewModel(app, state); store.put("editor", vm)
                assertEquals(100, vm.state.jpegQuality)
                vm.importPhotos(originals.map(Uri::fromFile))
            }
            awaitReady(vm)
            onMain { assertFalse(vm.state.hasChanges); vm.updateField(FieldId.AUTHOR, "Temporary"); assertTrue(vm.state.hasChanges); vm.updateField(FieldId.AUTHOR, "Default"); assertFalse(vm.state.hasChanges); assertEquals(2, vm.state.photos.size); firstId = vm.state.photos.first().id; vm.updateField(FieldId.AUTHOR, "First"); vm.updateStyle(CardStyle(textScale=1.5f)); vm.selectPhoto(1) }
            awaitReady(vm)
            onMain { vm.updateField(FieldId.AUTHOR, "Late callback", firstId); vm.updateStyle(CardStyle(textScale=1.8f), firstId); assertEquals("Default", vm.state.info[FieldId.AUTHOR]); assertEquals(1f, vm.state.style.textScale, .001f); vm.updateField(FieldId.AUTHOR, "Second"); vm.setJpegQuality(82); vm.selectPhoto(0) }
            awaitReady(vm)
            onMain {
                assertEquals("First", vm.state.info[FieldId.AUTHOR]); assertEquals(1.5f, vm.state.style.textScale, .001f)
                store.clear(); vm = EditorViewModel(app, state); store.put("restored", vm)
            }
            awaitReady(vm)
            onMain { assertEquals(2, vm.state.photos.size); assertEquals("First", vm.state.info[FieldId.AUTHOR]); assertEquals(82, vm.state.jpegQuality) }
            val closed = CountDownLatch(1)
            onMain { vm.closeSession { closed.countDown() } }
            assertTrue(closed.await(15, TimeUnit.SECONDS))
            onMain { assertTrue(vm.state.photos.isEmpty()); assertNull(vm.state.original); assertNull(state.get<ArrayList<String>>("session")) }
            assertTrue(originals.all { it.isFile })
            assertTrue(File(app.filesDir, "drafts").listFiles().orEmpty().isEmpty())
        } finally {
            onMain { store.clear() }
            app.cleanPreferences()
            root.deleteRecursively()
        }
    }

    private class IsolatedApplication(private val original: Context, private val directory: File) : Application() {
        private val prefix = directory.name
        private val preferences = mutableSetOf<String>()
        init { attachBaseContext(original) }
        override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
        override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val key = "$prefix-$name"; preferences += key
            return original.getSharedPreferences(key, mode)
        }
        fun cleanPreferences() { preferences.forEach(original::deleteSharedPreferences) }
    }
}
