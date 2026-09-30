import Foundation
import CryptoKit
import ImageIO
import AVFoundation

nonisolated enum FuyaoPhotosPackage {
    static let fileExtension = "fuyaophotos"
    static let mimeType = "application/vnd.fuyaophotos"
    static let magic = Data(Array("FUYAOPHOTOS".utf8) + [0, 1, 13, 10, 26])
    static let maximumResourceBytes: Int64 = 512 * 1024 * 1024
    static let maximumBytes = maximumResourceBytes * 2 + 32_788

    struct Resource: Codable, Equatable, Sendable {
        let fileExtension: String
        let bytes: Int64
        let sha256: String
        var valid: Bool { bytes > 0 && bytes <= maximumResourceBytes && sha256.count == 64
            && sha256.utf8.allSatisfy { (48...57).contains($0) || (97...102).contains($0) } }
    }
    struct Manifest: Codable, Equatable, Sendable {
        let format: String
        let version: Int
        let assetIdentifier: String
        let stillImageTimeUs: Int64
        let photo: Resource
        let movie: Resource
        var valid: Bool { format == "fuyaophotos.live-photo" && version == 1
            && UUID(uuidString: assetIdentifier) != nil && assetIdentifier.count == 36
            && stillImageTimeUs >= 0 && stillImageTimeUs <= 60_000_000
            && photo.valid && movie.valid && ["jpg", "heic"].contains(photo.fileExtension) && movie.fileExtension == "mov" }
    }
    struct Contents: Sendable {
        let manifest: Manifest
        let photo: URL
        let movie: URL
    }

    static func write(photo: URL, movie: URL, identifier: String, stillImageTimeUs: Int64, to destination: URL) throws {
        let suffix = photo.pathExtension.lowercased()
        let header = Manifest(format: "fuyaophotos.live-photo", version: 1, assetIdentifier: identifier,
            stillImageTimeUs: stillImageTimeUs,
            photo: try resource(photo, suffix: suffix == "jpeg" ? "jpg" : suffix), movie: try resource(movie, suffix: "mov"))
        guard header.valid else { throw CardError.invalidPackage }
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        let json = try encoder.encode(header)
        guard json.count <= 32_768 else { throw CardError.invalidPackage }
        guard FileManager.default.createFile(atPath: destination.path, contents: nil) else { throw CardError.exportFailed }
        do {
            let output = try FileHandle(forWritingTo: destination)
            defer { try? output.close() }
            try output.write(contentsOf: magic)
            var size = UInt32(json.count).bigEndian
            try withUnsafeBytes(of: &size) { try output.write(contentsOf: $0) }
            try output.write(contentsOf: json)
            for (url, resource) in [(photo, header.photo), (movie, header.movie)] {
                let input = try FileHandle(forReadingFrom: url)
                defer { try? input.close() }
                guard try copy(input, to: output, count: resource.bytes) == resource.sha256,
                      try input.read(upToCount: 1)?.isEmpty != false else { throw CardError.invalidPackage }
            }
        } catch { try? FileManager.default.removeItem(at: destination); throw error }
    }

    static func read(_ source: URL, into directory: URL) throws -> Contents {
        let length = Int64(try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0)
        guard length > 20, length <= maximumBytes else { throw CardError.invalidPackage }
        let input = try FileHandle(forReadingFrom: source)
        defer { try? input.close() }
        guard try input.read(upToCount: magic.count) == magic,
              let raw = try input.read(upToCount: 4), raw.count == 4 else { throw CardError.invalidPackage }
        let count = raw.reduce(0) { $0 << 8 | Int($1) }
        guard (1...32_768).contains(count), let json = try input.read(upToCount: count), json.count == count,
              let header = try? JSONDecoder().decode(Manifest.self, from: json), header.valid,
              length == 20 + Int64(count) + header.photo.bytes + header.movie.bytes else { throw CardError.invalidPackage }
        // ASVS 5.2.1 / 5.3.2: no compression, paths or symlinks; extract two bounded resources under owned names.
        let photo = directory.appendingPathComponent("photo." + header.photo.fileExtension)
        let movie = directory.appendingPathComponent("video.mov")
        guard !FileManager.default.fileExists(atPath: photo.path), !FileManager.default.fileExists(atPath: movie.path) else {
            throw CardError.invalidPackage
        }
        do {
            for (url, resource) in [(photo, header.photo), (movie, header.movie)] {
                guard FileManager.default.createFile(atPath: url.path, contents: nil) else { throw CardError.exportFailed }
                let output = try FileHandle(forWritingTo: url)
                defer { try? output.close() }
                guard try copy(input, to: output, count: resource.bytes) == resource.sha256 else { throw CardError.invalidPackage }
            }
            return Contents(manifest: header, photo: photo, movie: movie)
        } catch {
            try? FileManager.default.removeItem(at: photo); try? FileManager.default.removeItem(at: movie)
            throw error
        }
    }

    static func validateLivePhoto(_ contents: Contents) async throws {
        let inspection = try PhotoMediaInspector.inspect(contents.photo)
        guard [.stillJPEG, .ultraHDRJPEG, .stillHEIC, .heicWithAuxiliaryData].contains(inspection.kind),
              let source = CGImageSourceCreateWithURL(contents.photo as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any],
              let note = properties[kCGImagePropertyMakerAppleDictionary as String] as? [String: Any],
              let identifier = note["17"] as? String, identifier == contents.manifest.assetIdentifier,
              try await LivePhotoMovie.contentIdentifier(contents.movie) == identifier else { throw CardError.invalidPackage }
        let time = try await LivePhotoMotionMovie.coverTime(AVURLAsset(url: contents.movie))
        guard abs(time - contents.manifest.stillImageTimeUs) <= 2_000 else { throw CardError.invalidPackage }
        let styles = StyleInjection.stylesCoverage(in: contents.photo)
        if styles.photographic { try await LivePhotoTextureMetadata.requirePhotographicTrack(contents.movie) }
        if styles.texture, try await LivePhotoTextureMetadata.movieNeedsTextureTrack(contents.movie) { throw CardError.invalidPackage }
        try await LivePhotoPair.validate(photo: contents.photo, movie: contents.movie)
    }

    private static func resource(_ url: URL, suffix: String) throws -> Resource {
        let size = Int64(try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0)
        guard size > 0, size <= maximumResourceBytes else { throw CardError.invalidPackage }
        let input = try FileHandle(forReadingFrom: url); defer { try? input.close() }
        return Resource(fileExtension: suffix, bytes: size, sha256: try copy(input, to: nil, count: size))
    }

    private static func copy(_ input: FileHandle, to output: FileHandle?, count: Int64) throws -> String {
        var remaining = count; var digest = SHA256()
        while remaining > 0 {
            try Task.checkCancellation()
            guard let bytes = try input.read(upToCount: Int(min(remaining, 65_536))), !bytes.isEmpty else { throw CardError.invalidPackage }
            digest.update(data: bytes); try output?.write(contentsOf: bytes); remaining -= Int64(bytes.count)
        }
        return digest.finalize().map { String(format: "%02x", $0) }.joined()
    }
}
