import Foundation

nonisolated enum AppleStylePayloadError: Error {
    case invalid(String)
}

/// Apple Photographic Styles payload, ported from the device-verified layout of
/// BeetMan/XDRemux-Flutter (Apache-2.0): an identity Standard style injected into HEIF.
/// The scene statistics and c/d light maps are measured from the photo being styled,
/// following the phone-validated calibration of nathanatgit/Shalielie (MIT).
nonisolated enum AppleStyleMetadata {
    static let stylesContentType = "tag:apple.com,2023:photo:metadata:styles"
    static let deltaMapURN = "tag:apple.com,2023:photo:aux:styledeltamap"
    static let linearThumbnailURN = "tag:apple.com,2023:photo:aux:linearthumbnail"
    static let skyMatteURN = "urn:com:apple:photo:2020:aux:semanticskymatte"
    static let skyMatteVersion = 65536
    private static let styleBlocks = 864

    /// The Apple capture pipeline's 2018-2020 semantic matte family. A container that
    /// already carries one of these holds real segmentation data, so the styles plist
    /// announces usable person masks (PersonMasksValidHint = 1.0); native captures
    /// without segmentation keep the -1.0 the reference ships. Detection must run before
    /// this injector adds its own empty sky placeholder.
    static let semanticMatteURNs: Set<String> = [
        "urn:com:apple:photo:2018:aux:portraiteffectsmatte",
        "urn:com:apple:photo:2019:aux:semanticskinmatte",
        "urn:com:apple:photo:2019:aux:semantichairmatte",
        "urn:com:apple:photo:2019:aux:semanticteethmatte",
        "urn:com:apple:photo:2020:aux:semanticglassesmatte",
        "urn:com:apple:photo:2020:aux:semanticskymatte",
    ]

    /// Scene fields derived from one photo: key '6' percentiles of the linearized display
    /// luma (ToneMappedImage; LinearImage is the same signal scaled by linearImageScale)
    /// and the 32x32 little-endian FP16 c/d light maps, stored rotated 180 degrees from
    /// the primary's stored orientation and floored at lightMapFloor.
    nonisolated struct SceneSample {
        static let linearImageScale = 0.166
        static let lightMapFloor = 0.040741
        static let cSlope = 0.7774
        static let cIntercept = 0.0294
        static let dSlope = 0.6542
        static let dIntercept = -0.0128
        // highKey is not scene-derived; the reference values travel with every style file.
        static let toneMappedHighKey = 0.5505164861679077
        static let linearHighKey = 0.9925689101216177

        var blackPoint: Double
        var p02: Double
        var p10: Double
        var p25: Double
        var p50: Double
        var p75: Double
        var p98: Double
        var whitePoint: Double
        var lightMapC: [UInt8]
        var lightMapD: [UInt8]

        static func fittedLightMap(storedLinearLumaReversed: [Double], slope: Double, intercept: Double) -> [UInt8] {
            var out = [UInt8]()
            out.reserveCapacity(storedLinearLumaReversed.count * 2)
            for value in storedLinearLumaReversed {
                let clamped = min(max(slope * value + intercept, lightMapFloor), 1.0)
                let bits = Float16(clamped).bitPattern
                out.append(UInt8(truncatingIfNeeded: bits))
                out.append(UInt8(truncatingIfNeeded: bits >> 8))
            }
            return out
        }
    }

    static var skyMatteXMP: String {
        """
        <x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="XMP Core 6.0.0">
           <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
              <rdf:Description rdf:about=""
                    xmlns:semanticSegmentationMatte="http://ns.apple.com/semanticSegmentationMatte/1.0/">
                 <semanticSegmentationMatte:SemanticSegmentationMatteVersion>\(skyMatteVersion)</semanticSegmentationMatte:SemanticSegmentationMatteVersion>
              </rdf:Description>
           </rdf:RDF>
        </x:xmpmeta>
        """ + "\n"
    }

    /// Identity lattice: 864 blocks of 30 little-endian f16 values, 1.0 at [3, 7, 11].
    static func identityStyleData() -> [UInt8] {
        var out = [UInt8]()
        out.reserveCapacity(styleBlocks * 30 * 2)
        for _ in 0..<styleBlocks {
            for index in 0..<30 {
                let value: UInt16 = (index == 3 || index == 7 || index == 11) ? 0x3c00 : 0
                out.append(UInt8(truncatingIfNeeded: value))
                out.append(UInt8(truncatingIfNeeded: value >> 8))
            }
        }
        return out
    }

    static func newIdentifier() -> String { UUID().uuidString.uppercased() }

    /// The styleMetadata binary plist describing an identity (Standard) photographic style.
    /// Object creation order must match the Android port; the scene values themselves are
    /// measured from the photo being styled, so the two ports agree on layout, not bytes.
    static func styleMetadata(scene: SceneSample, personMasksValid: Bool) -> [UInt8] {
        var writer = BplistWriter()
        let k0 = writer.addStr("0"); let v0 = writer.addInt(15)
        let kf = writer.addStr("f"); let vf = writer.addInt(32)
        let k1 = writer.addStr("1"); let v1 = writer.addData(identityStyleData())
        let kj = writer.addStr("j"); let vj = writer.addReal(1.0)
        let kg = writer.addStr("g"); let vg = writer.addInt(1278226536)
        let k4 = writer.addStr("4"); let v4 = writer.addReal(4.0)
        let ki = writer.addStr("i")
        let aI = writer.addStr("OriginalRangeMin"); let avI = writer.addReal(0.0)
        let bI = writer.addStr("OriginalRangeMax"); let bvI = writer.addReal(0.0762939453125)
        let cI = writer.addStr("Gain"); let cvI = writer.addReal(7.353515625)
        let vi = writer.addDict([(aI, avI), (bI, bvI), (cI, cvI)])
        let k6 = writer.addStr("6"); let v6 = addStats(&writer, scene: scene)
        let kc = writer.addStr("c"); let vc = writer.addData(scene.lightMapC)
        let kk = writer.addStr("k"); let vk = writer.addBool(false)
        let kh = writer.addStr("h"); let vh = writer.addReal(1.8384023904800415)
        let k2 = writer.addStr("2"); let v2 = writer.addBool(true)
        let k5 = writer.addStr("5"); let v5 = writer.addInt(2)
        let k3 = writer.addStr("3"); let v3 = writer.addData(AppleStyleGolden.FIELD_3)
        let ke = writer.addStr("e"); let ve = writer.addInt(32)
        let k7 = writer.addStr("7")
        let a7 = writer.addStr("PersonMasksValidHint"); let av7 = writer.addReal(personMasksValid ? 1.0 : -1.0)
        let b7 = writer.addStr("SkinRatio"); let bv7 = writer.addReal(0.0)
        let c7 = writer.addStr("PeopleRatio"); let cv7 = writer.addReal(0.0)
        let v7 = writer.addDict([(a7, av7), (b7, bv7), (c7, cv7)])
        let kd = writer.addStr("d"); let vd = writer.addData(scene.lightMapD)
        let top = writer.addDict([(k0, v0), (kf, vf), (k1, v1), (kj, vj), (kg, vg), (k4, v4), (ki, vi), (k6, v6),
                                  (kc, vc), (kk, vk), (kh, vh), (k2, v2), (k5, v5), (k3, v3), (ke, ve), (k7, v7), (kd, vd)])
        return writer.finish(top: top)
    }

    private static func addStats(_ writer: inout BplistWriter, scene: SceneSample) -> Int {
        func zero(_ writer: inout BplistWriter) -> Int {
            var entries: [(Int, Int)] = []
            for key in ["highKey", "p75", "p25", "blackPoint", "p02", "p50", "whitePoint", "p10", "p98"] {
                entries.append((writer.addStr(key), writer.addReal(key == "highKey" ? 1.0 : 0.0)))
            }
            return writer.addDict(entries)
        }
        func measured(_ writer: inout BplistWriter, scale: Double, highKey: Double) -> Int {
            let values: [(String, Double)] = [("p02", scene.p02 * scale), ("p98", scene.p98 * scale),
                                              ("p10", scene.p10 * scale), ("blackPoint", scene.blackPoint * scale),
                                              ("p75", scene.p75 * scale), ("highKey", highKey),
                                              ("whitePoint", scene.whitePoint * scale), ("p50", scene.p50 * scale),
                                              ("p25", scene.p25 * scale)]
            return writer.addDict(values.map { (writer.addStr($0.0), writer.addReal($0.1)) })
        }
        let toneMapped = measured(&writer, scale: 1.0, highKey: SceneSample.toneMappedHighKey)
        let linear = measured(&writer, scale: SceneSample.linearImageScale, highKey: SceneSample.linearHighKey)
        let values = [zero(&writer), zero(&writer), zero(&writer), zero(&writer), toneMapped,
                      zero(&writer), linear, zero(&writer), zero(&writer), zero(&writer)]
        let names = ["LinearImagePersonSegmentBased", "ToneMappedImageRedChannelSkinBased", "ToneMappedImagePersonSegmentBased",
                     "LinearGTCImage", "ToneMappedImage", "ToneMappedImageGreenChannelSkinBased", "LinearImage",
                     "LinearImageSkinBased", "ToneMappedImageBlueChannelSkinBased", "ToneMappedImageSkinBased"]
        var entries: [(Int, Int)] = []
        for (name, value) in zip(names, values) { entries.append((writer.addStr(name), value)) }
        return writer.addDict(entries)
    }

    /// Style MakerNote entries (tag 43 identifier, tag 84 runtime flags) following the
    /// device-verified Apple entry layout.
    static func stylesNote(identifier: String) -> [UInt8] {
        mergedStyleNote(existing: nil, identifier: identifier)
    }

    /// Merges the style entries into an existing Apple MakerNote, keeping the captured
    /// tags (notably the tag 17 Live Photo pairing id). Falls back to a fresh note when
    /// the existing payload does not parse cleanly.
    static func mergedStyleNote(existing: Data?, identifier: String) -> [UInt8] {
        precondition(identifier.count == 36)
        var entries = parsedAppleEntries(existing) ?? []
        entries.removeAll { $0.tag == 43 || $0.tag == 84 }
        entries.append(AppleMakerNoteEntry(tag: 43, type: 2, count: 37,
                                           payload: Array(identifier.uppercased().utf8) + [0]))
        entries.append(AppleMakerNoteEntry(tag: 84, type: 7, count: AppleStyleGolden.TAG_84.count,
                                           payload: AppleStyleGolden.TAG_84))
        return assembleNote(entries: entries)
    }

    nonisolated struct AppleMakerNoteEntry {
        var tag: Int
        var type: Int
        var count: Int
        var payload: [UInt8]?
        var inline: UInt32 = 0
    }

    /// Splices the merged style MakerNote into an Exif APP1 payload ("Exif\0\0" + TIFF),
    /// preserving every other root and Exif-IFD entry in both byte orders.
    static func appleNoteWithStyle(exif: Data?, styleIdentifier: String) throws -> Data {
        let existing = try existingMakerNote(exif)
        let note = mergedStyleNote(existing: existing.map(Data.init), identifier: styleIdentifier)
        return try withMakerNote(exif, note: note)
    }

    /// Tag numbers present in the file's Apple MakerNote (empty when the note is absent
    /// or unreadable); used by the metadata report to surface pairing and style tags.
    static func makerNoteTags(exif app1: Data?) -> [Int] {
        guard let note = try? existingMakerNote(app1), let entries = parsedAppleEntries(note) else { return [] }
        return entries.map(\.tag)
    }

    /// Unwraps an Exif item payload into the APP1 body ("Exif\0\0" + TIFF), tolerating
    /// legacy writers that stored the payload without the exif_data_block offset.
    static func exifApp1Payload(_ payload: [UInt8]) -> Data? {
        let marker = Array("Exif\0\0".utf8)
        func hasMarker(_ at: Int) -> Bool {
            payload.count >= at + marker.count && Array(payload[at..<at + marker.count]) == marker
        }
        if payload.count > 4 && hasMarker(4) { return Data(payload[4...]) }
        if hasMarker(0) { return Data(payload) }
        return nil
    }

    private static func assembleNote(entries: [AppleMakerNoteEntry]) -> [UInt8] {
        let sorted = entries.sorted { $0.tag < $1.tag }
        var at = 16 + sorted.count * 12 + 4
        var out = Array("Apple iOS".utf8) + [0x00, 0x00, 0x01, 0x4d, 0x4d]
        out.append(UInt8((sorted.count >> 8) & 0xff))
        out.append(UInt8(sorted.count & 0xff))
        for entry in sorted {
            let offset = entry.payload != nil ? at : 0
            out.append(UInt8((entry.tag >> 8) & 0xff)); out.append(UInt8(entry.tag & 0xff))
            out.append(UInt8((entry.type >> 8) & 0xff)); out.append(UInt8(entry.type & 0xff))
            appendBE(UInt32(entry.count), into: &out)
            appendBE(offset == 0 ? entry.inline : UInt32(offset), into: &out)
            if let payload = entry.payload { at += payload.count }
        }
        out.append(contentsOf: [0, 0, 0, 0])
        for entry in sorted where entry.payload != nil { out.append(contentsOf: entry.payload!) }
        return out
    }

    private static func parsedAppleEntries(_ data: Data?) -> [AppleMakerNoteEntry]? {
        guard let data, data.count >= 18 else { return nil }
        let bytes = [UInt8](data)
        guard Array(bytes[0..<12]) == Array("Apple iOS".utf8) + [0x00, 0x00, 0x01],
              bytes[12] == 0x4d, bytes[13] == 0x4d else { return nil }
        let count = Int(bytes[14]) << 8 | Int(bytes[15])
        guard count > 0, count <= 512, 16 + count * 12 <= bytes.count else { return nil }
        var entries: [AppleMakerNoteEntry] = []
        for index in 0..<count {
            let at = 16 + index * 12
            let tag = Int(bytes[at]) << 8 | Int(bytes[at + 1])
            let type = Int(bytes[at + 2]) << 8 | Int(bytes[at + 3])
            let entryCount = Int(u32BE(bytes, at + 4))
            let value = u32BE(bytes, at + 8)
            // Native Apple notes carry exotic entries (extended types, empty offsets);
            // skipping one malformed entry preserves the captured tags around it instead
            // of discarding the whole note and with it the Live pairing id.
            guard let unit = tiffTypeSize(type), entryCount > 0, entryCount <= 1 << 20 else { continue }
            let size = unit * entryCount
            if size > 4 {
                guard value > 0, Int(value) + size <= bytes.count else { continue }
                entries.append(AppleMakerNoteEntry(tag: tag, type: type, count: entryCount,
                                                   payload: Array(bytes[Int(value)..<Int(value) + size])))
            } else {
                entries.append(AppleMakerNoteEntry(tag: tag, type: type, count: entryCount, payload: nil, inline: value))
            }
        }
        return entries
    }

    private static func tiffTypeSize(_ type: Int) -> Int? {
        switch type {
        case 1, 2, 6, 7: return 1
        case 3, 8: return 2
        case 4, 9, 11: return 4
        case 5, 10, 12: return 8
        // TIFF technical-note extensions Apple notes use: IFD, LONG8, SLONG8, IFD8.
        case 13, 18: return 4
        case 16, 17: return 8
        default: return nil
        }
    }

    private static func existingMakerNote(_ exif: Data?) throws -> Data? {
        guard let exif else { return nil }
        let bytes = try tiffBytes(exif)
        let (root, _) = try ifdEntries(bytes, at: Int(u32(bytes, 4, bigEndian: bytes[0] == 0x4d)))
        guard let pointer = root.first(where: { $0.tag == 0x8769 }) else { return nil }
        let (exifIFD, source) = try ifdEntries(bytes, at: pointer.value)
        guard let note = exifIFD.first(where: { $0.tag == 0x927c }) else { return nil }
        guard note.type == 7, note.value > 0, note.value + note.count <= source.count else { return nil }
        return Data(bytes[note.value..<note.value + note.count])
    }

    private static func withMakerNote(_ exif: Data?, note: [UInt8]) throws -> Data {
        let prefix = Array("Exif\0\0".utf8)
        let source: [UInt8]
        if let exif {
            let bytes = [UInt8](exif)
            guard bytes.count <= 60_000, Array(bytes.prefix(6)) == prefix else {
                throw AppleStylePayloadError.invalid("exif payload prefix")
            }
            source = Array(bytes.dropFirst(6))
        } else {
            source = [0x4d, 0x4d, 0, 42, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0]
        }
        guard source.count >= 14 else { throw AppleStylePayloadError.invalid("tiff header size") }
        let bigEndian = source[0] == 0x4d && source[1] == 0x4d
        guard bigEndian || (source[0] == 0x49 && source[1] == 0x49) else {
            throw AppleStylePayloadError.invalid("tiff byte order")
        }
        guard Int(u16(source, 2, bigEndian: bigEndian)) == 42 else {
            throw AppleStylePayloadError.invalid("tiff magic")
        }
        let root = try ifdEntries(source, at: Int(u32(source, 4, bigEndian: bigEndian))).entries
        let previous = root.first(where: { $0.tag == 0x8769 }).map { entry -> [TIFFEntry] in
            (try? ifdEntries(source, at: entry.value).entries) ?? []
        } ?? []
        let noteAt = (source.count + 1) & ~1
        let exifAt = (noteAt + note.count + 1) & ~1
        var fields = previous.filter { $0.tag != 0x927c }
        fields.append(TIFFEntry(tag: 0x927c, type: 7, count: note.count, value: noteAt))
        fields.sort { $0.tag < $1.tag }
        let rootAt = exifAt + 6 + fields.count * 12
        var rootFields = root.filter { $0.tag != 0x8769 }
        rootFields.append(TIFFEntry(tag: 0x8769, type: 4, count: 1, value: exifAt))
        rootFields.sort { $0.tag < $1.tag }
        var output = [UInt8](repeating: 0, count: rootAt + 6 + rootFields.count * 12)
        output.replaceSubrange(0..<source.count, with: source)
        putU32(UInt32(rootAt), into: &output, at: 4, bigEndian: bigEndian)
        output.replaceSubrange(noteAt..<noteAt + note.count, with: note)
        putU32(UInt32(fields.count), into: &output, at: exifAt, bigEndian: bigEndian, bytes: 2)
        for (index, entry) in fields.enumerated() {
            writeTIFFEntry(entry, into: &output, at: exifAt + 2 + index * 12, bigEndian: bigEndian)
        }
        putU32(UInt32(rootFields.count), into: &output, at: rootAt, bigEndian: bigEndian, bytes: 2)
        for (index, entry) in rootFields.enumerated() {
            writeTIFFEntry(entry, into: &output, at: rootAt + 2 + index * 12, bigEndian: bigEndian)
        }
        guard output.count + 6 <= 65533 else { throw AppleStylePayloadError.invalid("exif overflow") }
        return Data(prefix + output)
    }

    private nonisolated struct TIFFEntry {
        var tag: Int
        var type: Int
        var count: Int
        var value: Int
    }

    private static func ifdEntries(_ source: [UInt8], at raw: Int) throws -> (entries: [TIFFEntry], source: [UInt8]) {
        guard raw >= 8, raw <= source.count - 6 else { throw AppleStylePayloadError.invalid("ifd offset") }
        let bigEndian = source[0] == 0x4d
        let count = Int(u16(source, raw, bigEndian: bigEndian))
        guard count <= 512, raw + 6 + count * 12 <= source.count else {
            throw AppleStylePayloadError.invalid("ifd entry count")
        }
        var entries: [TIFFEntry] = []
        for index in 0..<count {
            let at = raw + 2 + index * 12
            entries.append(TIFFEntry(tag: Int(u16(source, at, bigEndian: bigEndian)),
                                     type: Int(u16(source, at + 2, bigEndian: bigEndian)),
                                     count: Int(u32(source, at + 4, bigEndian: bigEndian)),
                                     value: Int(u32(source, at + 8, bigEndian: bigEndian))))
        }
        return (entries, source)
    }

    private static func writeTIFFEntry(_ entry: TIFFEntry, into output: inout [UInt8], at: Int, bigEndian: Bool) {
        putU32(UInt32(entry.tag), into: &output, at: at, bigEndian: bigEndian, bytes: 2)
        putU32(UInt32(entry.type), into: &output, at: at + 2, bigEndian: bigEndian, bytes: 2)
        putU32(UInt32(entry.count), into: &output, at: at + 4, bigEndian: bigEndian)
        putU32(UInt32(entry.value), into: &output, at: at + 8, bigEndian: bigEndian)
    }

    private static func tiffBytes(_ exif: Data) throws -> [UInt8] {
        let bytes = [UInt8](exif)
        guard Array(bytes.prefix(6)) == Array("Exif\0\0".utf8) else {
            throw AppleStylePayloadError.invalid("exif payload prefix")
        }
        return Array(bytes.dropFirst(6))
    }

    private static func u16(_ bytes: [UInt8], _ at: Int, bigEndian: Bool) -> UInt32 {
        bigEndian ? (UInt32(bytes[at]) << 8 | UInt32(bytes[at + 1])) : (UInt32(bytes[at + 1]) << 8 | UInt32(bytes[at]))
    }

    private static func u32(_ bytes: [UInt8], _ at: Int, bigEndian: Bool) -> UInt32 {
        let raw: UInt32 = (UInt32(bytes[at]) << 24) | (UInt32(bytes[at + 1]) << 16) | (UInt32(bytes[at + 2]) << 8) | UInt32(bytes[at + 3])
        return bigEndian ? raw : raw.byteSwapped
    }

    private static func u32BE(_ bytes: [UInt8], _ at: Int) -> UInt32 {
        (UInt32(bytes[at]) << 24) | (UInt32(bytes[at + 1]) << 16) | (UInt32(bytes[at + 2]) << 8) | UInt32(bytes[at + 3])
    }

    private static func putU32(_ value: UInt32, into output: inout [UInt8], at: Int, bigEndian: Bool, bytes: Int = 4) {
        let all: [UInt8] = bigEndian
            ? [UInt8((value >> 24) & 0xff), UInt8((value >> 16) & 0xff), UInt8((value >> 8) & 0xff), UInt8(value & 0xff)]
            : [UInt8(value & 0xff), UInt8((value >> 8) & 0xff), UInt8((value >> 16) & 0xff), UInt8((value >> 24) & 0xff)]
        output.replaceSubrange(at..<at + bytes, with: Array(all[(4 - bytes)...]))
    }

    private static func appendBE(_ value: UInt32, into out: inout [UInt8]) {
        out.append(UInt8((value >> 24) & 0xff))
        out.append(UInt8((value >> 16) & 0xff))
        out.append(UInt8((value >> 8) & 0xff))
        out.append(UInt8(value & 0xff))
    }
}

/// Minimal binary-plist writer mirroring the Android BplistWriter byte layout: objects
/// are emitted sequentially with 1/2-byte references and a 32-byte trailer.
nonisolated struct BplistWriter {
    private enum Object {
        case bool(Bool)
        case int(Int64)
        case real(Double)
        case data([UInt8])
        case str(String)
        case dict([(Int, Int)])
    }

    private var objects: [Object] = []

    mutating func addBool(_ value: Bool) -> Int { objects.append(.bool(value)); return objects.count - 1 }
    mutating func addInt(_ value: Int64) -> Int { objects.append(.int(value)); return objects.count - 1 }
    mutating func addReal(_ value: Double) -> Int { objects.append(.real(value)); return objects.count - 1 }
    mutating func addData(_ value: [UInt8]) -> Int { objects.append(.data(value)); return objects.count - 1 }
    mutating func addStr(_ value: String) -> Int { objects.append(.str(value)); return objects.count - 1 }
    mutating func addDict(_ entries: [(Int, Int)]) -> Int { objects.append(.dict(entries)); return objects.count - 1 }

    mutating func finish(top: Int) -> [UInt8] {
        var out: [UInt8] = Array("bplist00".utf8)
        var offsets = [Int](repeating: 0, count: objects.count)
        for (index, object) in objects.enumerated() {
            offsets[index] = out.count
            write(object, into: &out)
        }
        let refSize = objects.count <= 255 ? 1 : 2
        let offsetTableAt = out.count
        let offsetSize = offsetTableAt + 8 <= 0xff ? 1 : (offsetTableAt + 8 <= 0xffff ? 2 : 4)
        for offset in offsets {
            switch offsetSize {
            case 1: out.append(UInt8(truncatingIfNeeded: offset))
            case 2: out.append(UInt8((offset >> 8) & 0xff)); out.append(UInt8(offset & 0xff))
            default: appendBE(UInt32(offset), into: &out)
            }
        }
        var trailer = [UInt8](repeating: 0, count: 32)
        trailer[6] = UInt8(offsetSize)
        trailer[7] = UInt8(refSize)
        trailer.replaceSubrange(8..<16, with: bigEndianBytes(UInt64(objects.count)))
        trailer.replaceSubrange(16..<24, with: bigEndianBytes(UInt64(top)))
        trailer.replaceSubrange(24..<32, with: bigEndianBytes(UInt64(offsetTableAt)))
        out.append(contentsOf: trailer)
        return out
    }

    private func write(_ object: Object, into out: inout [UInt8]) {
        func lengthPrefix(_ marker: UInt8, _ length: Int) {
            if length < 15 {
                out.append(marker | UInt8(length))
            } else {
                out.append(marker | 0x0f)
                if length <= 0xff {
                    out.append(0x10); out.append(UInt8(length))
                } else if length <= 0xffff {
                    out.append(0x11); out.append(UInt8((length >> 8) & 0xff)); out.append(UInt8(length & 0xff))
                } else {
                    out.append(0x12); appendBE(UInt32(length), into: &out)
                }
            }
        }
        func ref(_ value: Int) {
            if objects.count <= 255 {
                out.append(UInt8(truncatingIfNeeded: value))
            } else {
                out.append(UInt8((value >> 8) & 0xff)); out.append(UInt8(value & 0xff))
            }
        }
        switch object {
        case .bool(let value): out.append(value ? 0x09 : 0x08)
        case .int(let value):
            let bytes = value >= 0 && value <= 0xff ? 1
                : value >= 0 && value <= 0xffff ? 2
                : value >= 0 && value <= 0xffff_ffff ? 4 : 8
            out.append(0x10 | UInt8(bytes.trailingZeroBitCount))
            out.append(contentsOf: bigEndianBytes(UInt64(bitPattern: value))[(8 - bytes)...])
        case .real(let value):
            out.append(0x23)
            out.append(contentsOf: bigEndianBytes(value.bitPattern))
        case .data(let value):
            lengthPrefix(0x40, value.count)
            out.append(contentsOf: value)
        case .str(let value):
            let bytes = Array(value.utf8)
            lengthPrefix(0x50, bytes.count)
            out.append(contentsOf: bytes)
        case .dict(let entries):
            lengthPrefix(0xd0, entries.count)
            entries.forEach { ref($0.0) }
            entries.forEach { ref($0.1) }
        }
    }
}

nonisolated private func bigEndianBytes(_ value: UInt64) -> [UInt8] {
    withUnsafeBytes(of: value.bigEndian) { Array($0) }
}

nonisolated private func appendBE(_ value: UInt32, into out: inout [UInt8]) {
    out.append(UInt8((value >> 24) & 0xff))
    out.append(UInt8((value >> 16) & 0xff))
    out.append(UInt8((value >> 8) & 0xff))
    out.append(UInt8(value & 0xff))
}
