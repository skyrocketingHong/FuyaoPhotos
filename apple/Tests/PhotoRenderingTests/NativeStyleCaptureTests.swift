import Testing
import Foundation
import ImageIO
import AVFoundation
@testable import PhotoRenderingCore

struct NativeStyleCaptureTests {
    private static var sampleDirectory: String? { ProcessInfo.processInfo.environment["FUYAO_STYLE_SAMPLE_DIR"] }

    @Test(.enabled(if: sampleDirectory != nil))
    func readsNativeVariableApertureAndStyles() async throws {
        let folder = URL(fileURLWithPath: try #require(Self.sampleDirectory))
        let photo = folder.appendingPathComponent("IMG_0311.HEIC")
        let movie = folder.appendingPathComponent("IMG_0311.MOV")
        let capture = try await CardImageProcessor.shared.read(photo, author: "")
        #expect(capture.card[.camera].contains("6.93mm"))
        #expect(capture.card[.aperture] == "1.8")
        #expect(capture.card[.focalLength] == "24 MM")
        #expect(capture.card[.photographicStyle] == "Standard")
        let coverage = StyleInjection.stylesCoverage(in: photo)
        #expect(coverage.photographic && coverage.texture)
        let report = MediaMetadataReportReader.read(url: photo, isLivePhoto: true)
        #expect(report.sections.flatMap(\.rows).contains { $0.labelKey == "metadata.report.texturePreset" && $0.value == "Standard" })
        try await LivePhotoPair.validate(photo: photo, movie: movie)
        #expect(try await LivePhotoMotionMovie.coverTime(AVURLAsset(url: movie)) >= 0)
    }

    @Test(.enabled(if: sampleDirectory != nil))
    func clearingLocationKeepsNativeImageStylePayloads() throws {
        let source = URL(fileURLWithPath: try #require(Self.sampleDirectory)).appendingPathComponent("IMG_0311.HEIC")
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let output = folder.appendingPathComponent("metadata.heic")
        var options = CardSaveOptions(); options.keepLocation = false
        try MetadataImageWriter.write(source, to: output, options: options)
        func payloads(_ url: URL) throws -> [String: Data] {
            let container = try HeifContainer.load(fileURL: url)
            var result: [String: Data] = [:]
            for item in container.items {
                if let type = container.contentType(of: item), type.contains("photo:metadata:") {
                    result[type] = Data(try container.payload(of: item.id))
                }
            }
            return result
        }
        let before = try payloads(source)
        #expect(before.count == 2)
        #expect(try payloads(output) == before)
    }

    @Test(.enabled(if: sampleDirectory != nil))
    func clearingLocationKeepsMovieRenderingMetadata() async throws {
        let source = URL(fileURLWithPath: try #require(Self.sampleDirectory)).appendingPathComponent("IMG_0311.MOV")
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let output = folder.appendingPathComponent("metadata.mov")
        var options = CardSaveOptions(); options.keepLocation = false
        try await LivePhotoMovie.copy(from: source, to: output, options: options)
        func styleValues(_ url: URL) async throws -> [String: String] {
            var result: [String: String] = [:]
            for item in try await AVURLAsset(url: url).load(.metadata) {
                let key = item.identifier?.rawValue ?? ""
                if key.contains("smartstyle") || key.contains("texturestyle") {
                    result[key] = try await item.load(.value).map(String.init(describing:))
                }
            }
            return result
        }
        let before = try await styleValues(source)
        #expect(before.count >= 10)
        #expect(try await styleValues(output) == before)
        let originalTracks = try await AVURLAsset(url: source).load(.tracks)
        let outputTracks = try await AVURLAsset(url: output).load(.tracks)
        #expect(originalTracks.map({ $0.mediaType.rawValue }).sorted() == outputTracks.map({ $0.mediaType.rawValue }).sorted())
        let auxiliary = AVMediaType(rawValue: "auxv")
        #expect(originalTracks.filter { $0.mediaType == auxiliary }.count == 4)
        #expect(try await LivePhotoMovie.sampleDigest(source, type: auxiliary) == LivePhotoMovie.sampleDigest(output, type: auxiliary))
        #expect(try await LivePhotoMotionMovie.coverTime(AVURLAsset(url: source)) == LivePhotoMotionMovie.coverTime(AVURLAsset(url: output)))
    }
}
