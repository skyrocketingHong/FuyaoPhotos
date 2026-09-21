package ing.fuyaoskyrocket.photoinfo.data.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile
import ing.fuyaoskyrocket.photoinfo.data.camera.LocalCameraDevice
import androidx.core.content.edit
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("editor", Context.MODE_PRIVATE)
    fun read() = EditorSettings(
        // Preserve the photographer saved by versions before the dedicated settings screen.
        defaultAuthor = preferences.getString("defaultAuthor", null) ?: preferences.getString("author", "").orEmpty(),
        resolvePhotoLocation = preferences.getBoolean("resolvePhotoLocation", true),
        fallbackMainFocal = preferences.getString("fallbackMainFocal", "").orEmpty(),
        lenses = readLenses(),
        exportDefaults = ExportOptions.restore(listOf(
            preferences.getString("export.format", "JPEG").orEmpty(),
            preferences.getInt("export.quality", 100).toString(),
            preferences.getBoolean("export.exif", true).toString(),
            preferences.getBoolean("export.location", false).toString(),
            preferences.getBoolean("export.time", true).toString())),
    )
    private fun readLenses(): List<LensProfile> = runCatching {
        val array = JSONArray(preferences.getString("lenses", "[]"))
        require(array.length() <= 64)
        (0 until array.length()).map { index ->
            val j = array.getJSONObject(index)
            fun optional(key: String) = if (j.isNull(key)) null else j.getDouble(key)
            val lens = LensProfile(j.getString("id"), j.getString("device"), j.getString("name"), j.optString("cameraId"),
                j.getDouble("equivalentMin"), j.getDouble("equivalentMax"), optional("zoomMin"), optional("zoomMax"), optional("physicalMin"), optional("physicalMax"))
            if (!j.has("exifModel")) lens.upgradeLegacy(LocalCameraDevice.hardwareKey)
            else lens.copy(exifModel=j.getString("exifModel"), hardwareDevice=j.optString("hardwareDevice"),
                digitalZoomMax=optional("digitalZoomMax"), facing=j.optString("facing"))
        }.filter { it.valid() }
    }.getOrDefault(emptyList())

    private fun encodeLenses(lenses: List<LensProfile>): String = JSONArray().apply {
        lenses.forEach { lens -> put(JSONObject().apply {
            put("id", lens.id); put("device",lens.device); put("name",lens.name); put("cameraId",lens.cameraId)
            put("equivalentMin",lens.equivalentMin); put("equivalentMax",lens.equivalentMax)
            put("zoomMin",lens.zoomMin ?: JSONObject.NULL); put("zoomMax",lens.zoomMax ?: JSONObject.NULL)
            put("physicalMin",lens.physicalMin ?: JSONObject.NULL); put("physicalMax",lens.physicalMax ?: JSONObject.NULL)
            put("exifModel",lens.exifModel); put("hardwareDevice",lens.hardwareDevice)
            put("digitalZoomMax",lens.digitalZoomMax ?: JSONObject.NULL); put("facing",lens.facing)
        }) }
    }.toString()

    fun save(settings: EditorSettings) {
        require(settings.validFocal && settings.defaultAuthor.length <= 512)
        require(settings.lenses.size <= 64 && settings.lenses.all { it.valid() })
        val defaults = settings.exportDefaults.sanitized()
        preferences.edit {
            putString("export.format", defaults.format.name)
            putInt("export.quality", defaults.jpegQuality)
            putBoolean("export.exif", defaults.keepExif)
            putBoolean("export.location", defaults.keepLocation)
            putBoolean("export.time", defaults.keepCaptureTime)
            putString("lenses", encodeLenses(settings.lenses))
            putString("defaultAuthor", settings.defaultAuthor.trim())
            putBoolean("resolvePhotoLocation", settings.resolvePhotoLocation)
            putString("fallbackMainFocal", settings.fallbackMainFocal.trim())
            remove("author")
        }
    }
}
