import Foundation
import ImageIO
import UniformTypeIdentifiers

nonisolated enum PhotoMediaKind: Sendable {
    case stillJPEG
    case stillPNG
    case stillHEIC
    case ultraHDRJPEG
    case motionJPEG
    case hdrMotionJPEG
    case xiaomiPortraitJPEG
    case heicWithAuxiliaryData
    case unsupported
}

nonisolated struct PhotoMediaInspection: Sendable {
    let kind: PhotoMediaKind
    let width: Int
    let height: Int
}

nonisolated enum PhotoMediaInspectionError: Error {
    case invalidImage
    case oversizedImage
    case malformedJPEG
}

/// Classifies original resources before an editor chooses a native write path.
nonisolated enum PhotoMediaInspector {
    static func inspect(_ url: URL) throws -> PhotoMediaInspection {
        let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
        let fileSize = (attributes[.size] as? NSNumber)?.int64Value ?? 0
        guard fileSize > 0, fileSize <= 512 * 1024 * 1024 else {
            throw PhotoMediaInspectionError.oversizedImage
        }
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              CGImageSourceGetCount(source) == 1,
              let identifier = CGImageSourceGetType(source) as String?,
              let type = UTType(identifier),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any],
              let width = (properties[kCGImagePropertyPixelWidth as String] as? NSNumber)?.intValue,
              let height = (properties[kCGImagePropertyPixelHeight as String] as? NSNumber)?.intValue,
              width > 0, height > 0, Int64(width) * Int64(height) <= 200_000_000 else {
            throw PhotoMediaInspectionError.invalidImage
        }
        let kind: PhotoMediaKind
        if type.conforms(to: .jpeg) {
            kind = try inspectJPEG(url)
        } else if type.conforms(to: .png) {
            kind = .stillPNG
        } else if type.conforms(to: .heic) || type.conforms(to: .heif) {
            let auxiliary = [kCGImageAuxiliaryDataTypeHDRGainMap, kCGImageAuxiliaryDataTypeISOGainMap,
                             kCGImageAuxiliaryDataTypeDepth, kCGImageAuxiliaryDataTypeDisparity,
                             kCGImageAuxiliaryDataTypePortraitEffectsMatte]
            kind = auxiliary.contains { CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, $0) != nil }
                ? .heicWithAuxiliaryData : .stillHEIC
        } else {
            kind = .unsupported
        }
        return PhotoMediaInspection(kind: kind, width: width, height: height)
    }

    private static func inspectJPEG(_ url: URL) throws -> PhotoMediaKind {
        let data = try Data(contentsOf: url, options: .mappedIfSafe)
        return try data.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            guard bytes.count >= 4, bytes[0] == 0xff, bytes[1] == 0xd8 else {
                throw PhotoMediaInspectionError.malformedJPEG
            }
            var cursor = 2
            var metadataBytes = 0
            var mpf = false
            var xmp = Data()
            let prefix = Data("http://ns.adobe.com/xap/1.0/\0".utf8)
            while cursor + 4 <= bytes.count && cursor <= 4 * 1024 * 1024 {
                guard bytes[cursor] == 0xff else { throw PhotoMediaInspectionError.malformedJPEG }
                while cursor < bytes.count && bytes[cursor] == 0xff { cursor += 1 }
                guard cursor < bytes.count else { throw PhotoMediaInspectionError.malformedJPEG }
                let marker = bytes[cursor]
                cursor += 1
                if marker == 0xda { break }
                guard marker != 0xd9, marker != 0xd8, marker != 0,
                      !(0xd0...0xd7).contains(marker), cursor + 2 <= bytes.count else {
                    throw PhotoMediaInspectionError.malformedJPEG
                }
                let length = Int(bytes[cursor]) << 8 | Int(bytes[cursor + 1])
                guard length >= 2, length <= bytes.count - cursor else {
                    throw PhotoMediaInspectionError.malformedJPEG
                }
                let payloadStart = cursor + 2
                let payloadEnd = cursor + length
                if marker == 0xe2, payloadEnd - payloadStart >= 4,
                   bytes[payloadStart] == 0x4d, bytes[payloadStart + 1] == 0x50,
                   bytes[payloadStart + 2] == 0x46, bytes[payloadStart + 3] == 0 {
                    mpf = true
                }
                if marker == 0xe1, payloadEnd - payloadStart >= prefix.count,
                   prefix.indices.allSatisfy({ bytes[payloadStart + $0] == prefix[$0] }) {
                    let packetStart = payloadStart + prefix.count
                    let packetLength = payloadEnd - packetStart
                    metadataBytes += packetLength
                    guard metadataBytes <= 128 * 1024 else { throw PhotoMediaInspectionError.malformedJPEG }
                    xmp.append(contentsOf: bytes[packetStart..<payloadEnd])
                }
                cursor = payloadEnd
            }
            guard cursor <= 4 * 1024 * 1024 else { throw PhotoMediaInspectionError.malformedJPEG }
            let primaryEnd = try jpegEnd(bytes)
            guard let packet = String(data: xmp, encoding: .utf8) else {
                throw PhotoMediaInspectionError.malformedJPEG
            }
            let hdr = packet.range(of: "hdr-gain-map", options: .caseInsensitive) != nil ||
                packet.range(of: "hdrgm:", options: .caseInsensitive) != nil
            let motion = packet.contains("http://ns.google.com/photos/1.0/camera/") &&
                (packet.contains("MotionPhoto") || packet.contains("MicroVideo"))
            let portrait = packet.contains("http://ns.xiaomi.com/photos/1.0/camera/bokeh") &&
                packet.contains("rawlength") && packet.contains("depthlength")
            if portrait && hdr && mpf && primaryEnd < bytes.count { return .xiaomiPortraitJPEG }
            if motion && primaryEnd < bytes.count { return hdr ? .hdrMotionJPEG : .motionJPEG }
            if hdr && mpf && primaryEnd < bytes.count { return .ultraHDRJPEG }
            if !hdr && !motion && !portrait && !mpf && primaryEnd == bytes.count { return .stillJPEG }
            return .unsupported
        }
    }

    private static func jpegEnd(_ bytes: UnsafeRawBufferPointer) throws -> Int {
        var at = 2
        var scanning = false
        var markers = 0
        while at < bytes.count {
            markers += 1
            guard markers <= 100_000 else { throw PhotoMediaInspectionError.malformedJPEG }
            if scanning {
                while at < bytes.count && bytes[at] != 0xff { at += 1 }
                guard at < bytes.count else { break }
            } else if bytes[at] != 0xff {
                throw PhotoMediaInspectionError.malformedJPEG
            }
            while at < bytes.count && bytes[at] == 0xff { at += 1 }
            guard at < bytes.count else { break }
            let marker = bytes[at]
            at += 1
            if scanning && (marker == 0 || (0xd0...0xd7).contains(marker)) { continue }
            if marker == 0xd9 { return at }
            guard marker != 0xd8, marker != 0, marker != 1,
                  !(0xd0...0xd7).contains(marker), at + 2 <= bytes.count else {
                throw PhotoMediaInspectionError.malformedJPEG
            }
            let length = Int(bytes[at]) << 8 | Int(bytes[at + 1])
            guard length >= 2, length <= bytes.count - at else {
                throw PhotoMediaInspectionError.malformedJPEG
            }
            at += length
            scanning = marker == 0xda
        }
        throw PhotoMediaInspectionError.malformedJPEG
    }
}
