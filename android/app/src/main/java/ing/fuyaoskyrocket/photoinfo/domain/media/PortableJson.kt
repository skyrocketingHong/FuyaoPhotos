package ing.fuyaoskyrocket.photoinfo.domain.media

import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader

internal object PortableJson {
    fun objectFrom(bytes: ByteArray, maximum: Int): JSONObject {
        require(bytes.size in 1..maximum)
        val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        var depth = 0; var quoted = false; var escaped = false
        for (c in text) {
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 8) }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted)
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            fun value() {
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject(); val names = mutableSetOf<String>()
                        while (reader.hasNext()) { require(names.add(reader.nextName())); value() }
                        reader.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) value(); reader.endArray() }
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    else -> error("Invalid JSON value")
                }
            }
            value(); require(reader.peek() == JsonToken.END_DOCUMENT)
        }
        val parser = JSONTokener(text)
        val result = parser.nextValue()
        require(result is JSONObject && parser.nextClean() == '\u0000')
        return result
    }
    fun string(value: JSONObject, key: String): String {
        require(value.get(key) is String)
        return value.getString(key)
    }
}
