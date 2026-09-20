package ing.fuyaoskyrocket.photoinfo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ing.fuyaoskyrocket.photoinfo.data.export.PhotoExporter
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoRepository
import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.platform.CardRenderer
import ing.fuyaoskyrocket.photoinfo.platform.FontRepository
import ing.fuyaoskyrocket.photoinfo.data.settings.SettingsRepository
import android.content.Context
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** These checks require an emulator or device; compilation alone does not verify rendering. */
@RunWith(AndroidJUnit4::class)
class PhotoPipelineTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun exifGpsAndXiaomiLensAreReadTogether() = runBlocking {
        val original = quadrantPhoto(1)
        ExifInterface(original).apply {
            setAttribute(ExifInterface.TAG_MAKE, "Xiaomi")
            setAttribute(ExifInterface.TAG_MODEL, "Xiaomi 17 Ultra by Leica")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, "75")
            saveAttributes()
        }
        try {
            val source = PhotoRepository(context).import(Uri.fromFile(original))
            try {
                assertEquals(30.0, requireNotNull(source.coordinates).latitude, .00001)
                assertEquals(120.0, requireNotNull(source.coordinates).longitude, .00001)
                assertEquals("75 MM (3.2X)", source.info[FieldId.FOCAL_LENGTH])
                assertEquals("LEICA TELEPHOTO", source.info[FieldId.CAMERA])
                assertTrue(source.info[FieldId.LOCATION].isEmpty()) // No invented place before geocoding.
            } finally { source.file.delete() }
        } finally { original.delete() }
    }

    @Test fun settingsPersistAndMigrateThePreviousPhotographer() {
        val preferences = context.getSharedPreferences("editor", Context.MODE_PRIVATE)
        val repository = SettingsRepository(context)
        val previous = repository.read()
        try {
            preferences.edit().remove("defaultAuthor").putString("author", "Legacy author").commit()
            assertEquals("Legacy author", repository.read().defaultAuthor)
            val expected = EditorSettings("New author", false, "24")
            repository.save(expected)
            assertEquals(expected, SettingsRepository(context).read())
            assertFalse(preferences.contains("author"))
        } finally { repository.save(previous) }
    }

    @Test fun bundledFontIsDefaultAndResetRestoresIt() {
        val fonts = FontRepository(context)
        fonts.reset()
        val bundled = context.assets.list("fonts").orEmpty().contains("SF-Mono-Regular.otf")
        assertEquals(if (bundled) "SF Mono Regular" else null, fonts.displayName)
        assertFalse(fonts.hasCustomFont)
        if (!bundled) return // A public source checkout intentionally has no private font binary.
        val default = fonts.typeface
        val imported = File.createTempFile("font-", ".otf", context.cacheDir)
        try {
            context.assets.open("fonts/SF-Mono-Regular.otf").use { input ->
                imported.outputStream().use { input.copyTo(it) }
            }
            fonts.import(Uri.fromFile(imported))
            assertTrue(fonts.hasCustomFont)
            assertNotNull(FontRepository(context).typeface)
            fonts.reset()
            assertEquals("SF Mono Regular", fonts.displayName)
            assertFalse(fonts.hasCustomFont)
            assertSame(default, fonts.typeface)
        } finally { imported.delete(); fonts.reset() }
    }

    @Test fun allEightExifOrientationsDecodeOnce() = runBlocking {
        val repository = PhotoRepository(context)
        val expectedTopLeft = listOf(Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE,
            Color.RED, Color.BLUE, Color.YELLOW, Color.GREEN)
        for (orientation in 1..8) {
            val file = quadrantPhoto(orientation)
            val source = repository.import(Uri.fromFile(file))
            try {
                val bitmap = repository.decode(source, preview = false)
                try {
                    assertEquals(if (orientation in 5..8) 64 else 96, bitmap.width)
                    assertEquals(if (orientation in 5..8) 96 else 64, bitmap.height)
                    val actual = bitmap.getPixel(bitmap.width/4, bitmap.height/4)
                    val expected = expectedTopLeft[orientation-1]
                    assertTrue("Orientation $orientation", abs(Color.red(actual)-Color.red(expected))<40 &&
                        abs(Color.green(actual)-Color.green(expected))<40 && abs(Color.blue(actual)-Color.blue(expected))<40)
                } finally { bitmap.recycle() }
            } finally { file.delete(); source.file.delete() }
        }
    }

    @Test fun renderingDoesNotAlterPixelsOutsideCardOrSource() {
        val source = Bitmap.createBitmap(1527,859,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val result = CardRenderer().preview(source, PhotoInfo(mapOf(FieldId.ISO to "100")), CardStyle(), Typeface.MONOSPACE)
        try {
            assertEquals(1527,result.width);assertEquals(859,result.height)
            assertEquals(Color.BLACK,result.getPixel(1200,700))
            assertEquals(Color.BLACK,result.getPixel(1455,820))
            assertEquals(Color.BLACK,source.getPixel(1255,690))
            // Interior above the vertically centered text is the neutral gray backdrop at 60% opacity.
            val gray=result.getPixel(1255,690)
            assertTrue(Color.red(gray) in 52..56);assertEquals(Color.red(gray),Color.green(gray))
        } finally { result.recycle(); source.recycle() }
    }

    @Test fun jpegAndPngExportsHaveOrientedDimensionsAndNoGps() = runBlocking {
        val repository=PhotoRepository(context)
        val original=quadrantPhoto(6)
        val source=repository.import(Uri.fromFile(original))
        try {
            for(format in ExportFormat.entries) {
                val destination=File.createTempFile("result-", ".${format.extension}", context.cacheDir)
                try {
                    PhotoExporter(context,repository).export(source, source.info, CardStyle(), Typeface.MONOSPACE,
                        format, true, Uri.fromFile(destination))
                    val bitmap=requireNotNull(BitmapFactory.decodeFile(destination.absolutePath))
                    try { assertEquals(64,bitmap.width);assertEquals(96,bitmap.height) } finally { bitmap.recycle() }
                    val exif=ExifInterface(destination)
                    assertEquals(1,exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,0))
                    assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
                    assertEquals("TEST CAMERA",exif.getAttribute(ExifInterface.TAG_MODEL))
                } finally { destination.delete() }
            }
        } finally { original.delete();source.file.delete() }
    }

    private fun quadrantPhoto(orientation: Int): File {
        val file=File.createTempFile("source-", ".jpg", context.cacheDir)
        val bitmap=Bitmap.createBitmap(96,64,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap);val paint=Paint()
            for(y in 0..1)for(x in 0..1) {
                paint.color=listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW)[y*2+x]
                canvas.drawRect(x*48f,y*32f,(x+1)*48f,(y+1)*32f,paint)
            }
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,100,it)) }
        } finally { bitmap.recycle() }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION,orientation.toString())
            setAttribute(ExifInterface.TAG_MODEL,"TEST CAMERA")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,"24")
            setAttribute(ExifInterface.TAG_EXPOSURE_TIME,"1/125")
            setLatLong(30.0,120.0)
            saveAttributes()
        }
        return file
    }
}
