package ing.fuyaoskyrocket.photoinfo.data.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile
import androidx.core.content.edit
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("editor", Context.MODE_PRIVATE)
    fun read() = EditorSettings(
        // Preserve the photographer saved by versions before the dedicated settings screen.
        defaultAuthor = preferences.getString("defaultAuthor", null) ?: preferences.getString("author", "").orEmpty(),
        resolvePhotoLocation = preferences.getBoolean("resolvePhotoLocation", true),
        fallbackMainFocal = preferences.getString("fallbackMainFocal", "").orEmpty(),
        lenses = readLenses(),
    )
    private fun readLenses(): List<LensProfile> = runCatching {
        val array = JSONArray(preferences.getString("lenses", "[]"))
        require(array.length() <= 64)
        (0 until array.length()).map { index ->
            val j = array.getJSONObject(index)
            fun optional(key: String) = if (j.isNull(key)) null else j.getDouble(key)
            LensProfile(j.getString("id"), j.getString("device"), j.getString("name"), j.optString("cameraId"),
                j.getDouble("equivalentMin"), j.getDouble("equivalentMax"), optional("zoomMin"), optional("zoomMax"), optional("physicalMin"), optional("physicalMax"))
        }.filter { it.valid() }
    }.getOrDefault(emptyList())

    private fun encodeLenses(lenses: List<LensProfile>): String = JSONArray().apply {
        lenses.forEach { lens -> put(JSONObject().apply {
            put("id", lens.id); put("device",lens.device); put("name",lens.name); put("cameraId",lens.cameraId)
            put("equivalentMin",lens.equivalentMin); put("equivalentMax",lens.equivalentMax)
            put("zoomMin",lens.zoomMin ?: JSONObject.NULL); put("zoomMax",lens.zoomMax ?: JSONObject.NULL)
            put("physicalMin",lens.physicalMin ?: JSONObject.NULL); put("physicalMax",lens.physicalMax ?: JSONObject.NULL)
        }) }
    }.toString()

    fun save(settings: EditorSettings) {
        require(settings.validFocal && settings.defaultAuthor.length <= 512)
        require(settings.lenses.size <= 64 && settings.lenses.all { it.valid() })
        preferences.edit {
            putString("lenses", encodeLenses(settings.lenses))
            putString("defaultAuthor", settings.defaultAuthor.trim())
            putBoolean("resolvePhotoLocation", settings.resolvePhotoLocation)
            putString("fallbackMainFocal", settings.fallbackMainFocal.trim())
            remove("author")
        }
    }
}
