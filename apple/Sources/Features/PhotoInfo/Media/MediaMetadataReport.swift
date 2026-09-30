import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Best-effort technical metadata report for one photo file. Rows carry either a
/// localization key (rendered by the view layer) or a verbatim technical value, so the
/// reader stays free of app-localized strings and missing facts simply drop out.
nonisolated struct MediaMetadataReport: Sendable {
    nonisolated struct Row: Sendable {
        let labelKey: String
        let valueKey: String?
        let value: String?

        static func keyed(_ labelKey: String, _ valueKey: String) -> Row {
            Row(labelKey: labelKey, valueKey: valueKey, value: nil)
        }

        static func text(_ labelKey: String, _ value: String) -> Row {
            Row(labelKey: labelKey, valueKey: nil, value: value)
        }
    }

    nonisolated struct Section: Sendable {
        let titleKey: String
        let rows: [Row]
    }

    let sections: [Section]

    nonisolated enum ValueKeys {
        static let none = "metadata.report.value.none"
        static let ultraHDR = "metadata.report.value.ultrahdr"
        static let isoGainMap = "metadata.report.value.isogainmap"
        static let appleGainMap = "metadata.report.value.applegainmap"
        static let livePhoto = "metadata.report.value.livephoto"
        static let googleMotion = "metadata.report.value.googlemotion"
        static let heicMotion = "metadata.report.value.heicmotion"
        static let styles2023 = "metadata.report.value.styles2023"
        static let styles2026 = "metadata.report.value.styles2026"
    }
}

nonisolated enum MediaMetadataReportReader {
    nonisolated struct Context {
        let uti: UTType?
        let properties: [String: Any]?
        let container: HeifContainer?
        let scan: PhotoJPEGScan?
        let photographicStyle: String?
    }

    static func read(url: URL, isLivePhoto: Bool) -> MediaMetadataReport {
        // Content-first classification: after a property parse, ImageIO can report a
        // misleading container UTI for gain-map JPEGs, so the format row is derived from
        // the bytes themselves rather than CGImageSourceGetType.
        let source = CGImageSourceCreateWithURL(url as CFURL, nil)
        let properties = source.flatMap { CGImageSourceCopyPropertiesAtIndex($0, 0, nil) as? [String: Any] }
        let scan = try? PhotoMediaInspector.scanJPEG(url)
        let container: HeifContainer? = scan == nil ? (try? HeifContainer.load(fileURL: url)) : nil
        let uti: UTType?
        if scan != nil {
            uti = UTType.jpeg
        } else if let container {
            uti = container.majorBrand == "avif" ? UTType("public.avif") : UTType.heic
        } else {
            uti = source.flatMap { CGImageSourceGetType($0) as String? }.flatMap { UTType($0) }
        }
        let context = Context(uti: uti, properties: properties, container: container, scan: scan,
            photographicStyle: PhotographicStyleReader.name(properties: properties ?? [:],
                metadata: source.flatMap { CGImageSourceCopyMetadataAtIndex($0, 0, nil) }, vendorName: scan?.vendorPhotographicStyle))

        var sections: [MediaMetadataReport.Section] = []
        let groups: [(String, [MediaMetadataReport.Row])] = [
            ("metadata.report.section.container", containerRows(context)),
            ("metadata.report.section.color", colorRows(context)),
            ("metadata.report.section.hdr", hdrRows(context)),
            ("metadata.report.section.motion", motionRows(context, isLivePhoto: isLivePhoto)),
            ("metadata.report.section.styles", styleRows(context)),
            ("metadata.report.section.vendor", vendorRows(context)),
        ]
        for (titleKey, rows) in groups where !rows.isEmpty {
            sections.append(MediaMetadataReport.Section(titleKey: titleKey, rows: rows))
        }
        return MediaMetadataReport(sections: sections)
    }

    // MARK: Sections

    private static func containerRows(_ context: Context) -> [MediaMetadataReport.Row] {
        var rows: [MediaMetadataReport.Row] = []
        if let uti = context.uti {
            rows.append(.text("metadata.report.format", uti.localizedDescription ?? uti.identifier))
        }
        if let container = context.container {
            var brands = [container.majorBrand] + container.compatibleBrands
            brands.removeAll { $0.isEmpty }
            if !brands.isEmpty { rows.append(.text("metadata.report.brands", brands.joined(separator: " / "))) }
            if let depth = primaryBitDepth(container) {
                rows.append(.text("metadata.report.bitDepth", "\(depth) bit"))
            }
        }
        if let scan = context.scan, scan.trailingBytes > 0 {
            rows.append(.text("metadata.report.trailingData", byteCount(scan.trailingBytes)))
        }
        return rows
    }

    private static func colorRows(_ context: Context) -> [MediaMetadataReport.Row] {
        var rows: [MediaMetadataReport.Row] = []
        if let profile = context.properties?[kCGImagePropertyProfileName as String] as? String, !profile.isEmpty {
            rows.append(.text("metadata.report.colorProfile", profile))
        }
        if let container = context.container, let gamut = colrDescription(container) {
            rows.append(.text("metadata.report.colorGamut", gamut))
        }
        return rows
    }

    private static func hdrRows(_ context: Context) -> [MediaMetadataReport.Row] {
        var rows: [MediaMetadataReport.Row] = []
        if let scan = context.scan {
            let hasGainMapXMP = scan.xmp.range(of: "hdrgm:", options: .caseInsensitive) != nil ||
                scan.xmp.range(of: "hdr-gain-map", options: .caseInsensitive) != nil
            if hasGainMapXMP || scan.isoGainMapSegment {
                if hasGainMapXMP && scan.mpf {
                    rows.append(.keyed("metadata.report.hdrStandard", MediaMetadataReport.ValueKeys.ultraHDR))
                } else {
                    rows.append(.keyed("metadata.report.hdrStandard", MediaMetadataReport.ValueKeys.isoGainMap))
                }
                if let description = hdrgmDescription(scan.xmp) {
                    rows.append(.text("metadata.report.hdrParameters", description))
                }
            }
        } else if let container = context.container {
            if let payload = container.isoGainMapPayload(), let facts = parseISOGainMap(payload) {
                rows.append(.keyed("metadata.report.hdrStandard", MediaMetadataReport.ValueKeys.isoGainMap))
                rows.append(.text("metadata.report.hdrParameters", facts.description))
            } else if container.items.contains(where: { container.auxCURN(of: $0.id)?.contains("hdrgainmap") == true }) {
                rows.append(.keyed("metadata.report.hdrStandard", MediaMetadataReport.ValueKeys.appleGainMap))
            } else {
                rows.append(.keyed("metadata.report.hdrStandard", MediaMetadataReport.ValueKeys.none))
            }
        }
        return rows
    }

    private static func motionRows(_ context: Context, isLivePhoto: Bool) -> [MediaMetadataReport.Row] {
        var rows: [MediaMetadataReport.Row] = []
        if isLivePhoto {
            rows.append(.keyed("metadata.report.motionStandard", MediaMetadataReport.ValueKeys.livePhoto))
        }
        if let scan = context.scan {
            let motion = scan.xmp.contains("http://ns.google.com/photos/1.0/camera/") &&
                (scan.xmp.contains("MotionPhoto") || scan.xmp.contains("MicroVideo"))
            if motion && scan.trailingBytes > 0 {
                rows.append(.keyed("metadata.report.motionStandard", MediaMetadataReport.ValueKeys.googleMotion))
                rows.append(.text("metadata.report.trailingVideo", byteCount(scan.trailingBytes)))
            }
        } else if let container = context.container,
                  let length = container.embeddedMotionLength, length > 8 {
            rows.append(.keyed("metadata.report.motionStandard", MediaMetadataReport.ValueKeys.heicMotion))
            rows.append(.text("metadata.report.trailingVideo", byteCount(length)))
        }
        return rows
    }

    private static func styleRows(_ context: Context) -> [MediaMetadataReport.Row] {
        var rows: [MediaMetadataReport.Row] = []
        if let name = context.photographicStyle { rows.append(.text("card.field.photographicStyle", name)) }
        guard let container = context.container else { return rows }
        let coverage = container.stylesCoverage
        rows += [
            .keyed("metadata.report.stylesStandard",
                   coverage.photographic ? MediaMetadataReport.ValueKeys.styles2023 : MediaMetadataReport.ValueKeys.none),
            .keyed("metadata.report.stylesTexture",
                   coverage.texture ? MediaMetadataReport.ValueKeys.styles2026 : MediaMetadataReport.ValueKeys.none),
        ]
        if let item = container.items.first(where: { container.contentType(of: $0) == AppleTextureStyles.textureStylesContentType }),
           let payload = try? container.payload(of: item.id), payload.count <= 65_536,
           let values = (try? PropertyListSerialization.propertyList(from: Data(payload), options: [], format: nil)) as? [String: Any],
           let name = PhotographicStyleReader.readableName(values["Preset"] as? String) {
            rows.append(.text("metadata.report.texturePreset", name))
        }
        return rows
    }

    private static func vendorRows(_ context: Context) -> [MediaMetadataReport.Row] {
        var rows: [MediaMetadataReport.Row] = []
        if let scan = context.scan, scan.xmp.contains("http://ns.xiaomi.com/photos/1.0/camera/bokeh"),
           let rawLength = xmpInt(scan.xmp, "rawlength"), let depthLength = xmpInt(scan.xmp, "depthlength") {
            var text = "rawlength \(rawLength), depthlength \(depthLength)"
            if let orientation = xmpInt(scan.xmp, "depthOrientation") { text += ", orientation \(orientation)°" }
            rows.append(.text("metadata.report.xiaomiDepth", text))
        }
        if let container = context.container {
            if let exifItem = container.items.first(where: { $0.type == "Exif" }),
               let payload = try? container.payload(of: exifItem.id) {
                let tags = AppleStyleMetadata.makerNoteTags(exif: AppleStyleMetadata.exifApp1Payload(payload))
                if !tags.isEmpty {
                    rows.append(.text("metadata.report.appleMakerNote", tags.map(String.init).joined(separator: ", ")))
                }
            }
            let stack = container.items.compactMap { container.auxCURN(of: $0.id) }
                .filter { urn in
                    urn.contains("auxid:2") || urn.contains("portraiteffectsmatte") ||
                    urn.contains("semantic") || urn.contains("hdrgainmap")
                }
            if !stack.isEmpty {
                rows.append(.text("metadata.report.appleAuxStack", stack.count.formatted()))
            }
        }
        return rows
    }

    // MARK: Helpers

    private static func primaryBitDepth(_ container: HeifContainer) -> Int? {
        for association in container.associations(of: container.primary) {
            guard let type = container.propertyType(association.index), type == "pixi" else { continue }
            let raw = container.properties[association.index - 1]
            guard raw.count >= 13 else { continue }
            let channels = Int(raw[12])
            guard channels >= 1, raw.count == 13 + channels else { continue }
            return raw[13...].map { Int($0) }.max()
        }
        return nil
    }

    /// Maps the colr nclx box to the color-profile vocabulary macOS shows users.
    private static func colrDescription(_ container: HeifContainer) -> String? {
        for association in container.associations(of: container.primary) {
            guard let type = container.propertyType(association.index), type == "colr" else { continue }
            let raw = container.properties[association.index - 1]
            guard raw.count >= 19, String(decoding: raw[8..<12], as: UTF8.self) == "nclx" else { continue }
            let primaries = Int(raw[14]) << 8 | Int(raw[15])
            let transfer = Int(raw[16]) << 8 | Int(raw[17])
            let primariesName: String
            switch primaries {
            case 1: primariesName = "BT.709"
            case 9: primariesName = "BT.2020"
            case 12: primariesName = "DCI-P3"
            default: primariesName = "\(primaries)"
            }
            let transferName: String
            switch transfer {
            case 1, 13: transferName = "sRGB EOTF"
            case 16: transferName = "PQ"
            case 18: transferName = "HLG"
            default: transferName = "\(transfer)"
            }
            return "\(transferName) / \(primariesName)"
        }
        return nil
    }

    private static func hdrgmDescription(_ xmp: String) -> String? {
        var parts: [String] = []
        if let min = xmpDouble(xmp, "HDRCapacityMin"), let max = xmpDouble(xmp, "HDRCapacityMax") {
            parts.append(String(format: "headroom %.2f..%.2f", min, max))
        }
        if let gamma = xmpDouble(xmp, "Gamma") {
            parts.append(String(format: "gamma %.2f", gamma))
        }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }

    private nonisolated struct GainMapFacts {
        let baseHeadroom: Double
        let fullHeadroom: Double
        let gainMin: Double
        let gainMax: Double

        var description: String {
            String(format: "headroom %.2f..%.2f, gain %.2f..%.2f", baseHeadroom, fullHeadroom, gainMin, gainMax)
        }
    }

    /// ISO 21496-1 gain-map metadata: version word, minimum/writer versions, a flags byte
    /// carrying the channel count, base/full headroom rationals, five rationals per channel.
    private static func parseISOGainMap(_ payload: [UInt8]) -> GainMapFacts? {
        guard payload.count == 62 || payload.count == 142, payload[0] == 0 else { return nil }
        let channels = payload[5] == 0xc0 ? 3 : 1
        guard payload.count == 22 + channels * 40 else { return nil }
        func rational(_ at: Int) -> Double? {
            guard at + 8 <= payload.count else { return nil }
            let numerator = Int(Int32(truncatingIfNeeded: be32(payload, at)))
            let denominator = be32(payload, at + 4)
            guard denominator > 0 else { return nil }
            return Double(numerator) / Double(denominator)
        }
        guard let base = rational(6), let full = rational(14),
              let gainMin = rational(22), let gainMax = rational(30) else { return nil }
        return GainMapFacts(baseHeadroom: exp2(base), fullHeadroom: exp2(full),
                            gainMin: exp2(gainMin), gainMax: exp2(gainMax))
    }

    private static func xmpInt(_ xmp: String, _ attribute: String) -> Int? {
        guard let range = xmp.range(of: "\(attribute)=\"") else { return nil }
        let rest = xmp[range.upperBound...]
        guard let end = rest.firstIndex(of: "\"") else { return nil }
        return Int(rest[..<end])
    }

    private static func xmpDouble(_ xmp: String, _ attribute: String) -> Double? {
        guard let range = xmp.range(of: "\(attribute)=\"") else { return nil }
        let rest = xmp[range.upperBound...]
        guard let end = rest.firstIndex(of: "\"") else { return nil }
        return Double(rest[..<end])
    }

    private static func byteCount(_ value: Int) -> String {
        ByteCountFormatter.string(fromByteCount: Int64(value), countStyle: .file)
    }

    private static func be32(_ bytes: [UInt8], _ at: Int) -> UInt32 {
        (UInt32(bytes[at]) << 24) | (UInt32(bytes[at + 1]) << 16) | (UInt32(bytes[at + 2]) << 8) | UInt32(bytes[at + 3])
    }
}
