package ing.fuyaoskyrocket.photoinfo.domain.lens

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import ing.fuyaoskyrocket.photoinfo.domain.media.PortableJson

/** The exchange document contains one EXIF model; Camera2 bindings stay on their own device. */
data class LensProfileFile(val device: String, val exifModel: String, val lenses: List<LensProfile>) {
    fun encoded(): ByteArray {
        validate()
        return JSONObject().apply {
            put("format", FORMAT); put("version", 1); put("device", device); put("exifModel", exifModel)
            put("lenses", JSONArray().apply {
                lenses.forEach { lens -> put(JSONObject().apply {
                    put("name", lens.name); put("facing", lens.facing.lowercase(Locale.ROOT).ifBlank { "unspecified" })
                    put("equivalentMin", lens.equivalentMin); put("equivalentMax", lens.equivalentMax)
                    lens.physicalMin?.let { put("physicalMin", it) }; lens.physicalMax?.let { put("physicalMax", it) }
                    lens.zoomMin?.let { put("zoomMin", it) }; lens.zoomMax?.let { put("zoomMax", it) }
                    lens.digitalZoomMax?.let { put("digitalZoomMax", it) }
                }) }
            })
        }.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    }

    fun merging(existing: List<LensProfile>): List<LensProfile> {
        validate()
        return (existing.filterNot { it.acceptsExif(exifModel) } + lenses).also { require(it.size <= 64) }
    }

    fun filename() = device.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().take(80)
        .ifBlank { "Camera" } + ".json"

    private fun validate() {
        require(text(device) && text(exifModel) && lenses.size in 1..64)
        require(lenses.all { it.valid() && text(it.name) && it.device == device && it.acceptsExif(exifModel) })
    }

    companion object {
        const val FORMAT = "fuyaophotos.lenses"
        const val MAX_BYTES = 128 * 1024
        fun from(profiles: List<LensProfile>): LensProfileFile {
            val first = profiles.first()
            return LensProfileFile(first.device, first.exifModel, profiles).also { it.validate() }
        }
        fun decode(bytes: ByteArray): LensProfileFile {
            // ASVS 1.5.3 / 2.2.1: use the same UTF-8 schema and bounds as the Apple decoder.
            require(bytes.size in 1..MAX_BYTES)
            val value = PortableJson.objectFrom(bytes, MAX_BYTES)
            require(PortableJson.string(value, "format") == FORMAT && value.get("version") is Number && value.getDouble("version") == 1.0)
            val device = PortableJson.string(value, "device"); val model = PortableJson.string(value, "exifModel")
            val items = value.getJSONArray("lenses")
            require(items.length() in 1..64)
            fun number(item: JSONObject, key: String): Double {
                require(item.get(key) is Number)
                return item.getDouble(key).also { require(it.isFinite()) }
            }
            fun optional(item: JSONObject, key: String) = if (item.isNull(key)) null else number(item, key)
            val profiles = (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val direction = PortableJson.string(item, "facing")
                require(direction in setOf("unspecified", "back", "front", "external"))
                LensProfile(id = UUID.randomUUID().toString(), device = device, exifModel = model,
                    name = PortableJson.string(item, "name"), facing = if (direction == "unspecified") "" else direction.uppercase(Locale.ROOT),
                    equivalentMin = number(item, "equivalentMin"), equivalentMax = number(item, "equivalentMax"),
                    physicalMin = optional(item, "physicalMin"), physicalMax = optional(item, "physicalMax"),
                    zoomMin = optional(item, "zoomMin"), zoomMax = optional(item, "zoomMax"),
                    digitalZoomMax = optional(item, "digitalZoomMax"))
            }
            return LensProfileFile(device, model, profiles).also { it.validate() }
        }
        private fun text(value: String) = value.isNotBlank() && value.length <= 256 && value.none(Char::isISOControl)
    }
}
