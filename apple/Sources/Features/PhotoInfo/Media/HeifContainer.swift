import Foundation

nonisolated enum MediaContainerError: Error {
    case invalid(String)
}

/// Surgical HEIF meta-box rewriter: parses an existing container, appends hidden items,
/// properties and references, and writes the file back without re-encoding any payload.
/// Unlike the Android writer (which re-emits a curated box set), unknown top-level and
/// meta child boxes are preserved verbatim so Apple-authored files survive the round
/// trip: grid primaries, idat-resident payloads, multiple mdats, existing groups.
nonisolated struct HeifContainer {
    nonisolated struct Item {
        var id: UInt32
        var type: String
        var infoSuffix: [UInt8]
        var hidden: Bool
        var constructionMethod: Int
        var baseOffset: UInt64
        var extents: [Extent]
    }

    nonisolated struct Extent {
        var offset: UInt64
        var length: UInt64
    }

    nonisolated struct Reference {
        var type: String
        var from: UInt32
        var to: [UInt32]
    }

    nonisolated struct PropertyAssociation {
        var index: Int
        var essential: Bool
    }

    nonisolated struct RawBox {
        var type: String
        var start: Int
        var payload: Int
        var end: Int
    }

    private(set) var primary: UInt32
    private(set) var items: [Item]
    private(set) var properties: [[UInt8]]
    private(set) var associations: [UInt32: [PropertyAssociation]]
    private(set) var references: [Reference]
    private(set) var majorBrand: String
    private(set) var compatibleBrands: [String]

    private var original: [UInt8]
    private var topLevel: [RawBox]
    private var metaRange: Range<Int>
    private var metaChildren: [RawBox]
    private var hasIREF: Bool
    private var irefVersion: Int
    private var idatPayload: Range<Int>?
    private var originalItemCount: Int
    private var originalPropertyCount: Int
    private var originalReferenceCount: Int
    private var appendedPayloads: [UInt32: [UInt8]] = [:]

    private var maxItemID: UInt32 { items.map(\.id).max() ?? 0 }

    /// Entity-to-group ids share the item-id space: a group id that equals an item id
    /// is a parse error for MediaToolbox. Apple's own writer picks max item id + 1 for
    /// its altr group, so appended items must clear the reserved group ids too.
    private var nextAvailableID: UInt32 {
        max(maxItemID, reservedGroupIDs().max() ?? 0) + 1
    }

    // MARK: Loading

    nonisolated static func load(fileURL: URL) throws -> HeifContainer {
        try load(bytes: [UInt8](try Data(contentsOf: fileURL)))
    }

    nonisolated static func load(bytes: [UInt8]) throws -> HeifContainer {
        guard bytes.count >= 16 else { throw MediaContainerError.invalid("file too small") }
        let top = try boxes(in: bytes, from: 0, to: bytes.count)
        guard let ftyp = top.first, ftyp.type == "ftyp" else {
            throw MediaContainerError.invalid("missing ftyp")
        }
        guard top.filter({ $0.type == "ftyp" }).count == 1, top.filter({ $0.type == "meta" }).count == 1 else {
            throw MediaContainerError.invalid("duplicate ftyp or meta")
        }
        let brandBytes = Array(bytes[ftyp.payload..<ftyp.end])
        guard !brandBytes.isEmpty, brandBytes.count % 4 == 0 else {
            throw MediaContainerError.invalid("ftyp size")
        }
        let brands = (0..<brandBytes.count / 4).map {
            String(decoding: brandBytes[$0 * 4..<$0 * 4 + 4], as: UTF8.self)
        }
        let metaBox = top.first { $0.type == "meta" }!
        guard u32(bytes, metaBox.payload) == 0 else { throw MediaContainerError.invalid("meta version") }
        let children = try boxes(in: bytes, from: metaBox.payload + 4, to: metaBox.end)

        func child(_ type: String) -> RawBox? { children.first { $0.type == type } }
        guard let pitm = child("pitm"), let iinf = child("iinf"), let iloc = child("iloc"),
              let iprp = child("iprp"), child("hdlr") != nil else {
            throw MediaContainerError.invalid("meta missing pitm/iinf/iloc/iprp/hdlr")
        }
        var pitmReader = Reader(bytes, pitm.payload, pitm.end)
        let pitmVersion = try pitmReader.u8()
        pitmReader.at += 3
        guard pitmVersion <= 1 else { throw MediaContainerError.invalid("pitm version") }
        let primary = pitmVersion == 0 ? UInt32(try pitmReader.u16()) : try pitmReader.u32()

        var iinfReader = Reader(bytes, iinf.payload, iinf.end)
        let iinfVersion = try iinfReader.u8()
        iinfReader.at += 3
        guard iinfVersion == 0 else { throw MediaContainerError.invalid("iinf version \(iinfVersion)") }
        let itemCount = Int(try iinfReader.u16())
        let infoEntries = try boxes(in: bytes, from: iinf.payload + 6, to: iinf.end)
        guard itemCount == infoEntries.count, itemCount >= 1, itemCount <= 4096 else {
            throw MediaContainerError.invalid("item count \(itemCount) vs \(infoEntries.count)")
        }
        var parsedItems: [Item] = []
        for entry in infoEntries {
            guard entry.type == "infe" else { throw MediaContainerError.invalid("info entry \(entry.type)") }
            var reader = Reader(bytes, entry.payload, entry.end)
            let version = try reader.u8()
            let flags = (Int(try reader.u8()) << 16) | (Int(try reader.u8()) << 8) | Int(try reader.u8())
            guard version == 2 || version == 3, flags <= 1 else {
                throw MediaContainerError.invalid("infe v\(version) f\(flags)")
            }
            let id = version == 2 ? UInt32(try reader.u16()) : try reader.u32()
            guard try reader.u16() == 0 else { throw MediaContainerError.invalid("protected item") }
            let type = try reader.fourCC()
            let suffix = Array(bytes[reader.at..<entry.end])
            parsedItems.append(Item(id: id, type: type, infoSuffix: suffix, hidden: flags == 1,
                                    constructionMethod: 0, baseOffset: 0, extents: []))
        }
        guard Set(parsedItems.map(\.id)).count == itemCount, parsedItems.contains(where: { $0.id == primary }) else {
            throw MediaContainerError.invalid("item id mismatch")
        }

        var locationReader = Reader(bytes, iloc.payload, iloc.end)
        let locationVersion = try locationReader.u8()
        locationReader.at += 3
        guard locationVersion <= 2 else { throw MediaContainerError.invalid("iloc version") }
        let sizes = try locationReader.u8()
        let sizes2 = try locationReader.u8()
        let offsetSize = Int(sizes >> 4)
        let lengthSize = Int(sizes & 15)
        let baseSize = Int(sizes2 >> 4)
        let indexSize = locationVersion == 0 ? 0 : Int(sizes2 & 15)
        let locationCount = locationVersion < 2 ? Int(try locationReader.u16()) : Int(try locationReader.u32())
        guard locationCount == itemCount else { throw MediaContainerError.invalid("iloc count") }
        var locations: [UInt32: (construction: Int, base: UInt64, extents: [Extent])] = [:]
        for _ in 0..<locationCount {
            let id = locationVersion < 2 ? UInt32(try locationReader.u16()) : try locationReader.u32()
            let construction = locationVersion > 0 ? Int(try locationReader.u16()) : 0
            guard construction <= 1, try locationReader.u16() == 0 else {
                throw MediaContainerError.invalid("iloc method or data ref")
            }
            let base = try locationReader.variable(baseSize)
            let extentCount = Int(try locationReader.u16())
            guard extentCount >= 1, extentCount <= 4096 else { throw MediaContainerError.invalid("extent count") }
            var extents: [Extent] = []
            for _ in 0..<extentCount {
                _ = try locationReader.variable(indexSize)
                let offset = try locationReader.variable(offsetSize)
                let length = try locationReader.variable(lengthSize)
                extents.append(Extent(offset: offset, length: length))
            }
            guard locations[id] == nil else { throw MediaContainerError.invalid("duplicate iloc \(id)") }
            locations[id] = (construction, base, extents)
        }
        guard locationReader.at == iloc.end else { throw MediaContainerError.invalid("iloc trailing bytes") }

        var irefBox: Range<Int>?
        var irefVer = 0
        var parsedReferences: [Reference] = []
        if let iref = child("iref") {
            var reader = Reader(bytes, iref.payload, iref.end)
            irefVer = Int(try reader.u8())
            reader.at += 3
            guard irefVer <= 1 else { throw MediaContainerError.invalid("iref version") }
            irefBox = iref.start..<iref.end
            for entry in try boxes(in: bytes, from: iref.payload + 4, to: iref.end) {
                var entryReader = Reader(bytes, entry.payload, entry.end)
                let from = irefVer == 0 ? UInt32(try entryReader.u16()) : try entryReader.u32()
                let count = Int(try entryReader.u16())
                guard count <= 4096 else { throw MediaContainerError.invalid("reference count") }
                var to: [UInt32] = []
                for _ in 0..<count { to.append(irefVer == 0 ? UInt32(try entryReader.u16()) : try entryReader.u32()) }
                parsedReferences.append(Reference(type: entry.type, from: from, to: to))
            }
        }

        let iprpChildren = try boxes(in: bytes, from: iprp.payload, to: iprp.end)
        guard let ipco = iprpChildren.first(where: { $0.type == "ipco" }) else {
            throw MediaContainerError.invalid("missing ipco")
        }
        let propertyBoxes = try boxes(in: bytes, from: ipco.payload, to: ipco.end)
        let rawProperties = propertyBoxes.map { Array(bytes[$0.start..<$0.end]) }
        var parsedAssociations: [UInt32: [PropertyAssociation]] = [:]
        for ipma in iprpChildren where ipma.type == "ipma" {
            var reader = Reader(bytes, ipma.payload, ipma.end)
            let version = try reader.u8()
            _ = try reader.u16()
            let flags = try reader.u8()
            guard version <= 1, flags <= 1 else { throw MediaContainerError.invalid("ipma v\(version) f\(flags)") }
            let count = Int(try reader.u32())
            guard count <= 4096 else { throw MediaContainerError.invalid("ipma count") }
            for _ in 0..<count {
                let id = version == 0 ? UInt32(try reader.u16()) : try reader.u32()
                let propertyCount = Int(try reader.u8())
                var props: [PropertyAssociation] = []
                for _ in 0..<propertyCount {
                    let value = flags == 0 ? Int(try reader.u8()) : Int(try reader.u16())
                    let mask = flags == 0 ? 0x80 : 0x8000
                    let index = value & (mask - 1)
                    guard index > 0, index <= rawProperties.count else {
                        throw MediaContainerError.invalid("association index \(index)")
                    }
                    props.append(PropertyAssociation(index: index, essential: value & mask != 0))
                }
                guard parsedAssociations[id] == nil else { throw MediaContainerError.invalid("ipma duplicate \(id)") }
                parsedAssociations[id] = props
            }
        }

        var mergedItems: [Item] = []
        for index in 0..<parsedItems.count {
            var item = parsedItems[index]
            guard let location = locations[item.id] else {
                throw MediaContainerError.invalid("no iloc for \(item.id)")
            }
            item.constructionMethod = location.construction
            item.baseOffset = location.base
            item.extents = location.extents
            mergedItems.append(item)
        }
        let idatChild = child("idat")
        return HeifContainer(primary: primary, items: mergedItems, properties: rawProperties,
                             associations: parsedAssociations, references: parsedReferences,
                             majorBrand: brands.first ?? "", compatibleBrands: Array(brands.dropFirst()),
                             original: bytes, topLevel: top, metaRange: metaBox.start..<metaBox.end,
                             metaChildren: children, hasIREF: irefBox != nil, irefVersion: irefVer,
                             idatPayload: idatChild.map { $0.payload..<$0.end },
                             originalItemCount: mergedItems.count, originalPropertyCount: rawProperties.count,
                             originalReferenceCount: parsedReferences.count)
    }

    private init(primary: UInt32, items: [Item], properties: [[UInt8]],
                 associations: [UInt32: [PropertyAssociation]], references: [Reference],
                 majorBrand: String, compatibleBrands: [String], original: [UInt8], topLevel: [RawBox],
                 metaRange: Range<Int>, metaChildren: [RawBox], hasIREF: Bool, irefVersion: Int,
                 idatPayload: Range<Int>?, originalItemCount: Int, originalPropertyCount: Int,
                 originalReferenceCount: Int) {
        self.primary = primary
        self.items = items
        self.properties = properties
        self.associations = associations
        self.references = references
        self.majorBrand = majorBrand
        self.compatibleBrands = compatibleBrands
        self.original = original
        self.topLevel = topLevel
        self.metaRange = metaRange
        self.metaChildren = metaChildren
        self.hasIREF = hasIREF
        self.irefVersion = irefVersion
        self.idatPayload = idatPayload
        self.originalItemCount = originalItemCount
        self.originalPropertyCount = originalPropertyCount
        self.originalReferenceCount = originalReferenceCount
    }

    // MARK: Reading

    nonisolated struct Reader {
        let bytes: [UInt8]
        var at: Int
        let end: Int

        init(_ bytes: [UInt8], _ at: Int, _ end: Int) {
            self.bytes = bytes
            self.at = at
            self.end = end
        }

        mutating func u8() throws -> UInt8 {
            guard at < end else { throw MediaContainerError.invalid("reader overrun") }
            defer { at += 1 }
            return bytes[at]
        }

        mutating func u16() throws -> UInt16 {
            try UInt16(u8()) << 8 | UInt16(u8())
        }

        mutating func u32() throws -> UInt32 {
            try UInt32(u16()) << 16 | UInt32(u16())
        }

        mutating func fourCC() throws -> String {
            let raw: [UInt8] = [try u8(), try u8(), try u8(), try u8()]
            return String(decoding: raw, as: UTF8.self)
        }

        mutating func variable(_ size: Int) throws -> UInt64 {
            var value: UInt64 = 0
            for _ in 0..<size { value = value << 8 | UInt64(try u8()) }
            return value
        }
    }

    nonisolated static func boxes(in bytes: [UInt8], from: Int, to: Int) throws -> [RawBox] {
        var result: [RawBox] = []
        var at = from
        while at < to {
            guard at + 8 <= to else { throw MediaContainerError.invalid("truncated box header") }
            var size = Int(u32(bytes, at))
            var headerSize = 8
            if size == 1 {
                guard at + 16 <= to else { throw MediaContainerError.invalid("truncated largesize") }
                size = Int(u64(bytes, at + 8))
                headerSize = 16
            } else if size == 0 {
                size = to - at
            }
            let type = String(decoding: bytes[(at + 4)..<(at + 8)], as: UTF8.self)
            guard size >= headerSize, at + size <= to else {
                throw MediaContainerError.invalid("box size \(size) at \(at) (type \(type))")
            }
            result.append(RawBox(type: type, start: at, payload: at + headerSize, end: at + size))
            at += size
        }
        return result
    }

    func payload(of id: UInt32) throws -> [UInt8] {
        guard let item = items.first(where: { $0.id == id }) else {
            throw MediaContainerError.invalid("item \(id) missing")
        }
        var out: [UInt8] = []
        for extent in item.extents {
            let start: Int
            let end: Int
            if item.constructionMethod == 1 {
                guard let idat = idatPayload else { throw MediaContainerError.invalid("idat missing") }
                start = idat.lowerBound + Int(item.baseOffset) + Int(extent.offset)
                end = start + Int(extent.length)
                guard start >= idat.lowerBound, end <= idat.upperBound else {
                    throw MediaContainerError.invalid("idat extent out of range")
                }
            } else {
                start = Int(item.baseOffset) + Int(extent.offset)
                end = start + Int(extent.length)
                let insideMDAT = topLevel.contains { $0.type == "mdat" && start >= $0.payload && end <= $0.end }
                guard end <= original.count, insideMDAT else {
                    throw MediaContainerError.invalid("extent out of mdat")
                }
            }
            out.append(contentsOf: original[start..<end])
        }
        return out
    }

    func item(_ id: UInt32) -> Item? { items.first { $0.id == id } }

    func propertyType(_ index: Int) -> String? {
        guard index >= 1, index <= properties.count, properties[index - 1].count >= 8 else { return nil }
        return String(decoding: properties[index - 1][4..<8], as: UTF8.self)
    }

    func associations(of id: UInt32) -> [PropertyAssociation] { associations[id] ?? [] }

    func auxCURN(of id: UInt32) -> String? {
        associations(of: id).compactMap { association -> String? in
            guard let type = propertyType(association.index), type == "auxC" else { return nil }
            let raw = properties[association.index - 1]
            guard raw.count > 12 else { return nil }
            return String(decoding: raw[12...], as: UTF8.self).trimmingCharacters(in: ["\0"])
        }.first
    }

    var tmapIDs: [UInt32] { items.filter { $0.type == "tmap" }.map(\.id) }
    var toneTargets: [UInt32] { [primary] + tmapIDs }

    /// Byte length of a trailing mpvd motion box, when present (Google motion in HEIC).
    var embeddedMotionLength: Int? {
        topLevel.first { $0.type == "mpvd" }.map { $0.end - $0.payload }
    }

    /// ISO 21496-1 gain-map payload carried by the tone map item, when present.
    func isoGainMapPayload() -> [UInt8]? {
        guard let tone = items.first(where: { $0.type == "tmap" }) else { return nil }
        return try? payload(of: tone.id)
    }

    /// Content types carried by `uri `/`mime` items sit in the infe tail after the name: name\0type\0.
    func contentType(of item: Item) -> String? {
        let bytes = item.infoSuffix
        guard let firstNUL = bytes.firstIndex(of: 0), firstNUL + 1 < bytes.count else { return nil }
        let rest = bytes[bytes.index(after: firstNUL)...]
        let end = rest.firstIndex(of: 0) ?? rest.endIndex
        return String(decoding: rest[..<end], as: UTF8.self)
    }

    func containsASCII(_ bytes: [UInt8], _ needle: String) -> Bool {
        let target = Array(needle.utf8)
        guard !target.isEmpty, target.count <= bytes.count else { return false }
        for start in 0...(bytes.count - target.count) {
            if Array(bytes[start..<start + target.count]) == target { return true }
        }
        return false
    }

    /// Ports the Android HeifGraph detection and splits it per style generation so a
    /// photo carrying only the 2023 Standard stack can still gain the 2026 texture
    /// layer (and the other way round).
    var stylesCoverage: (photographic: Bool, texture: Bool) {
        var photographic = false
        var texture = false
        for item in items {
            if item.type.localizedCaseInsensitiveContains("style") ||
                containsASCII(item.infoSuffix, "styleMetadata") ||
                containsASCII(item.infoSuffix, "photo:metadata:styles") {
                photographic = true
            }
            if containsASCII(item.infoSuffix, AppleTextureStyles.textureStylesContentType) {
                texture = true
            }
        }
        return (photographic, texture)
    }

    // MARK: Mutation

    mutating func appendProperty(_ raw: [UInt8]) -> Int {
        properties.append(raw)
        return properties.count
    }

    @discardableResult
    mutating func addHiddenItem(type: String, infoSuffix: [UInt8], payload: [UInt8],
                                properties newProperties: [PropertyAssociation]) -> UInt32 {
        let id = nextAvailableID
        items.append(Item(id: id, type: type, infoSuffix: infoSuffix, hidden: true,
                          constructionMethod: 0, baseOffset: 0, extents: []))
        associations[id] = newProperties
        appendedPayloads[id] = payload
        return id
    }

    mutating func addReference(type: String, from: UInt32, to: [UInt32]) {
        references.append(Reference(type: type, from: from, to: to))
    }

    /// Moves an existing item's payload into the appended mdat (used when the Exif item
    /// grows to fit the merged MakerNote).
    mutating func rehomeItemPayload(id: UInt32, newPayload: [UInt8]) throws {
        guard items.contains(where: { $0.id == id }) else { throw MediaContainerError.invalid("item \(id) missing") }
        appendedPayloads[id] = newPayload
    }

    // MARK: Box builders

    nonisolated static func ispeBox(_ width: Int, _ height: Int) -> [UInt8] {
        box("ispe", versioned: true) { out in
            appendBE(UInt32(width), into: &out)
            appendBE(UInt32(height), into: &out)
        }
    }

    nonisolated static func pixiBox(channels: [UInt8]) -> [UInt8] {
        box("pixi", versioned: true) { out in
            out.append(UInt8(channels.count))
            out.append(contentsOf: channels)
        }
    }

    nonisolated static func auxCBox(_ urn: String) -> [UInt8] {
        box("auxC", versioned: true) { out in
            out.append(contentsOf: Array(urn.utf8))
            out.append(0)
        }
    }

    nonisolated static func box(_ type: String, versioned: Bool, payload: (inout [UInt8]) -> Void) -> [UInt8] {
        var body: [UInt8] = []
        if versioned { body.append(contentsOf: [0, 0, 0, 0]) }
        var inner: [UInt8] = []
        payload(&inner)
        body.append(contentsOf: inner)
        var bytes: [UInt8] = []
        appendBE(UInt32(8 + body.count), into: &bytes)
        bytes.append(contentsOf: Array(type.utf8.prefix(4)))
        bytes.append(contentsOf: body)
        return bytes
    }

    // MARK: Writing

    func write(to url: URL) throws {
        try Data(try serialized()).write(to: url, options: .atomic)
    }

    private func serialized() throws -> [UInt8] {
        guard items.count >= 1, items.count <= 4096 else { throw MediaContainerError.invalid("item count \(items.count)") }
        guard Set(items.map(\.id)).count == items.count else { throw MediaContainerError.invalid("duplicate item ids") }
        guard items.allSatisfy({ $0.id >= 1 && $0.id <= 65535 }) else {
            throw MediaContainerError.invalid("item id exceeds 16 bits")
        }
        guard items.allSatisfy({ associations[$0.id].map { $0.count <= 255 } ?? true }) else {
            throw MediaContainerError.invalid("property association overflow")
        }
        guard properties.count < 32768 else { throw MediaContainerError.invalid("property count") }
        guard appendedPayloads.values.allSatisfy({ !$0.isEmpty }) else {
            throw MediaContainerError.invalid("empty payload")
        }
        guard metaChildren.contains(where: { $0.type == "hdlr" }) else {
            throw MediaContainerError.invalid("missing hdlr")
        }

        // Two passes mirror the Android writer: the meta box is sized with placeholder
        // offsets first, because iloc carries absolute file offsets that depend on the
        // final meta size. Entry widths do not change between passes.
        let entriesPass1 = ilocEntries(appendedBase: 0, shift: 0)
        let metaPass1 = try metaBytes(ilocEntries: entriesPass1)
        let shift = metaPass1.count - metaRange.count
        let appendedBase = UInt64(original.count + shift + 8)
        let entriesPass2 = ilocEntries(appendedBase: appendedBase, shift: UInt64(shift))
        let metaPass2 = try metaBytes(ilocEntries: entriesPass2)
        guard metaPass2.count == metaPass1.count else { throw MediaContainerError.invalid("meta size drift") }

        for entry in entriesPass2 {
            for extent in entry.extents where extent.offset + extent.length > 0xffff_ffff {
                throw MediaContainerError.invalid("extent beyond 32 bits")
            }
        }

        var out: [UInt8] = []
        for box in topLevel {
            if box.type == "meta" {
                out.append(contentsOf: metaPass2)
            } else {
                out.append(contentsOf: original[box.start..<box.end])
            }
        }
        let payloads = items.compactMap { item -> (UInt32, [UInt8])? in
            appendedPayloads[item.id].map { (item.id, $0) }
        }
        var mdat: [UInt8] = []
        appendBE(UInt32(payloads.reduce(8) { $0 + $1.1.count }), into: &mdat)
        mdat.append(contentsOf: Array("mdat".utf8))
        out.append(contentsOf: mdat)
        for (_, payload) in payloads { out.append(contentsOf: payload) }
        return out
    }

    private func ilocEntries(appendedBase: UInt64, shift: UInt64) -> [(id: UInt32, construction: Int, extents: [Extent])] {
        var running = appendedBase
        var entries: [(UInt32, Int, [Extent])] = []
        for item in items {
            if let payload = appendedPayloads[item.id] {
                entries.append((item.id, 0, [Extent(offset: running, length: UInt64(payload.count))]))
                running += UInt64(payload.count)
            } else {
                let extents: [Extent] = item.extents.map { extent in
                    if item.constructionMethod == 0 {
                        return Extent(offset: item.baseOffset + extent.offset + shift, length: extent.length)
                    }
                    return Extent(offset: item.baseOffset + extent.offset, length: extent.length)
                }
                entries.append((item.id, item.constructionMethod, extents))
            }
        }
        return entries.map { (id: $0.0, construction: $0.1, extents: $0.2) }
    }

    private func metaBytes(ilocEntries: [(id: UInt32, construction: Int, extents: [Extent])]) throws -> [UInt8] {
        var body: [UInt8] = [0, 0, 0, 0]
        for child in metaChildren {
            switch child.type {
            case "iinf": body.append(contentsOf: try rebuiltIINF())
            case "iloc": body.append(contentsOf: try rebuiltILOC(ilocEntries))
            case "iref": body.append(contentsOf: try rebuiltIREF())
            case "iprp": body.append(contentsOf: try rebuiltIPRP())
            case "grpl":
                if let tone = tmapIDs.first, !hasAltrGroup(containing: tone) {
                    body.append(contentsOf: Self.box("grpl", versioned: false) { out in
                        out.append(contentsOf: Self.box("altr", versioned: true) { group in
                            appendBE(nextAvailableID, into: &group)
                            appendBE(2, into: &group)
                            appendBE(tone, into: &group)
                            appendBE(primary, into: &group)
                        })
                    })
                } else {
                    body.append(contentsOf: original[child.start..<child.end])
                }
            default: body.append(contentsOf: original[child.start..<child.end])
            }
        }
        var metaBox: [UInt8] = []
        appendBE(UInt32(8 + body.count), into: &metaBox)
        metaBox.append(contentsOf: Array("meta".utf8))
        metaBox.append(contentsOf: body)
        return metaBox
    }

    private func rebuiltIINF() throws -> [UInt8] {
        var payload: [UInt8] = []
        payload.append(UInt8((items.count >> 8) & 0xff))
        payload.append(UInt8(items.count & 0xff))
        for item in items {
            var entry: [UInt8] = [2, 0, 0, item.hidden ? 1 : 0]
            entry.append(UInt8((item.id >> 8) & 0xff))
            entry.append(UInt8(item.id & 0xff))
            entry.append(contentsOf: [0, 0])
            entry.append(contentsOf: Array(item.type.utf8.prefix(4)))
            entry.append(contentsOf: item.infoSuffix)
            payload.append(contentsOf: Self.box("infe", versioned: false) { out in out.append(contentsOf: entry) })
        }
        var bytes: [UInt8] = []
        appendBE(UInt32(12 + payload.count), into: &bytes)
        bytes.append(contentsOf: Array("iinf".utf8))
        bytes.append(contentsOf: [0, 0, 0, 0])
        bytes.append(contentsOf: payload)
        return bytes
    }

    private func rebuiltILOC(_ entries: [(id: UInt32, construction: Int, extents: [Extent])]) throws -> [UInt8] {
        var payload: [UInt8] = [0x44, 0x00]
        payload.append(UInt8((items.count >> 8) & 0xff))
        payload.append(UInt8(items.count & 0xff))
        for entry in entries {
            payload.append(UInt8((entry.id >> 8) & 0xff))
            payload.append(UInt8(entry.id & 0xff))
            payload.append(UInt8((entry.construction >> 8) & 0xff))
            payload.append(UInt8(entry.construction & 0xff))
            payload.append(contentsOf: [0, 0])
            payload.append(UInt8((entry.extents.count >> 8) & 0xff))
            payload.append(UInt8(entry.extents.count & 0xff))
            for extent in entry.extents {
                appendBE(UInt32(extent.offset), into: &payload)
                appendBE(UInt32(extent.length), into: &payload)
            }
        }
        var bytes: [UInt8] = []
        appendBE(UInt32(12 + payload.count), into: &bytes)
        bytes.append(contentsOf: Array("iloc".utf8))
        bytes.append(contentsOf: [0x01, 0, 0, 0])
        bytes.append(contentsOf: payload)
        return bytes
    }

    private func rebuiltIREF() throws -> [UInt8] {
        // The version/flags word lives in the box header below; payload holds only entries.
        var payload: [UInt8] = []
        if let range = irefRangeForWrite {
            // Old entries start after size, type and the FullBox version/flags.
            payload.append(contentsOf: original[range.lowerBound + 12..<range.upperBound])
        }
        let wide = irefVersion == 1
        for reference in references.dropFirst(originalReferenceCount) {
            var entry: [UInt8] = []
            if wide {
                appendBE(reference.from, into: &entry)
                appendBE(UInt32(reference.to.count), into: &entry)
                reference.to.forEach { appendBE($0, into: &entry) }
            } else {
                guard reference.from <= 65535, reference.to.allSatisfy({ $0 <= 65535 }) else {
                    throw MediaContainerError.invalid("reference id exceeds 16 bits")
                }
                entry.append(UInt8((reference.from >> 8) & 0xff)); entry.append(UInt8(reference.from & 0xff))
                entry.append(UInt8((reference.to.count >> 8) & 0xff)); entry.append(UInt8(reference.to.count & 0xff))
                reference.to.forEach {
                    entry.append(UInt8(($0 >> 8) & 0xff)); entry.append(UInt8($0 & 0xff))
                }
            }
            payload.append(contentsOf: Self.box(reference.type, versioned: false) { out in out.append(contentsOf: entry) })
        }
        var bytes: [UInt8] = []
        appendBE(UInt32(12 + payload.count), into: &bytes)
        bytes.append(contentsOf: Array("iref".utf8))
        bytes.append(contentsOf: [UInt8(irefVersion), 0, 0, 0])
        bytes.append(contentsOf: payload)
        return bytes
    }

    private var irefRangeForWrite: Range<Int>? {
        metaChildren.first { $0.type == "iref" }.map { $0.start..<$0.end }
    }

    private func rebuiltIPRP() throws -> [UInt8] {
        let iprpChild = metaChildren.first { $0.type == "iprp" }!
        let children = try Self.boxes(in: original, from: iprpChild.payload, to: iprpChild.end)
        guard let ipco = children.first(where: { $0.type == "ipco" }) else {
            throw MediaContainerError.invalid("missing ipco")
        }
        var ipcoPayload: [UInt8] = Array(original[ipco.payload..<ipco.end])
        for raw in properties.dropFirst(originalPropertyCount) { ipcoPayload.append(contentsOf: raw) }
        var iprpPayload: [UInt8] = []
        for child in children {
            if child.type == "ipco" {
                var bytes: [UInt8] = []
                appendBE(UInt32(8 + ipcoPayload.count), into: &bytes)
                bytes.append(contentsOf: Array("ipco".utf8))
                bytes.append(contentsOf: ipcoPayload)
                iprpPayload.append(contentsOf: bytes)
            } else {
                iprpPayload.append(contentsOf: original[child.start..<child.end])
            }
        }
        if let newIPMA = newItemsIPMA() { iprpPayload.append(contentsOf: newIPMA) }
        var bytes: [UInt8] = []
        appendBE(UInt32(8 + iprpPayload.count), into: &bytes)
        bytes.append(contentsOf: Array("iprp".utf8))
        bytes.append(contentsOf: iprpPayload)
        return bytes
    }

    private func newItemsIPMA() -> [UInt8]? {
        let newIDs = items.dropFirst(originalItemCount).map(\.id)
        guard !newIDs.isEmpty else { return nil }
        var payload: [UInt8] = []
        appendBE(UInt32(newIDs.count), into: &payload)
        for id in newIDs {
            let props = associations[id] ?? []
            payload.append(UInt8((id >> 8) & 0xff))
            payload.append(UInt8(id & 0xff))
            payload.append(UInt8(props.count))
            for association in props {
                let value = association.index | (association.essential ? 0x8000 : 0)
                payload.append(UInt8((value >> 8) & 0xff))
                payload.append(UInt8(value & 0xff))
            }
        }
        var bytes: [UInt8] = []
        appendBE(UInt32(12 + payload.count), into: &bytes)
        bytes.append(contentsOf: Array("ipma".utf8))
        bytes.append(contentsOf: [0, 0, 0, 1])
        bytes.append(contentsOf: payload)
        return bytes
    }

    /// Entity-to-group entries of one grpl box, or nil when neither the FullBox nor the
    /// plain-children interpretation parses cleanly. Each entry body is the layout Apple
    /// and this writer both use: FullBox version/flags, group id, entity count, u32 ids.
    private func groupEntries(_ grpl: RawBox) -> [(id: UInt32, members: [UInt32])]? {
        for offset in [4, 0] {
            let start = grpl.payload + offset
            guard start <= grpl.end,
                  let groups = try? Self.boxes(in: original, from: start, to: grpl.end),
                  let last = groups.last, !groups.isEmpty, last.end == grpl.end else { continue }
            var entries: [(UInt32, [UInt32])] = []
            var valid = true
            for group in groups {
                let length = group.end - group.payload
                guard length >= 12, (length - 12) % 4 == 0 else { valid = false; break }
                var reader = Reader(original, group.payload, group.end)
                guard let _ = try? reader.u32(), let id = try? reader.u32(),
                      let count = try? Int(reader.u32()), count <= 4096,
                      length == 12 + count * 4, id <= 0xFFFF else { valid = false; break }
                var members: [UInt32] = []
                for _ in 0..<count {
                    guard let member = try? reader.u32() else { valid = false; break }
                    members.append(member)
                }
                if !valid { break }
                entries.append((id, members))
            }
            if valid { return entries }
        }
        return nil
    }

    private func reservedGroupIDs() -> Set<UInt32> {
        var reserved = Set<UInt32>()
        for grpl in metaChildren where grpl.type == "grpl" {
            reserved.formUnion((groupEntries(grpl) ?? []).map(\.id))
        }
        return reserved
    }

    private func hasAltrGroup(containing id: UInt32) -> Bool {
        for grpl in metaChildren where grpl.type == "grpl" {
            for entry in groupEntries(grpl) ?? [] where entry.members.contains(id) {
                return true
            }
        }
        return false
    }
}

nonisolated private func u32(_ bytes: [UInt8], _ at: Int) -> UInt32 {
    (UInt32(bytes[at]) << 24) | (UInt32(bytes[at + 1]) << 16) | (UInt32(bytes[at + 2]) << 8) | UInt32(bytes[at + 3])
}

nonisolated private func u64(_ bytes: [UInt8], _ at: Int) -> UInt64 {
    (UInt64(u32(bytes, at)) << 32) | UInt64(u32(bytes, at + 4))
}

nonisolated private func appendBE(_ value: UInt32, into out: inout [UInt8]) {
    out.append(UInt8((value >> 24) & 0xff))
    out.append(UInt8((value >> 16) & 0xff))
    out.append(UInt8((value >> 8) & 0xff))
    out.append(UInt8(value & 0xff))
}
