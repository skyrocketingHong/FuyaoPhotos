package ing.fuyaoskyrocket.photoinfo.data.camera

import android.os.Build
import ing.fuyaoskyrocket.photoinfo.domain.metadata.MetadataFormatting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object LocalCameraDevice {
    @Volatile private var cachedName: String? = null
    // Model-scoped Camera2 namespace; never an Android ID, IMEI or serial number.
    val hardwareKey: String get() = listOf(Build.MANUFACTURER, Build.MODEL, Build.DEVICE).joinToString("|")

    suspend fun productName(): String = cachedName ?: withContext(Dispatchers.IO) {
        // Some vendors expose a marketing name separately from Build.MODEL.
        // Fixed read-only property keys: no shell interpolation or user-supplied commands.
        val name = listOf("ro.product.marketname", "ro.product.vendor.marketname")
            .firstNotNullOfOrNull(::productProperty)
            ?: MetadataFormatting.device(Build.MANUFACTURER, Build.MODEL)
        cachedName = name
        name
    }
    private fun productProperty(key: String): String? = runCatching {
        val process = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(250, TimeUnit.MILLISECONDS) || process.exitValue() != 0) return@runCatching null
            process.inputStream.bufferedReader().use { it.readLine()?.trim() }
                ?.takeIf { it.isNotBlank() && it.length <= 256 && !it.equals("unknown", true) }
        } finally {
            process.destroy()
            process.inputStream.close(); process.errorStream.close(); process.outputStream.close()
        }
    }.getOrNull()
}
