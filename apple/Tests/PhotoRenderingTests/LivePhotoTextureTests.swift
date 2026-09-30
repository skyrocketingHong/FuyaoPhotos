import Testing
import Foundation
import AVFoundation
@testable import PhotoRenderingCore

struct LivePhotoTextureTests {
    private static var samples: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("docs/samples")
    }
    private static var legacy: URL { samples.appendingPathComponent("apple-airdrop-full-IMG_8565") }
    private static var native: URL { samples.appendingPathComponent("apple-native-styles-IMG_0311") }

    @Test func containerParserRejectsUnrepresentableLargeBoxes() {
        let invalid: [UInt8] = [0, 0, 0, 1] + Array("moov".utf8) + Array(repeating: 255, count: 8)
        #expect(throws: (any Error).self) { try HeifContainer.boxes(in: invalid, from: 0, to: invalid.count) }
        #expect(throws: (any Error).self) { try HeifContainer.boxes(in: invalid, from: -1, to: invalid.count) }
    }

    @Test(.enabled(if: FileManager.default.fileExists(atPath: legacy.appendingPathComponent("IMG_8565.MOV").path)))
    func addsTextureTrackWithoutChangingNativeMediaAndPairsAfterCleaning() async throws {
        let work = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: work) }
        let source = Self.legacy.appendingPathComponent("IMG_8565.HEIC")
        let movie = Self.legacy.appendingPathComponent("IMG_8565.MOV")
        let photo = work.appendingPathComponent("style.heic"), output = work.appendingPathComponent("style.mov")
        try StyleInjection.inject(source: source, kind: .heicWithAuxiliaryData, hdr: true,
            addPhotographic: false, addTexture: true, grainSeedName: "original", destination: photo)
        #expect(try await LivePhotoTextureMetadata.movieNeedsTextureTrack(movie))
        try await LivePhotoTextureMetadata.addIfNeeded(source: movie, photo: photo, destination: output)
        #expect(try await !LivePhotoTextureMetadata.movieNeedsTextureTrack(output))
        let before = try await AVURLAsset(url: movie).load(.tracks)
        let after = try await AVURLAsset(url: output).load(.tracks)
        #expect(after.count == before.count + 1)
        #expect(try await LivePhotoMotionMovie.coverTime(AVURLAsset(url: movie)) == LivePhotoMotionMovie.coverTime(AVURLAsset(url: output)))
        try await LivePhotoPair.validate(photo: photo, movie: output)
        let cleanedPhoto = work.appendingPathComponent("clean.heic"), cleanedMovie = work.appendingPathComponent("clean.mov")
        var options = CardSaveOptions(); options.keepExif = false; options.keepCaptureTime = false; options.keepLocation = false
        try MetadataImageWriter.write(photo, to: cleanedPhoto, options: options)
        try await LivePhotoMovie.copy(from: output, to: cleanedMovie, options: options)
        #expect(try await !LivePhotoTextureMetadata.movieNeedsTextureTrack(cleanedMovie))
        try await LivePhotoPair.validate(photo: cleanedPhoto, movie: cleanedMovie)
        let values = try await AVURLAsset(url: cleanedMovie).load(.metadata)
        #expect(values.filter { $0.identifier?.rawValue.contains("texturestyle.") == true }.count == 4)
        let previouslyCleaned = work.appendingPathComponent("previously-cleaned.mov")
        let laterStyled = work.appendingPathComponent("later-styled.mov")
        try await LivePhotoMovie.copy(from: movie, to: previouslyCleaned, options: options)
        try await LivePhotoTextureMetadata.addIfNeeded(source: previouslyCleaned, photo: photo, destination: laterStyled)
        try await LivePhotoPair.validate(photo: cleanedPhoto, movie: laterStyled)
    }

    @Test(.enabled(if: FileManager.default.fileExists(atPath: native.appendingPathComponent("IMG_0311.MOV").path)))
    func existingNativeTextureTrackIsByteIdentical() async throws {
        let source = Self.native.appendingPathComponent("IMG_0311.MOV")
        let output = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".mov")
        defer { try? FileManager.default.removeItem(at: output) }
        try await LivePhotoTextureMetadata.addIfNeeded(source: source,
            photo: Self.native.appendingPathComponent("IMG_0311.HEIC"), destination: output)
        #expect(try Data(contentsOf: source) == Data(contentsOf: output))
    }
}
