package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfileFile
import ing.fuyaoskyrocket.photoinfo.domain.media.*
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import ing.fuyaoskyrocket.photoinfo.domain.metadata.CaptureMakerNote
import ing.fuyaoskyrocket.photoinfo.domain.metadata.PhotographicStyleReader
import ing.fuyaoskyrocket.photoinfo.domain.metadata.AndroidLensMetadata
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile
import ing.fuyaoskyrocket.photoinfo.domain.model.FieldId
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import org.json.JSONObject
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Runs in testAndroidDom, with Android's real JSON/DOM implementation. */
class PortableFormatsTest {
    @Test fun xiaomiPrivatePacketReadsOnlyAnExplicitStyleName() {
        val json = File(root(), "shared/fixtures/xiaomi-style-standard.json").readBytes()
        val packet = XiaomiPhotographicStyleReader.prefix + byteArrayOf(1, 1) + json
        assertEquals("Standard", XiaomiPhotographicStyleReader.name(listOf(packet)))
        assertEquals("Leica Natural", XiaomiPhotographicStyleReader.name(listOf(stylePacket("""{"filterName":"Leica Natural"}"""))))
    }

    @Test fun xiaomiStyleRejectsUnknownNumbersMalformedOrAmbiguousPackets() {
        for (auxiliary in listOf("""{"filterId":66048}""", """{"filterName":66048}""", """{"filterName":"66048"}""",
            """{"filterName":"Style\nname"}""", "invalid", """["Standard"]""")) {
            assertNull(XiaomiPhotographicStyleReader.name(listOf(stylePacket(auxiliary))))
        }
        assertNull(XiaomiPhotographicStyleReader.name(listOf(stylePacket(JSONObject().put("filterName", "Standard")))))
        val complete = stylePacket("""{"filterName":"Standard"}""")
        assertNull(XiaomiPhotographicStyleReader.name(listOf(complete, complete)))
        assertNull(XiaomiPhotographicStyleReader.name(listOf(complete.copyOf(complete.size-1))))
        val multipart = complete.copyOf().apply { this[XiaomiPhotographicStyleReader.prefix.size+1]=2 }
        assertNull(XiaomiPhotographicStyleReader.name(listOf(multipart)))
        assertNull(XiaomiPhotographicStyleReader.name(listOf(XiaomiPhotographicStyleReader.prefix + byteArrayOf(1,1) + ByteArray(65_534))))
        assertNull(XiaomiPhotographicStyleReader.name(listOf(XiaomiPhotographicStyleReader.prefix + byteArrayOf(1,1,-1))))
    }

    @Test fun originalXiaomiPhotoSuppliesStyleWithoutChangingItsBytes() {
        val source = System.getenv("FUYAO_LEICA_SAMPLE")?.let(::File)
            ?: File(root(), "docs/samples/leica-style-20261001/MVIMG_20260928_200322.jpg")
        assumeTrue(source.isFile)
        fun hash() = MessageDigest.getInstance("SHA-256").digest(source.readBytes())
        val before = hash()
        val media = MotionPhoto.inspect(source, "image/jpeg")
        assertFalse(media.blocked)
        assertEquals("Standard", media.photographicStyle)
        val name = PhotographicStyleReader.name(CaptureMakerNote.Facts(), null, media.photographicStyle)
        val card = PhotoInfo(mapOf(FieldId.PHOTOGRAPHIC_STYLE to requireNotNull(name)))
        assertEquals("STYLE: STANDARD", card.displayRows().last().text)
        val profile = LensProfile("sample", "Xiaomi 17 Ultra by Leica", "Telephoto", equivalentMin=100.0,
            equivalentMax=100.0, physicalMin=26.5, physicalMax=26.5, facing="BACK", stylePrefix="Leica")
        val imported = LensProfileFile.decode(LensProfileFile.from(listOf(profile)).encoded()).lenses
        val lens = AndroidLensMetadata.resolve("Xiaomi", profile.exifModel, "", 100.0, profiles=imported, physicalMm=26.5)
        assertEquals("Leica", lens.stylePrefix)
        assertEquals("Leica Standard", PhotographicStyleReader.displayName(name, lens.stylePrefix))
        val ambiguous = AndroidLensMetadata.resolve("Xiaomi", profile.exifModel, "", 100.0,
            profiles=imported+profile.copy(id="other",stylePrefix="Other"), physicalMm=26.5)
        assertEquals("", ambiguous.stylePrefix)
        assertArrayEquals(before, hash())
    }

    private fun stylePacket(auxiliary: Any) = XiaomiPhotographicStyleReader.prefix + byteArrayOf(1,1) +
        JSONObject().put("version", "32").put("889e", auxiliary).toString().toByteArray(Charsets.UTF_8)

    @Test fun hardwareIdsRoundTripWithoutBecomingLocalBindings() {
        val bytes = File(root(), "shared/fixtures/lenses-v1-with-ids.json").readBytes()
        val file = LensProfileFile.decode(bytes)
        assertEquals(listOf("0", "2"), file.lenses.map { it.cameraId })
        assertTrue(file.lenses.all { it.stylePrefix == "Example" })
        assertTrue(file.lenses.all { it.hardwareDevice.isBlank() && it.hardwareModel == "Example|Model" })
        val again = LensProfileFile.decode(file.encoded())
        assertEquals(listOf("0", "2"), again.lenses.map { it.cameraId })
        assertTrue(again.lenses.all { it.hardwareDevice.isBlank() })
        val text = bytes.toString(Charsets.UTF_8)
        for (invalid in listOf(text.replace("\"id\":\"0\"", "\"id\":0"), text.replace("\"id\":\"0\"", "\"id\":\"bad\\nID\""))) {
            assertThrows(Exception::class.java) { LensProfileFile.decode(invalid.toByteArray()) }
        }
        assertThrows(Exception::class.java) {
            LensProfileFile.decode(text.replace("\"stylePrefix\":\"Example\"", "\"stylePrefix\":\""+"A".repeat(65)+"\"").toByteArray())
        }
    }
    private fun root(): File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.take(6)
        .first { File(it, "shared/fixtures/lenses-v1.json").isFile }
    private fun output() = File(root(), ".local/portable-exchange").apply { mkdirs() }

    @Test fun lensFileIsOneLineAndDropsLocalBindings() {
        val file = LensProfileFile.decode(File(root(), "shared/fixtures/lenses-v1.json").readBytes())
        val bytes = file.encoded()
        assertFalse(bytes.contains(10)); assertFalse(bytes.contains(13))
        assertEquals(2, LensProfileFile.decode(bytes).lenses.size)
        assertTrue(file.lenses.all { it.cameraId.isBlank() && it.hardwareDevice.isBlank() })
        val replacement = file.merging(file.lenses)
        assertEquals(2, replacement.size)
        assertEquals(8.0, file.lenses.last().zoomFor(200.0)!!, 0.0001)
        File(output(), "lenses-from-android.json").writeBytes(bytes)
    }

    @Test fun lensDecoderRejectsWrongTypesVersionsRangesAndTrailingData() {
        val value = File(root(), "shared/fixtures/lenses-v1.json").readText()
        for (bad in listOf(value.replace("\"version\":1", "\"version\":2"),
            value.replace("\"equivalentMin\":24", "\"equivalentMin\":\"24\""), value + "extra",
            value.replace("\"equivalentMin\":24", "\"equivalentMin\":-24"))) {
            assertThrows(Exception::class.java) { LensProfileFile.decode(bad.toByteArray()) }
        }
    }

    @Test fun officialLensMegapixelsUseTheSharedFixtureAndRoundTripWithLegacyFiles() {
        val file = LensProfileFile.decode(File(root(), "shared/fixtures/lenses-v1-with-pixels.json").readBytes())
        assertEquals(listOf(50.25, 48.0), file.lenses.map { it.originalMegapixels })
        assertEquals(listOf(50.25, 48.0), LensProfileFile.decode(file.encoded()).lenses.map { it.originalMegapixels })
        val legacy = LensProfileFile.decode(File(root(), "shared/fixtures/lenses-v1.json").readBytes())
        assertTrue(legacy.lenses.all { it.originalMegapixels == null })
        val nullable = JSONObject(file.encoded().toString(Charsets.UTF_8)).apply {
            getJSONArray("lenses").getJSONObject(0).put("originalMegapixels", JSONObject.NULL)
        }
        assertNull(LensProfileFile.decode(nullable.toString().toByteArray()).lenses.first().originalMegapixels)
    }

    @Test fun originalMegapixelJsonRejectsCoercionAndInvalidBounds() {
        val text = File(root(), "shared/fixtures/lenses-v1-with-pixels.json").readText()
        for (invalid in listOf("\"50.25\"", "true", "false", "0", "-1", "1000.01", "1e309")) {
            val changed = text.replace("\"originalMegapixels\":50.25", "\"originalMegapixels\":$invalid")
            assertNotEquals(text, changed)
            assertThrows(Exception::class.java) { LensProfileFile.decode(changed.toByteArray()) }
        }
    }

    @Test fun packageStreamsRoundTripAndRejectsCorruption() {
        val directory = kotlin.io.path.createTempDirectory("fuyao-package-test").toFile()
        try {
            val photo = File(directory, "photo.jpg").apply { writeBytes(byteArrayOf(-1, -40, -1, -39)) }
            val movie = File(directory, "video.mov").apply { writeBytes(ByteArray(200000) { it.toByte() }) }
            val archive = File(directory, "test.fuyaophotos")
            FuyaoPhotosPackage.write(photo, movie, "00112233-4455-6677-8899-AABBCCDDEEFF", 1000000, archive)
            val extracted = File(directory, "read").apply { mkdir() }
            val read = FuyaoPhotosPackage.read(archive, extracted)
            assertArrayEquals(photo.readBytes(), read.photo.readBytes())
            assertArrayEquals(movie.readBytes(), read.movie.readBytes())
            val bytes = archive.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); archive.writeBytes(bytes)
            val bad = File(directory, "bad").apply { mkdir() }
            assertThrows(Exception::class.java) { FuyaoPhotosPackage.read(archive, bad) }
            assertEquals(0, bad.listFiles()!!.size)
        } finally { directory.deleteRecursively() }
    }

    @Test fun androidProducesAnApplePairFromTheRealMotionSample() {
        val source = File(root(), "docs/samples/xiaomi-hdr-live-plain-20260924-122908.jpg")
        assumeTrue(source.isFile)
        val work = kotlin.io.path.createTempDirectory("fuyao-real-pair").toFile()
        try {
            val media = MotionPhoto.inspect(source, "image/jpeg")
            val motion = requireNotNull(media.motion)
            val mp4 = File(work, "source.mp4"); val movie = File(work, "video.mov"); val photo = File(work, "photo.jpg")
            VideoMetadata.copy(source, motion.offset, motion.length, mp4, ExportOptions(keepLocation = true))
            val id = "00112233-4455-6677-8899-AABBCCDDEEFF"
            val cover = AppleLivePhotoMovie.write(mp4, movie, id, motion.timestampUs)
            val still = File(work, "still-source.jpg")
            still.outputStream().use { JpegContainer.copyRange(source, 0, motion.offset, it) }
            val layout = JpegContainer.inspect(still)
            val note = ApplePhotoMetadata.withAppleNotes(JpegContainer.exif(layout).singleOrNull(), id, false, null)
            JpegContainer.rewrite(still, photo, listOf(note), null)
            FuyaoPhotosPackage.write(photo, movie, id, cover, File(output(), "from-android.fuyaophotos"))
            val roundtrip = File(work, "reopened.mov")
            AppleLivePhotoMovie.write(movie, roundtrip, id, cover)
            FuyaoPhotosPackage.write(photo, roundtrip, id, cover, File(output(), "from-android-reopened.fuyaophotos"))
            val base = HeifImageContainer.read(requireNotNull(javaClass.getResourceAsStream("/media/base.heic")).use { it.readBytes() })
            val exifID = base.items.maxOf { it.id } + 1
            val heic = File(work, "photo.heic")
            base.copy(items = base.items + HeifImageContainer.Item(exifID, "Exif", byteArrayOf(0),
                IsoBmff.data { writeInt(6); write(ApplePhotoMetadata.withIdentifier(null, id)) }),
                references = base.references + HeifImageContainer.Reference("cdsc", exifID, listOf(base.primary))).write(heic)
            FuyaoPhotosPackage.write(heic, movie, id, cover, File(output(), "from-android-heic.fuyaophotos"))
            for (filename in listOf("from-android.fuyaophotos", "from-android-heic.fuyaophotos")) {
                val decoded = File(work, filename).apply { mkdir() }
                val contents = FuyaoPhotosPackage.read(File(output(), filename), decoded)
                val assembled = File(decoded, "reopened.photo")
                FuyaoPackageMedia.assemble(contents, assembled)
                assertTrue(assembled.length() > contents.movie.length())
            }
        } finally { work.deleteRecursively() }
    }

    @Test fun opensAppleWrittenPackagesAndLensFiles() {
        val archive = File(output(), "from-apple.fuyaophotos")
        assumeTrue(archive.isFile)
        val work = kotlin.io.path.createTempDirectory("fuyao-apple-pair").toFile()
        try {
            val contents = FuyaoPhotosPackage.read(archive, work)
            assertTrue(contents.photo.length() > 0 && contents.movie.length() > 0)
            MotionPhoto.validateVideo(contents.movie, 0, contents.movie.length())
            val lenses = LensProfileFile.decode(File(output(), "lenses-from-apple.json").readBytes())
            assertEquals(2, lenses.lenses.size)
        } finally { work.deleteRecursively() }
    }
}
