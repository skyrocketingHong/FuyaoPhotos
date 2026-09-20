package ing.fuyaoskyrocket.photoinfo.data.settings

import android.content.Context
import androidx.core.content.edit
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("editor", Context.MODE_PRIVATE)
    fun read() = EditorSettings(
        // Preserve the photographer saved by versions before the dedicated settings screen.
        defaultAuthor = preferences.getString("defaultAuthor", null) ?: preferences.getString("author", "").orEmpty(),
        resolvePhotoLocation = preferences.getBoolean("resolvePhotoLocation", true),
        fallbackMainFocal = preferences.getString("fallbackMainFocal", "").orEmpty(),
    )
    fun save(settings: EditorSettings) {
        require(settings.validFocal && settings.defaultAuthor.length <= 512)
        preferences.edit {
            putString("defaultAuthor", settings.defaultAuthor.trim())
            putBoolean("resolvePhotoLocation", settings.resolvePhotoLocation)
            putString("fallbackMainFocal", settings.fallbackMainFocal.trim())
            remove("author")
        }
    }
}
