import Foundation
import Testing
import AVFoundation
@testable import PhotoRenderingCore

struct FuyaoPhotosPackageTests {
    private static var root: URL { LensProfileFileTests.root }
    private static var exchange: URL { root.appendingPathComponent(".local/portable-exchange") }
    @Test func streamsAndRejectsDamagedPackages() throws {
        let work = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: work) }
        let photo = work.appendingPathComponent("source.jpg"), movie = work.appendingPathComponent("source.mov")
        let archive = work.appendingPathComponent("source.fuyaophotos"), output = work.appendingPathComponent("read")
        try Data([255, 216, 255, 217]).write(to: photo); try Data(repeating: 123, count: 200_000).write(to: movie)
        try FuyaoPhotosPackage.write(photo: photo, movie: movie, identifier: UUID().uuidString, stillImageTimeUs: 1_000_000, to: archive)
        try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
        let read = try FuyaoPhotosPackage.read(archive, into: output)
        #expect(try Data(contentsOf: photo) == Data(contentsOf: read.photo))
        #expect(try Data(contentsOf: movie) == Data(contentsOf: read.movie))
        try FileManager.default.removeItem(at: read.photo); try FileManager.default.removeItem(at: read.movie)
        var bytes = try Data(contentsOf: archive); bytes[bytes.count - 1] ^= 1; try bytes.write(to: archive)
        #expect(throws: CardError.invalidPackage) { try FuyaoPhotosPackage.read(archive, into: output) }
        #expect(try FileManager.default.contentsOfDirectory(atPath: output.path).isEmpty)
    }
    @Test(.enabled(if: FileManager.default.fileExists(atPath: exchange.appendingPathComponent("from-android.fuyaophotos").path)),
          arguments: ["from-android.fuyaophotos", "from-android-reopened.fuyaophotos", "from-android-heic.fuyaophotos"])
    func androidPackagePassesNativeApplePairValidation(_ filename: String) async throws {
        let work = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: work) }
        let contents = try FuyaoPhotosPackage.read(Self.exchange.appendingPathComponent(filename), into: work)
        try await FuyaoPhotosPackage.validateLivePhoto(contents)
        #expect(contents.manifest.photo.fileExtension == (filename.contains("-heic") ? "heic" : "jpg"))
        let file = try LensProfileFile.decode(Data(contentsOf: Self.exchange.appendingPathComponent("lenses-from-android.json")))
        #expect(file.lenses.count == 2)
    }
    @Test(.enabled(if: FileManager.default.fileExists(atPath: root.appendingPathComponent("docs/samples/apple-native-styles-IMG_0311/IMG_0311.MOV").path)))
    func writesNativeResourcesForAndroidInteroperability() async throws {
        let source = Self.root.appendingPathComponent("docs/samples/apple-native-styles-IMG_0311")
        let photo = source.appendingPathComponent("IMG_0311.HEIC"), movie = source.appendingPathComponent("IMG_0311.MOV")
        let identifier = try await LivePhotoMovie.contentIdentifier(movie)
        let time = try await LivePhotoMotionMovie.coverTime(AVURLAsset(url: movie))
        try FileManager.default.createDirectory(at: Self.exchange, withIntermediateDirectories: true)
        try FuyaoPhotosPackage.write(photo: photo, movie: movie, identifier: identifier, stillImageTimeUs: time,
            to: Self.exchange.appendingPathComponent("from-apple.fuyaophotos"))
        let lenses = try LensProfileFile.decode(Data(contentsOf: Self.root.appendingPathComponent("shared/fixtures/lenses-v1.json")))
        try lenses.encoded().write(to: Self.exchange.appendingPathComponent("lenses-from-apple.json"))
    }
}
