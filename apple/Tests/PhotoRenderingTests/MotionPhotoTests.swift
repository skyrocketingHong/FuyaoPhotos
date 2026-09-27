import Testing
import Foundation
import ImageIO
import CoreImage
import UniformTypeIdentifiers
@testable import PhotoRenderingCore

struct MotionPhotoTests {
    @Test func motionDirectoryAndVideoLengthAreWritten() throws {
        let minimal = Data([0xff, 0xd8, 0xff, 0xd9])
        let result = try MotionPhotoJPEG.rewrite(minimal, videoLength: 4096, timestamp: 800_000)
        let text = String(decoding: result, as: UTF8.self)
        #expect(text.contains("MotionPhoto=\"1\""))
        #expect(text.contains("Length=\"4096\""))
        #expect(text.contains("PresentationTimestampUs=\"800000\""))
        #expect(result.suffix(2) == minimal.suffix(2))
    }
    @Test func existingXMPIsMergedAndUnsafeXMPIsRejected() throws {
        func jpeg(_ xml: String) -> Data {
            let payload = MotionPhotoJPEG.xmpPrefix + Data(xml.utf8)
            let n = payload.count + 2
            return Data([0xff, 0xd8, 0xff, 0xe1, UInt8(n >> 8), UInt8(n & 255)]) + payload + Data([0xff, 0xd9])
        }
        let xml = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description xmlns:dc=\"http://purl.org/dc/elements/1.1/\" dc:description=\"A &amp; B\"/></rdf:RDF></x:xmpmeta>"
        let result = try MotionPhotoJPEG.rewrite(jpeg(xml + "\0"), videoLength: 2000, timestamp: -1)
        let text = String(decoding: result, as: UTF8.self)
        #expect(text.contains("A &amp; B"))
        #expect(text.components(separatedBy: "http://ns.adobe.com/xap/1.0/").count == 2)
        #expect(throws: (any Error).self) { try MotionPhotoJPEG.rewrite(jpeg("<!DOCTYPE root>" + xml), videoLength: 100, timestamp: -1) }
    }

    @Test(.enabled(if: ProcessInfo.processInfo.environment["FUYAO_LIVE_SAMPLE"] != nil))
    func realLivePhotoConvertsWithoutChangingVideoSamples() async throws {
        let folder = URL(fileURLWithPath: try #require(ProcessInfo.processInfo.environment["FUYAO_LIVE_SAMPLE"]))
        let output = FileManager.default.temporaryDirectory.appendingPathComponent("fuyao-motion-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: output) }
        let jpeg = output.appendingPathComponent("still.jpg")
        let mp4 = output.appendingPathComponent("video.mp4")
        let motion = output.appendingPathComponent("motion.jpg")
        let source = ProcessInfo.processInfo.environment["FUYAO_HDR_SAMPLE"].map { URL(fileURLWithPath: $0) }
            ?? folder.appendingPathComponent("IMG_8565.HEIC")
        try await CardImageProcessor.shared.export(source, card: PhotoCard(), options: CardSaveOptions(keepLocation: true),
            hdr: true, to: jpeg, live: false)
        let timestamp = try await LivePhotoMotionMovie.write(folder.appendingPathComponent("IMG_8565.MOV"), to: mp4)
        try MotionPhotoJPEG.assemble(jpeg: jpeg, movie: mp4, timestampMicroseconds: timestamp, destination: motion)
        let before = try #require(CGImageSourceCreateWithURL(jpeg as CFURL, nil))
        let after = try #require(CGImageSourceCreateWithURL(motion as CFURL, nil))
        for type in [kCGImageAuxiliaryDataTypeHDRGainMap, kCGImageAuxiliaryDataTypeISOGainMap] {
            #expect((CGImageSourceCopyAuxiliaryDataInfoAtIndex(before, 0, type) != nil) ==
                    (CGImageSourceCopyAuxiliaryDataInfoAtIndex(after, 0, type) != nil))
        }
        let video = try Data(contentsOf: mp4)
        #expect(try Data(contentsOf: motion).suffix(video.count) == video)
        if let path = ProcessInfo.processInfo.environment["FUYAO_MOTION_OUTPUT"] {
            try Data(contentsOf: motion).write(to: URL(fileURLWithPath: path))
        }
    }
}
