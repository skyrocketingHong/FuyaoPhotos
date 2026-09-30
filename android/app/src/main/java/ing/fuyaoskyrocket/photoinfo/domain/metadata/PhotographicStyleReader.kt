package ing.fuyaoskyrocket.photoinfo.domain.metadata

import ing.fuyaoskyrocket.photoinfo.domain.media.MotionPhoto

object PhotographicStyleReader {
    private val nameKeys = setOf("PhotographicStyle", "PhotographicStyleName", "SmartStyleName", "CameraStyle", "LeicaStyle", "LeicaColorMode")

    fun name(makerNote: CaptureMakerNote.Facts, xmp: String?): String? {
        val names = if (xmp != null && xmp.length <= 1_048_576) runCatching {
            val root = MotionPhoto.parseXmp(xmp).documentElement
            val found = mutableSetOf<String>()
            fun visit(node: org.w3c.dom.Node, depth: Int) {
                require(depth <= 32)
                if (node.localName in nameKeys) readableName(node.textContent)?.let(found::add)
                node.attributes?.let { attributes ->
                    for (index in 0 until attributes.length) {
                        val attribute = attributes.item(index)
                        if (attribute.localName in nameKeys) readableName(attribute.nodeValue)?.let(found::add)
                    }
                }
                for (index in 0 until node.childNodes.length) visit(node.childNodes.item(index), depth + 1)
            }
            visit(root, 0)
            found
        }.getOrDefault(emptySet()) else emptySet()
        return when (names.size) {
            0 -> makerNote.photographicStyle
            1 -> names.single()
            else -> null
        }
    }

    internal fun modernName(values: Map<String, Any>): String? {
        readableName(values["Preset"] as? String)?.let { return it }
        val neutral = mapOf("0" to 1.0, "1" to 0.0, "2" to 0.0, "3" to 1.0, "4" to 1.0, "5" to 1.0,
            "6" to 4.0, "7" to 0.0, "8" to 1.0, "9" to 1.0, "10" to 0.0, "11" to 0.0, "12" to 1.0)
        if ((0..7).any { it.toString() !in values }) return null
        return "Standard".takeIf {
            values.all { (key, value) ->
                val number = when (value) { is Number -> value.toDouble(); is Boolean -> if (value) 1.0 else 0.0; else -> null }
                number != null && number == neutral[key]
            }
        }
    }

    internal fun readableName(value: String?): String? = value?.trim()?.takeIf {
        it.length in 1..128 && it.any(Char::isLetter) && it.none(Character::isISOControl)
    }
}
