package ing.fuyaoskyrocket.photoinfo.data.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensBindings
import ing.fuyaoskyrocket.photoinfo.data.camera.LocalCameraDevice
import androidx.core.content.edit
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import ing.fuyaoskyrocket.photoinfo.domain.model.WorkspaceSettings
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoSharing
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature
import ing.fuyaoskyrocket.photoinfo.domain.model.StartPage

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("editor", Context.MODE_PRIVATE)
    fun read() = EditorSettings(
        // Preserve the photographer saved by versions before the dedicated settings screen.
        defaultAuthor = preferences.getString("defaultAuthor", null) ?: preferences.getString("author", "").orEmpty(),
        resolvePhotoLocation = preferences.getBoolean("resolvePhotoLocation", true),
        fallbackMainFocal = preferences.getString("fallbackMainFocal", "").orEmpty(),
        lenses = readLenses(),
        metadataSharesCards = preferences.getBoolean("metadata.sharesCards", false),
        workspace = WorkspaceSettings(
            startPage = StartPage.entries.firstOrNull { it.name == preferences.getString("workspace.startup", null) } ?: StartPage.EDITOR,
            sharing = PhotoSharing.entries.firstOrNull { it.name == preferences.getString("workspace.sharing", null) }
                ?: if (preferences.getBoolean("metadata.sharesCards", false)) PhotoSharing.PARTIAL else PhotoSharing.INDEPENDENT,
            sharedFeatures = preferences.getStringSet("workspace.sharedTabs", setOf("CARDS", "METADATA")).orEmpty()
                .mapNotNull { raw -> PhotoFeature.entries.firstOrNull { it.name == raw } }.toSet()),
        hevcEncoder = if (preferences.getString("export.hevcEncoder", "x265") == "platform")
            ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.PLATFORM
        else ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.X265,
        exportDefaults = ExportOptions.restore(listOf(
            preferences.getString("export.format", "JPEG").orEmpty(),
            preferences.getInt("export.quality", 100).toString(),
            preferences.getBoolean("export.exif", true).toString(),
            preferences.getBoolean("export.location", false).toString(),
            preferences.getBoolean("export.time", true).toString(),
            preferences.getBoolean("export.livePair", false).toString(),
            preferences.getBoolean("export.applePortrait", false).toString(),
            preferences.getBoolean("export.appleStyle", false).toString(),
            preferences.getBoolean("export.appleStyle3", false).toString())),
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
                digitalZoomMax=optional("digitalZoomMax"), facing=j.optString("facing"), hardwareModel=j.optString("hardwareModel"), stylePrefix=j.optString("stylePrefix"),
                originalMegapixels=optional("originalMegapixels"))
        }.filter { it.valid() }
    }.getOrDefault(emptyList())

    private fun encodeLenses(lenses: List<LensProfile>): String = JSONArray().apply {
        lenses.forEach { lens -> put(JSONObject().apply {
            put("id", lens.id); put("device",lens.device); put("name",lens.name); put("cameraId",lens.cameraId)
            put("equivalentMin",lens.equivalentMin); put("equivalentMax",lens.equivalentMax)
            put("zoomMin",lens.zoomMin ?: JSONObject.NULL); put("zoomMax",lens.zoomMax ?: JSONObject.NULL)
            put("physicalMin",lens.physicalMin ?: JSONObject.NULL); put("physicalMax",lens.physicalMax ?: JSONObject.NULL)
            put("exifModel",lens.exifModel); put("hardwareDevice",lens.hardwareDevice)
            put("hardwareModel",lens.hardwareModel)
            put("stylePrefix",lens.stylePrefix)
            put("originalMegapixels",lens.originalMegapixels ?: JSONObject.NULL)
            put("digitalZoomMax",lens.digitalZoomMax ?: JSONObject.NULL); put("facing",lens.facing)
        }) }
    }.toString()

    fun save(settings: EditorSettings) {
        require(settings.validFocal && settings.defaultAuthor.length <= 512)
        require(settings.lenses.size <= 64 && settings.lenses.all { it.valid() })
        require(!LensBindings.hasDuplicates(settings.lenses))
        val defaults = settings.exportDefaults.sanitized()
        preferences.edit {
            putString("workspace.startup", settings.workspace.startPage.name)
            putString("workspace.sharing", settings.workspace.sharing.name)
            putStringSet("workspace.sharedTabs", settings.workspace.sharedFeatures.map { it.name }.toSet())
            putString("export.format", defaults.format.name)
            putInt("export.quality", defaults.jpegQuality)
            putBoolean("export.exif", defaults.keepExif)
            putBoolean("export.location", defaults.keepLocation)
            putBoolean("export.time", defaults.keepCaptureTime)
            putBoolean("export.livePair", defaults.separateLivePhoto)
            putBoolean("export.applePortrait", defaults.applePortrait)
            putBoolean("export.appleStyle", defaults.appleStyle)
            putBoolean("export.appleStyle3", defaults.appleStyle3)
            putString("lenses", encodeLenses(settings.lenses))
            putString("export.hevcEncoder", if (settings.hevcEncoder == ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.PLATFORM) "platform" else "x265")
            putString("defaultAuthor", settings.defaultAuthor.trim())
            putBoolean("resolvePhotoLocation", settings.resolvePhotoLocation)
            putString("fallbackMainFocal", settings.fallbackMainFocal.trim())
            putBoolean("metadata.sharesCards", settings.metadataSharesCards)
            remove("author")
        }
    }
}
