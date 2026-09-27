import Testing
import Foundation
import CoreImage
import ImageIO
@testable import PhotoRenderingCore

struct MediaMetadataReportTests {
    @Test func plainHEICReportsContainerColorAndAbsenceFacts() throws {
        let url = try makeHEIC(width: 256, height: 192, hdr: false)
        let report = MediaMetadataReportReader.read(url: url, isLivePhoto: false)

        let brands = try #require(row(report, "metadata.report.section.container", "metadata.report.brands"))
        #expect(brands.value?.contains("heic") == true)
        let depth = try #require(row(report, "metadata.report.section.container", "metadata.report.bitDepth"))
        #expect(depth.value == "8 bit")
        let hdr = try #require(row(report, "metadata.report.section.hdr", "metadata.report.hdrStandard"))
        #expect(hdr.valueKey == MediaMetadataReport.ValueKeys.none)
        #expect(row(report, "metadata.report.section.styles", "metadata.report.stylesStandard")?.valueKey
            == MediaMetadataReport.ValueKeys.none)
        #expect(section(report, "metadata.report.section.motion") == nil)
    }

    @Test func injectedStylesShowInTheReport() throws {
        let source = try makeHEIC(width: 256, height: 192, hdr: false)
        let output = source.deletingLastPathComponent().appendingPathComponent("styled.heic")
        try StyleInjection.inject(source: source, kind: .stillHEIC, hdr: false, addPhotographic: true,
                                  addTexture: true, grainSeedName: "IMG_8565.HEIC", destination: output)
        let report = MediaMetadataReportReader.read(url: output, isLivePhoto: false)
        #expect(row(report, "metadata.report.section.styles", "metadata.report.stylesStandard")?.valueKey
            == MediaMetadataReport.ValueKeys.styles2023)
        #expect(row(report, "metadata.report.section.styles", "metadata.report.stylesTexture")?.valueKey
            == MediaMetadataReport.ValueKeys.styles2026)
        let makerNote = try #require(row(report, "metadata.report.section.vendor", "metadata.report.appleMakerNote"))
        #expect(makerNote.value?.contains("43") == true)
        #expect(makerNote.value?.contains("84") == true)
    }

    @Test func hdrHEICReportsTheISOGainMap() throws {
        let url = try makeHEIC(width: 256, height: 256, hdr: true)
        let report = MediaMetadataReportReader.read(url: url, isLivePhoto: false)
        let standard = try #require(row(report, "metadata.report.section.hdr", "metadata.report.hdrStandard"))
        #expect(standard.valueKey == MediaMetadataReport.ValueKeys.isoGainMap)
        #expect(row(report, "metadata.report.section.hdr", "metadata.report.hdrParameters") != nil)
    }

    @Test func jpegReportsUltraHDRAndGoogleMotion() throws {
        let url = try makeHDRJPEG()
        var report = MediaMetadataReportReader.read(url: url, isLivePhoto: false)
        // ImageIO writes the ISO 21496-1 embedding without the Google hdrgm XMP.
        #expect(row(report, "metadata.report.section.hdr", "metadata.report.hdrStandard")?.valueKey
            == MediaMetadataReport.ValueKeys.isoGainMap)

        // Append a Google motion XMP declaration and trailing video bytes to the same file.
        let marked = url.deletingLastPathComponent().appendingPathComponent("motion.jpg")
        var bytes = try Data(contentsOf: url)
        let xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF " +
            "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\" " +
            "GCamera:MotionPhoto=\"1\" GCamera:MotionPhotoVersion=\"1\"/></rdf:RDF></x:xmpmeta>"
        let prefix = Data("http://ns.adobe.com/xap/1.0/\0".utf8)
        var segment = Data([0xff, 0xe1])
        let payload = prefix + Data(xmp.utf8)
        segment.append(UInt16(payload.count + 2).bigEndianData)
        segment.append(payload)
        bytes.insert(contentsOf: segment, at: 2)
        bytes.append(Data([0x00, 0x01, 0x02, 0x03]))
        try bytes.write(to: marked)

        report = MediaMetadataReportReader.read(url: marked, isLivePhoto: false)
        #expect(row(report, "metadata.report.section.motion", "metadata.report.motionStandard")?.valueKey
            == MediaMetadataReport.ValueKeys.googleMotion)
        #expect(row(report, "metadata.report.section.motion", "metadata.report.trailingVideo") != nil)
        #expect(row(report, "metadata.report.section.hdr", "metadata.report.hdrStandard")?.valueKey
            == MediaMetadataReport.ValueKeys.isoGainMap)
        let format = try #require(row(report, "metadata.report.section.container", "metadata.report.format"))
        #expect(format.value == "JPEG image")
    }

    // MARK: Helpers

    private func section(_ report: MediaMetadataReport, _ titleKey: String) -> MediaMetadataReport.Section? {
        report.sections.first { $0.titleKey == titleKey }
    }

    private func row(_ report: MediaMetadataReport, _ titleKey: String, _ labelKey: String) -> MediaMetadataReport.Row? {
        section(report, titleKey)?.rows.first { $0.labelKey == labelKey }
    }

    private func makeHEIC(width: Int, height: Int, hdr: Bool) throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let url = folder.appendingPathComponent("source.heic")
        let bounds = CGRect(x: 0, y: 0, width: width, height: height)
        let base = CIImage(color: CIColor(red: 0.4, green: 0.3, blue: 0.5)).cropped(to: bounds)
        var options: [CIImageRepresentationOption: Any] = [:]
        if hdr {
            let hdrImage = base.applyingFilter("CIExposureAdjust", parameters: [kCIInputEVKey: 2]).settingContentHeadroom(4)
            options[.hdrImage] = hdrImage
        }
        try CIContext().writeHEIFRepresentation(of: base, to: url, format: .RGBA8,
                                                colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, options: options)
        return url
    }

    private func makeHDRJPEG() throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let url = folder.appendingPathComponent("source.jpg")
        let bounds = CGRect(x: 0, y: 0, width: 256, height: 192)
        let base = CIImage(color: CIColor(red: 0.4, green: 0.3, blue: 0.5)).cropped(to: bounds)
        let hdrImage = base.applyingFilter("CIExposureAdjust", parameters: [kCIInputEVKey: 2]).settingContentHeadroom(4)
        let context = CIContext(options: [.cacheIntermediates: false])
        guard let rendered = context.createCGImage(hdrImage, from: hdrImage.extent, format: .RGBAh,
                                                   colorSpace: CGColorSpace(name: CGColorSpace.extendedLinearDisplayP3)!,
                                                   deferred: false, calculateHDRStats: true) else {
            throw MediaContainerError.invalid("hdr render")
        }
        guard let destination = CGImageDestinationCreateWithURL(url as CFURL, "public.jpeg" as CFString, 1, nil) else {
            throw MediaContainerError.invalid("jpeg destination")
        }
        let properties: [String: Any] = [kCGImageDestinationEncodeRequest as String: kCGImageDestinationEncodeToISOGainmap]
        CGImageDestinationAddImage(destination, rendered, properties as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { throw MediaContainerError.invalid("jpeg finalize") }
        return url
    }
}

private extension UInt16 {
    var bigEndianData: Data {
        Data([UInt8(self >> 8), UInt8(self & 0xff)])
    }
}
