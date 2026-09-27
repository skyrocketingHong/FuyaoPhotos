import Foundation
import CoreImage
import ImageIO
import VideoToolbox
import CoreMedia

nonisolated enum HevcAuxStillError: Error {
    case encodingFailed(String)
}

/// Single-frame 8-bit HEVC stills for the style aux items. ImageIO always writes grid
/// primaries, which cannot feed a single hvc1 aux item, so frames go through
/// VideoToolbox directly (the analogue of the Android MediaCodec path): annex-B output
/// is split into parameter sets and slices, the slices become the length-prefixed item
/// payload, and the hvcC record is assembled from the SPS.
nonisolated enum HevcAuxStill {
    nonisolated struct EncodedStill {
        var payload: [UInt8]
        var properties: [[UInt8]]
    }

    /// Black frame whose hvcC declares monochrome like Apple's own matte items, though
    /// the coded stream stays 4:2:0 with neutral chroma; the Android port verified that
    /// this declaration combination passes the Photos matte gate.
    static func blackFrame(width: Int, height: Int) throws -> EncodedStill {
        guard width >= 2, width <= 4096, height >= 2, height <= 4096, width % 2 == 0, height % 2 == 0 else {
            throw HevcAuxStillError.encodingFailed("black frame size \(width)x\(height)")
        }
        let context = try bitmapContext(width: width, height: height)
        context.setFillColor(CGColor(gray: 0, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        return try encodedFrame(in: context, width: width, height: height, monochrome: true)
    }

    /// 4:3 center crop of the source's SDR rendition, scaled to 1024x768, matching the
    /// Android linear thumbnail. The SDR tone map keeps HDR sources from dragging their
    /// gain map into an aux image that must stay tone-map free.
    static func linearThumbnail(source: URL) throws -> EncodedStill {
        guard let image = CIImage(contentsOf: source,
                                  options: [.applyOrientationProperty: true, .expandToHDR: false,
                                            .toneMapHDRtoSDR: true]) else {
            throw HevcAuxStillError.encodingFailed("linear thumbnail decode")
        }
        let extent = image.extent
        let target: CGFloat = 4 / 3
        let ratio = extent.width / extent.height
        let cropWidth = ratio > target ? (extent.height * target).rounded(.down) : extent.width
        let cropHeight = ratio > target ? extent.height : (extent.width / target).rounded(.down)
        let cropX = (extent.width - cropWidth) / 2
        let cropY = (extent.height - cropHeight) / 2
        let cropped = image.cropped(to: CGRect(x: cropX, y: cropY, width: cropWidth, height: cropHeight))
        let scaled = cropped.transformed(by: CGAffineTransform(scaleX: 1024 / cropWidth, y: 768 / cropHeight))
        let context = CIContext(options: [.cacheIntermediates: false])
        guard let rendered = context.createCGImage(scaled, from: CGRect(x: 0, y: 0, width: 1024, height: 768),
                                                   format: .RGBA8,
                                                   colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!,
                                                   deferred: false) else {
            throw HevcAuxStillError.encodingFailed("linear thumbnail render")
        }
        let encoder = try bitmapContext(width: 1024, height: 768)
        encoder.draw(rendered, in: CGRect(x: 0, y: 0, width: 1024, height: 768))
        return try encodedFrame(in: encoder, width: 1024, height: 768, monochrome: false)
    }

    private static func bitmapContext(width: Int, height: Int) throws -> CGContext {
        guard let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                                      bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.displayP3)!,
                                      bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue
                                          | CGImageByteOrderInfo.order32Little.rawValue) else {
            throw HevcAuxStillError.encodingFailed("bitmap context \(width)x\(height)")
        }
        return context
    }

    private static func encodedFrame(in context: CGContext, width: Int, height: Int,
                                     monochrome: Bool) throws -> EncodedStill {
        let units = try compress(width: width, height: height, baseAddress: context.data!,
                                 bytesPerRow: context.bytesPerRow)
        var orderedTypes: [UInt8] = []
        var parameterSets: [UInt8: [UInt8]] = [:]
        var slices: [[UInt8]] = []
        for unit in units where unit.count >= 2 {
            let type = (unit[0] >> 1) & 0x3f
            if type == 32 || type == 33 || type == 34 {
                if parameterSets[type] == nil {
                    orderedTypes.append(type)
                    parameterSets[type] = unit
                }
            } else {
                slices.append(unit)
            }
        }
        guard !slices.isEmpty, let sps = parameterSets[33], sps.count >= 15 else {
            throw HevcAuxStillError.encodingFailed("hevc stream lacks parameter sets or slices")
        }
        let sets = orderedTypes.compactMap { type in parameterSets[type].map { (type: type, nals: [$0]) } }
        let hvcC = try hvcCBox(sets, chromaFormatIdc: monochrome ? 0 : 1)
        var payload: [UInt8] = []
        for slice in slices {
            appendBE(UInt32(slice.count), into: &payload)
            payload.append(contentsOf: slice)
        }
        let properties = [
            HeifContainer.ispeBox(width, height),
            HeifContainer.pixiBox(channels: monochrome ? [8] : [8, 8, 8]),
            hvcC,
        ]
        return EncodedStill(payload: payload, properties: properties)
    }

    private static func compress(width: Int, height: Int, baseAddress: UnsafeMutableRawPointer,
                                 bytesPerRow: Int) throws -> [[UInt8]] {
        var pixelBuffer: CVPixelBuffer?
        let attributes = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA] as CFDictionary
        let status = CVPixelBufferCreateWithBytes(kCFAllocatorDefault, width, height, kCVPixelFormatType_32BGRA,
                                                  baseAddress, bytesPerRow, nil, nil, attributes, &pixelBuffer)
        guard status == noErr, let buffer = pixelBuffer else {
            throw HevcAuxStillError.encodingFailed("pixel buffer \(status)")
        }
        let collector = OutputCollector()
        let refCon = Unmanaged.passRetained(collector).toOpaque()
        defer { Unmanaged.passRetained(collector).release() }
        var session: VTCompressionSession?
        let callback: VTCompressionOutputCallback = { refCon, _, _, _, sampleBuffer in
            HevcAuxStill.collectOutput(refCon, sampleBuffer)
        }
        guard VTCompressionSessionCreate(allocator: nil, width: Int32(width), height: Int32(height),
                                         codecType: kCMVideoCodecType_HEVC, encoderSpecification: nil,
                                         imageBufferAttributes: nil, compressedDataAllocator: nil,
                                         outputCallback: callback, refcon: refCon,
                                         compressionSessionOut: &session) == noErr, let session else {
            throw HevcAuxStillError.encodingFailed("vt session create")
        }
        defer { VTCompressionSessionInvalidate(session) }
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_ProfileLevel,
                             value: kVTProfileLevel_HEVC_Main_AutoLevel)
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_AverageBitRate,
                             value: Int(width * height * 2) as CFNumber)
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_ExpectedFrameRate, value: Float(1) as CFNumber)
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_RealTime, value: kCFBooleanFalse)
        guard VTCompressionSessionPrepareToEncodeFrames(session) == noErr else {
            throw HevcAuxStillError.encodingFailed("vt prepare")
        }
        var flags = VTEncodeInfoFlags()
        guard VTCompressionSessionEncodeFrame(session, imageBuffer: buffer,
                                              presentationTimeStamp: CMTime(value: 0, timescale: 1),
                                              duration: .invalid, frameProperties: nil,
                                              sourceFrameRefcon: nil, infoFlagsOut: &flags) == noErr else {
            throw HevcAuxStillError.encodingFailed("vt encode")
        }
        guard VTCompressionSessionCompleteFrames(session, untilPresentationTimeStamp: .invalid) == noErr else {
            throw HevcAuxStillError.encodingFailed("vt complete frames")
        }
        // CompleteFrames returns after every output callback has delivered its sample.
        let units = collector.units
        guard !units.isEmpty else { throw HevcAuxStillError.encodingFailed("vt stream empty") }
        return units
    }

    /// HEVC parameter sets live in the sample's format description, not in the bitstream;
    /// the sample bytes themselves are length-prefixed (annex-B accepted as a fallback).
    fileprivate static func collectOutput(_ refCon: UnsafeMutableRawPointer?, _ sampleBuffer: CMSampleBuffer?) {
        guard let refCon, let sampleBuffer else { return }
        var units: [[UInt8]] = []
        if let format = CMSampleBufferGetFormatDescription(sampleBuffer) {
            // Query the set count first; probing past the end makes CoreMedia log a
            // kCMFormatDescriptionBridgeError on every encoded frame.
            var total = 0
            var nalHeaderLength: Int32 = 0
            guard CMVideoFormatDescriptionGetHEVCParameterSetAtIndex(
                format, parameterSetIndex: 0, parameterSetPointerOut: nil,
                parameterSetSizeOut: nil, parameterSetCountOut: &total,
                nalUnitHeaderLengthOut: &nalHeaderLength) == noErr, total > 0 else { return }
            for index in 0..<total {
                var pointer: UnsafePointer<UInt8>?
                var size = 0
                var count = 0
                let status = CMVideoFormatDescriptionGetHEVCParameterSetAtIndex(
                    format, parameterSetIndex: index, parameterSetPointerOut: &pointer,
                    parameterSetSizeOut: &size, parameterSetCountOut: &count,
                    nalUnitHeaderLengthOut: &nalHeaderLength)
                guard status == noErr, let pointer else { continue }
                units.append(Array(UnsafeBufferPointer(start: pointer, count: size)))
            }
        }
        if let block = CMSampleBufferGetDataBuffer(sampleBuffer) {
            let length = CMBlockBufferGetDataLength(block)
            var data = [UInt8](repeating: 0, count: length)
            let copied = data.withUnsafeMutableBytes {
                CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: length, destination: $0.baseAddress!)
            }
            if copied == noErr { units.append(contentsOf: parseSampleBytes(data)) }
        }
        Unmanaged<OutputCollector>.fromOpaque(refCon).takeUnretainedValue().append(units)
    }

    private final class OutputCollector: @unchecked Sendable {
        private let lock = NSLock()
        private var collected: [[UInt8]] = []

        func append(_ units: [[UInt8]]) {
            lock.lock()
            collected.append(contentsOf: units)
            lock.unlock()
        }

        var units: [[UInt8]] {
            lock.lock()
            defer { lock.unlock() }
            return collected
        }
    }

    /// VideoToolbox HEVC samples carry length-prefixed NAL units; some paths emit
    /// annex-B start codes instead, so both layouts are accepted.
    private static func parseSampleBytes(_ bytes: [UInt8]) -> [[UInt8]] {
        let hasStartCode = bytes.count >= 4 && bytes[0] == 0 && bytes[1] == 0
            && (bytes[2] == 1 || (bytes[2] == 0 && bytes[3] == 1))
        if hasStartCode { return splitAnnexB(bytes) }
        var units: [[UInt8]] = []
        var at = 0
        while at + 4 <= bytes.count {
            let length = Int(bytes[at]) << 24 | Int(bytes[at + 1]) << 16 | Int(bytes[at + 2]) << 8 | Int(bytes[at + 3])
            at += 4
            guard length >= 2, at + length <= bytes.count else { break }
            units.append(Array(bytes[at..<at + length]))
            at += length
        }
        return units
    }

    private static func splitAnnexB(_ bytes: [UInt8]) -> [[UInt8]] {
        var units: [[UInt8]] = []
        var current: [UInt8] = []
        var index = 0
        while index < bytes.count {
            let isThree = bytes[index] == 0 && index + 2 < bytes.count && bytes[index + 1] == 0 && bytes[index + 2] == 1
            let isFour = bytes[index] == 0 && index + 3 < bytes.count && bytes[index + 1] == 0
                && bytes[index + 2] == 0 && bytes[index + 3] == 1
            if isFour {
                if !current.isEmpty { units.append(current); current = [] }
                index += 4
            } else if isThree {
                if !current.isEmpty { units.append(current); current = [] }
                index += 3
            } else {
                current.append(bytes[index])
                index += 1
            }
        }
        if !current.isEmpty { units.append(current) }
        return units
    }

    /// Ports the Android HevcConfiguration.hvcBox layout: the 12-byte profile_tier_level
    /// is copied verbatim from the SPS (a shorter compatibility copy breaks platform
    /// parsers), and parameter sets are stored in first-seen order.
    private static func hvcCBox(_ parameterSets: [(type: UInt8, nals: [[UInt8]])],
                                chromaFormatIdc: UInt8) throws -> [UInt8] {
        guard let sps = parameterSets.first(where: { $0.type == 33 })?.nals.first, sps.count >= 15 else {
            throw HevcAuxStillError.encodingFailed("sps missing")
        }
        guard (Int(sps[2]) >> 1) & 7 == 0 else {
            throw HevcAuxStillError.encodingFailed("sps sub-layers")
        }
        var ptl = Array(sps[3..<15])
        if ptl[11] == 0 { ptl[11] = 0x5a }
        var payload: [UInt8] = [0x01]
        payload.append(contentsOf: ptl)
        payload.append(contentsOf: [0xf0, 0x00, 0xfc])
        payload.append(0xfc | chromaFormatIdc)
        payload.append(contentsOf: [0xf8, 0xf8])
        payload.append(contentsOf: [0x00, 0x00, 0x0b])
        payload.append(UInt8(parameterSets.count))
        for set in parameterSets {
            payload.append(0x80 | set.type)
            payload.append(UInt8((set.nals.count >> 8) & 0xff))
            payload.append(UInt8(set.nals.count & 0xff))
            for nal in set.nals {
                payload.append(UInt8((nal.count >> 8) & 0xff))
                payload.append(UInt8(nal.count & 0xff))
                payload.append(contentsOf: nal)
            }
        }
        var bytes: [UInt8] = []
        appendBE(UInt32(8 + payload.count), into: &bytes)
        bytes.append(contentsOf: Array("hvcC".utf8))
        bytes.append(contentsOf: payload)
        return bytes
    }

}

nonisolated private func appendBE(_ value: UInt32, into out: inout [UInt8]) {
    out.append(UInt8((value >> 24) & 0xff))
    out.append(UInt8((value >> 16) & 0xff))
    out.append(UInt8((value >> 8) & 0xff))
    out.append(UInt8(value & 0xff))
}
