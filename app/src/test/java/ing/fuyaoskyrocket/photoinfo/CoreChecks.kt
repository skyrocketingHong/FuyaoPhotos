package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.layout.CardLayoutEngine
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardOverflowException
import ing.fuyaoskyrocket.photoinfo.domain.metadata.MetadataFormatting as Format
import ing.fuyaoskyrocket.photoinfo.domain.metadata.AndroidLensMetadata
import ing.fuyaoskyrocket.photoinfo.domain.metadata.LocationFormatting
import ing.fuyaoskyrocket.photoinfo.domain.metadata.PhotoCoordinates
import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.domain.render.BoxBlur
import kotlin.math.abs
import kotlin.random.Random

/** The same framework-free checks run through JUnit and scripts/test-core.sh. */
object CoreChecks {
    fun run(log: (String) -> Unit = {}): Int {
        var total = 0
        fun verify(name: String, test: () -> Unit) { test(); total++; log("PASS $name") }
        fun near(actual: Float, expected: Float) { check(abs(actual - expected) < .02f) { "$actual != $expected" } }
        fun layout(w: Int, h: Int, info: PhotoInfo, style: CardStyle = CardStyle()) =
            CardLayoutEngine.layout(w, h, info, style) { it.codePointCount(0, it.length) * 6.3f }
        val all = PhotoInfo(mapOf(
            FieldId.DEVICE to "iPhone 16 Pro", FieldId.AUTHOR to "A. Lee", FieldId.LOCATION to "Hangzhou, China",
            FieldId.CAMERA to "Main", FieldId.IMAGE_SIZE to "24MP", FieldId.FOCAL_LENGTH to "24 MM (1X)",
            FieldId.EXPOSURE to "1/121", FieldId.APERTURE to "1.48", FieldId.ISO to "400",
        ))
        verify("empty fields are omitted") { check(PhotoInfo().displayRows().isEmpty()); check(layout(1527, 859, PhotoInfo()) == null) }
        verify("field order and 3 accent / 6 white rows") {
            val rows = all.displayRows(); check(rows.size == 9)
            check(rows.take(3).all { it.accent }); check(rows.drop(3).none { it.accent })
            check(rows[0].text == "iPHONE 16 PRO"); check(rows[1].text == "SHOT BY: A. LEE"); check(rows.last().text == "ISO: 400")
        }
        verify("blank fields are not replaced by example metadata") {
            check(all.with(FieldId.ISO,"  ").displayRows().none { it.text.startsWith("ISO:") })
            check(PhotoInfo(mapOf(FieldId.CAMERA to "Lens")).displayRows().single().text == "CAMERA: LENS")
        }
        verify("device model does not duplicate manufacturer") {
            check(Format.device("Apple", "iPhone 16 Pro") == "iPHONE 16 Pro")
            check(Format.device("Canon", "Canon EOS R5") == "Canon EOS R5")
            check(Format.device("Sony", "ILCE-7M4") == "Sony ILCE-7M4")
            check(Format.device(null, null).isEmpty())
        }
        verify("actual megapixel count and overflow-safe multiplication") {
            check(Format.megapixels(6000,4000) == "24MP"); check(Format.megapixels(4000,3000) == "12MP")
            check(Format.megapixels(1,1) == "0.000001MP")
            check(Format.megapixels(65536,65536) == "4295MP"); check(Format.megapixels(0,100).isEmpty())
        }
        verify("exposure reciprocal and long exposure formatting") {
            check(Format.exposure(1.0/121) == "1/121"); check(Format.exposure(30.0) == "30SEC")
            check(Format.exposure(1e-30) != "0SEC")
            check(Format.exposure(.3) == "0.3SEC"); check(Format.exposure(1.5) == "1.5SEC")
        }
        verify("invalid numeric metadata stays absent") {
            for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
                check(Format.number(value).isEmpty()); check(Format.exposure(value).isEmpty()); check(Format.equivalentFocalLength(value).isEmpty())
            }
        }
        verify("focal formatting never invents zoom") { check(Format.equivalentFocalLength(24.0) == "24 MM") }
        verify("Xiaomi 17 Ultra 75mm uses the supplied 3.2x camera convention") {
            val lens = AndroidLensMetadata.resolve("Xiaomi", "Xiaomi 17 Ultra by Leica", "", 75.0)
            check(lens.focalLength == "75 MM (3.2X)")
            check(lens.camera == "LEICA TELEPHOTO")
        }
        verify("known main and ultrawide lenses have matching magnifications") {
            check(AndroidLensMetadata.resolve("Xiaomi", "14", "", 23.0).focalLength == "23 MM (1X)")
            check(AndroidLensMetadata.resolve("Xiaomi", "14", "", 14.0).focalLength == "14 MM (0.6X)")
            check(AndroidLensMetadata.resolve("Xiaomi", "17 Ultra", "", 100.0).focalLength == "100 MM (4.3X)")
            check(AndroidLensMetadata.resolve("Xiaomi", "17 Ultra", "", 21.0).focalLength == "21 MM")
            check(AndroidLensMetadata.resolve("Xiaomi", "17 Ultra", "Front camera", 23.0).camera == "Front camera")
            check(AndroidLensMetadata.resolve("Xiaomi", "17 Ultra", "Front camera", 23.0, 24.0).focalLength == "23 MM")
        }
        verify("unknown devices do not inherit Xiaomi lens mappings") {
            val lens = AndroidLensMetadata.resolve("Samsung", "UNKNOWN", "", 75.0)
            check(lens.focalLength == "75 MM" && lens.camera.isEmpty())
            check(AndroidLensMetadata.resolve("Xiaomi", "UNKNOWN", "", 75.0).focalLength == "75 MM")
        }
        verify("unknown device calibration and explicit lens zoom") {
            check(AndroidLensMetadata.resolve("Brand", "Device", "", 72.0, 24.0).focalLength == "72 MM (3X)")
            val lens = AndroidLensMetadata.resolve("Brand", "Device", "TELEPHOTO (3.2x)", 75.0)
            check(lens.focalLength == "75 MM (3.2X)" && lens.camera == "TELEPHOTO (3.2x)")
            check(AndroidLensMetadata.resolve("Brand", "Device", "", 75.0, Double.NaN).focalLength == "75 MM")
        }
        verify("missing equivalent focal length stays empty") {
            check(AndroidLensMetadata.resolve("Xiaomi", "17 Ultra", "Existing lens", 0.0).focalLength.isEmpty())
            check(AndroidLensMetadata.resolve("Xiaomi", "17 Ultra", "Existing lens", 0.0).camera == "Existing lens")
        }
        verify("reference place format uses city and English country") {
            check(LocationFormatting.place("Hangzhou", null, "Zhejiang", "中国", "CN") == "Hangzhou, China")
            check(LocationFormatting.place("Paris", null, null, "France", "FR") == "Paris, France")
            check(LocationFormatting.place("Singapore", null, null, "Singapore", "SG") == "Singapore")
        }
        verify("place fallback omits empty fields and duplicates") {
            check(LocationFormatting.place(null, "County", "Region", "Country", null) == "County, Country")
            check(LocationFormatting.place(null, null, "Region", null, null) == "Region")
            check(LocationFormatting.place(null, null, null, null, null).isEmpty())
        }
        verify("EXIF coordinates are validated without rejecting the equator") {
            check(PhotoCoordinates.from(0.0, 0.0) != null)
            check(PhotoCoordinates.from(-45.0, -120.0) != null)
            check(PhotoCoordinates.from(91.0, 0.0) == null)
            check(PhotoCoordinates.from(0.0, Double.NaN) == null)
        }
        verify("default photographer only fills missing EXIF") {
            val settings = EditorSettings(defaultAuthor = " Default ")
            check(settings.authorFor("") == "Default")
            check(settings.authorFor("Photo author") == "Photo author")
        }
        verify("unknown lens calibration accepts only bounded positive values") {
            check(EditorSettings(fallbackMainFocal = "").validFocal)
            check(EditorSettings(fallbackMainFocal = "23.4375").mainFocalMm == 23.4375)
            for (invalid in listOf("0", "201", "NaN", "Infinity", "abc")) {
                check(!EditorSettings(fallbackMainFocal = invalid).validFocal)
            }
        }
        verify("reference geometry exactly matches 1527x859") {
            val x = requireNotNull(layout(1527,859,all))
            near(x.box.width,215f); near(x.box.height,168f); near(x.box.right,1450f); near(x.box.bottom,824f)
            near(x.radius,20f); near(x.fontSize,10.5f); near(x.lineHeight,12.5f); near(x.blurRadius,25f)
            check(x.lines.size == 9); x.lines.forEach { near(it.x,x.box.left+19f) }
            near(x.lines[3].top-x.lines[2].top,19.5f)
            near(x.lines.first().top-x.box.top,(168f-119.5f)/2)
        }
        verify("4K uses short-edge proportional scaling") {
            val x = requireNotNull(layout(3840,2160,all));val scale=2160f/859
            near(x.box.width,215f*scale);near(x.box.height,168f*scale)
            near(3840f-x.box.right,77f*scale);near(2160f-x.box.bottom,35f*scale);near(x.fontSize,10.5f*scale)
        }
        verify("portrait does not use 14 percent width") {
            val x=requireNotNull(layout(1080,1920,all));near(x.box.width,215f*1080/859)
            check(x.box.width/1080>.24f)
        }
        verify("4080x3072 text scales with photo resolution") {
            val x = requireNotNull(layout(4080, 3072, all))
            near(x.fontSize, 10.5f * 3072 / 859)
            val preview = requireNotNull(layout(2040, 1536, all))
            near(x.fontSize, preview.fontSize * 2)
        }
        verify("independent text size preserves card width and margins") {
            val info = PhotoInfo(mapOf(FieldId.DEVICE to "CAMERA", FieldId.ISO to "80"))
            val original = requireNotNull(layout(4080, 3072, info))
            val large = requireNotNull(layout(4080, 3072, info, CardStyle(textScale = 1.2f)))
            near(large.fontSize, original.fontSize * 1.2f)
            near(large.lineHeight, original.lineHeight * 1.2f)
            near(large.box.width, original.box.width)
            near(large.box.height, original.box.height)
            near(large.box.right, original.box.right)
            near(large.box.bottom, original.box.bottom)
        }
        verify("large text rewraps without clipping or shrinking") {
            val x = requireNotNull(layout(1527, 859, all, CardStyle(textScale = 1.5f)))
            near(x.fontSize, 15.75f)
            check(x.box.height > 168f)
            check(x.lines.all { it.text.codePointCount(0, it.text.length) * 6.3f * 1.5f <= 177f })
            check(x.lines.last().top + x.lineHeight <= x.box.bottom - 18f + .02f)
        }
        verify("invalid independent text size is bounded") {
            near(CardStyle(textScale = Float.NaN).sanitized().textScale, 1f)
            near(CardStyle(textScale = -1f).sanitized().textScale, .8f)
            near(CardStyle(textScale = 10f).sanitized().textScale, 1.8f)
        }
        verify("two wrapped rows retain font size and expand height only if needed") {
            val info=all.with(FieldId.AUTHOR,"PHOTOGRAPHER WITH A REALLY LONG NAME")
                .with(FieldId.LOCATION,"HANGZHOU CITY ZHEJIANG PROVINCE CHINA")
            val x=requireNotNull(layout(1527,859,info));near(x.fontSize,10.5f);check(x.box.height>168)
            x.lines.forEach { near(it.x,x.box.left+19f) }
            check(x.lines.none { it.text.contains('…') });check(x.lines.size>9)
        }
        verify("one wrapped credit keeps the reference card height") {
            val info = all.with(FieldId.AUTHOR, "LUIS ALBERTO RODRIGUEZ")
            val x = requireNotNull(layout(1527, 859, info))
            check(x.lines.size == 10)
            near(x.box.height, 168f)
            near(x.fontSize, 10.5f)
            near(x.lines.first().top - x.box.top, 18f)
        }
        verify("whitespace word wrapping preserves content") {
            val text="A VERY LONG PHOTOGRAPHER NAME WITH MANY WORDS"
            val lines=CardLayoutEngine.wrap(text,14f) { it.length.toFloat() }
            check(lines.all { it.length<=14 });check(lines.joinToString(" ")==text)
        }
        verify("CJK wrapping does not lose characters") {
            val text="杭州富阳摄影地点中华人民共和国"
            val lines=CardLayoutEngine.wrap(text,4f) { it.codePointCount(0,it.length).toFloat() }
            check(lines.joinToString("")==text);check(lines.all { it.length<=4 })
        }
        verify("surrogate-pair wrapping preserves whole Unicode code points") {
            val text="📷📷📷📷📷"
            val lines=CardLayoutEngine.wrap(text,2f) { it.codePointCount(0,it.length).toFloat() }
            check(lines.joinToString("")==text);check(lines.all { it.length%2==0 })
        }
        verify("explicit line breaks are preserved") {
            check(CardLayoutEngine.wrap("A\nB",10f) { it.length.toFloat() } == listOf("A","B"))
        }
        verify("oversized glyph fails instead of infinite looping") {
            check(runCatching { CardLayoutEngine.wrap("X",1f) { 100f } }.exceptionOrNull() is CardOverflowException)
        }
        verify("no missing-group spacing") {
            val info=PhotoInfo(mapOf(FieldId.ISO to "100", FieldId.APERTURE to "2.8"))
            val x=requireNotNull(layout(1527,859,info));near(x.lines[1].top-x.lines[0].top,12.5f)
        }
        verify("oversized text fails without truncating or changing font size") {
            val info=all.with(FieldId.LOCATION,"LONG ".repeat(800))
            check(runCatching { layout(859,859,info) }.exceptionOrNull() is CardOverflowException)
        }
        verify("invalid style values are sanitized") {
            val s=CardStyle(Float.NaN,Float.POSITIVE_INFINITY,-99f,Float.NaN,-1f,100f).sanitized()
            near(s.scale,1f);near(s.opacity,.6f);near(s.blur,0f);near(s.rightInset,77f);near(s.bottomInset,0f);near(s.cornerRadius,40f)
        }
        verify("extreme scale and margins keep card inside image") {
            val x=requireNotNull(layout(400,400,all,CardStyle(scale=2f,rightInset=250f,bottomInset=250f)))
            check(x.box.left>=0&&x.box.top>=0&&x.box.right<=400.01&&x.box.bottom<=400.01)
        }
        verify("zero blur returns a copy") {
            val input=intArrayOf(0xff11aa33.toInt(),0xff006600.toInt())
            val output=BoxBlur.blur(input,2,1,0);check(input.contentEquals(output));check(input!==output)
        }
        verify("solid opaque blur is constant") {
            val input=IntArray(63) {0xff8eac52.toInt()};check(BoxBlur.blur(input,9,7,14).contentEquals(input))
        }
        verify("one-pixel blur clamps all edges") { check(BoxBlur.blur(intArrayOf(0xff123456.toInt()),1,1,128).single()==0xff123456.toInt()) }
        verify("transparent RGB does not cause colored blur fringes") {
            val output=BoxBlur.blur(intArrayOf(0x00ff00ff,0xff00ff00.toInt(),0x00ff00ff),3,1,1)
            check(output.all { it and 0x00ff00ff == 0 })
        }
        verify("blur rejects mismatched dimensions") { check(runCatching {BoxBlur.blur(IntArray(4),3,2,1)}.isFailure) }
        val random=Random(20260920)
        for (width in listOf(1,2,7)) for (height in listOf(1,3,5)) for (radius in listOf(1,2,8)) {
            verify("sliding blur equals naive passes ${width}x${height} radius=$radius") {
                val input=IntArray(width*height) { 0xff000000.toInt() or random.nextInt(0x1000000) };val snapshot=input.copyOf()
                check(BoxBlur.blur(input,width,height,radius).contentEquals(naiveBlur(input,width,height,radius)))
                check(input.contentEquals(snapshot))
            }
        }
        return total
    }

    private fun naiveBlur(input: IntArray, width: Int, height: Int, radius: Int): IntArray {
        var data=input.copyOf();val window=2*radius+1
        repeat(3) {
            for (horizontal in listOf(true,false)) {
                val out=IntArray(data.size)
                for (y in 0 until height) for (x in 0 until width) {
                    val sum=IntArray(4)
                    for (i in -radius..radius) {
                        val xx=if(horizontal)(x+i).coerceIn(0,width-1) else x
                        val yy=if(horizontal)y else(y+i).coerceIn(0,height-1)
                        val c=data[yy*width+xx]
                        for(channel in 0..3)sum[channel]+=(c ushr (channel*8)) and 255
                    }
                    var c=0;for(channel in 0..3)c=c or ((sum[channel]/window) shl(channel*8))
                    out[y*width+x]=c
                }
                data=out
            }
        }
        return data
    }
}
