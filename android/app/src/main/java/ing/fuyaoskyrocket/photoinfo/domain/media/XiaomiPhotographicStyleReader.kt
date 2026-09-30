package ing.fuyaoskyrocket.photoinfo.domain.media

import ing.fuyaoskyrocket.photoinfo.domain.metadata.PhotographicStyleReader

object XiaomiPhotographicStyleReader {
    val prefix = "XIAOMI_CUSTOMIZE\u0000".toByteArray(Charsets.US_ASCII)

    fun name(packets: List<ByteArray>): String? = runCatching {
        val packet = packets.filter { JpegContainer.starts(it, prefix) }.singleOrNull() ?: return null
        require(packet.size in (prefix.size + 3)..65_533 && packet[prefix.size] == 1.toByte() && packet[prefix.size + 1] == 1.toByte())
        // ASVS 1.5.2/2.2.1: bounded UTF-8 dictionaries; numeric filter IDs are not style names.
        val fields = PortableJson.objectFrom(packet.copyOfRange(prefix.size + 2, packet.size), 65_533)
        val auxiliary = PortableJson.string(fields, "889e").toByteArray(Charsets.UTF_8)
        val values = PortableJson.objectFrom(auxiliary, 65_533)
        PhotographicStyleReader.readableName(PortableJson.string(values, "filterName"))
    }.getOrNull()
}
