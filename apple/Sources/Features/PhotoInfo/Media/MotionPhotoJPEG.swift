import Foundation
#if canImport(FoundationXML)
import FoundationXML
#endif

/// Google Motion Photo: a JPEG (including its gain map) followed by one MP4.
nonisolated enum MotionPhotoJPEG {
    static let xmpPrefix = Data("http://ns.adobe.com/xap/1.0/\0".utf8)
    struct Segment { let start: Int; let end: Int; let marker: UInt8 }

    static func assemble(jpeg: URL, movie: URL, timestampMicroseconds: Int64, destination: URL) throws {
        let videoSize = try movie.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        let photoSize = try jpeg.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard videoSize > 16, photoSize > 4, photoSize <= 512 * 1024 * 1024,
              timestampMicroseconds >= -1 else { throw CardError.invalidImage }
        let header = try rewrite(Data(contentsOf: jpeg), videoLength: videoSize, timestamp: timestampMicroseconds)
        try header.write(to: destination, options: .atomic)
        do {
            let output = try FileHandle(forWritingTo: destination)
            let input = try FileHandle(forReadingFrom: movie)
            defer { try? input.close(); try? output.close() }
            try output.seekToEnd()
            var copied = 0
            while let bytes = try input.read(upToCount: 1024 * 1024), !bytes.isEmpty {
                try Task.checkCancellation()
                try output.write(contentsOf: bytes)
                copied += bytes.count
            }
            guard copied == videoSize else { throw CardError.imageValidation }
        } catch { try? FileManager.default.removeItem(at: destination); throw error }
    }

    static func rewrite(_ input: Data, videoLength: Int, timestamp: Int64) throws -> Data {
        guard input.starts(with: [0xff, 0xd8]), videoLength > 0, timestamp >= -1 else { throw CardError.invalidImage }
        var segments: [Segment] = []
        var cursor = 2
        while cursor + 4 <= input.count {
            guard input[cursor] == 0xff else { throw CardError.invalidImage }
            let marker = input[cursor + 1]
            if marker == 0xda || marker == 0xd9 { break }
            let length = Int(input[cursor + 2]) * 256 + Int(input[cursor + 3])
            guard length >= 2, cursor + 2 + length <= input.count else { throw CardError.invalidImage }
            segments.append(Segment(start: cursor, end: cursor + length + 2, marker: marker))
            cursor += length + 2
        }
        let packets = segments.filter { $0.marker == 0xe1 && input[$0.start + 4..<$0.end].starts(with: xmpPrefix) }
        guard packets.count <= 1 else { throw CardError.imageValidation }
        let oldXMP = packets.first.map { Data(input[$0.start + 4 + xmpPrefix.count..<$0.end]) }
        let xml = try MotionPhotoXMP.updated(oldXMP, length: videoLength, timestamp: timestamp)
        let payload = xmpPrefix + xml
        guard payload.count + 2 <= 65535 else { throw CardError.imageValidation }
        let length = payload.count + 2
        let app1 = Data([0xff, 0xe1, UInt8(length >> 8), UInt8(length & 255)]) + payload
        let removed = packets.reduce(0) { $0 + $1.end - $1.start }
        let delta = app1.count - removed
        var data = input
        for segment in segments where segment.marker == 0xe2 && input[segment.start + 4..<segment.end].starts(with: Data([0x4d, 0x50, 0x46, 0])) {
            let shift = app1.count - packets.filter { $0.end <= segment.start }.reduce(0) { $0 + $1.end - $1.start }
            try adjustMPF(&data, segment: segment, sizeDelta: delta, offsetDelta: delta - shift)
        }
        var output = Data([0xff, 0xd8]) + app1
        cursor = 2
        for packet in packets {
            output.append(data[cursor..<packet.start])
            cursor = packet.end
        }
        output.append(data[cursor...])
        return output
    }

    private static func adjustMPF(_ data: inout Data, segment: Segment, sizeDelta: Int, offsetDelta: Int) throws {
        let base = segment.start + 8
        guard base + 8 <= segment.end else { throw CardError.imageValidation }
        let little = data[base] == 0x49 && data[base + 1] == 0x49
        guard little || (data[base] == 0x4d && data[base + 1] == 0x4d) else { throw CardError.imageValidation }
        func read(_ at: Int, _ count: Int) throws -> Int {
            guard at >= base, at + count <= segment.end else { throw CardError.imageValidation }
            return (0..<count).reduce(0) { value, i in value | (Int(data[at + i]) << (8 * (little ? i : count - 1 - i))) }
        }
        guard try read(base + 2, 2) == 42 else { throw CardError.imageValidation }
        let ifd = try base + read(base + 4, 4)
        let count = try read(ifd, 2)
        guard count <= 64 else { throw CardError.imageValidation }
        var updates: [(Int, Int)] = []
        for index in 0..<count {
            let entry = ifd + 2 + index * 12
            if try read(entry, 2) == 0xb002 {
                let bytes = try read(entry + 4, 4)
                let table = try base + read(entry + 8, 4)
                guard bytes >= 32, bytes % 16 == 0, bytes <= 256, table + bytes <= segment.end else { throw CardError.imageValidation }
                for item in 0..<(bytes / 16) {
                    let at = table + item * 16
                    let size = try read(at + 4, 4)
                    let offset = try read(at + 8, 4)
                    if item == 0 {
                        guard offset == 0 else { throw CardError.imageValidation }
                        updates.append((at + 4, size + sizeDelta))
                    } else {
                        guard offset > 0, base + offset + size <= data.count else { throw CardError.imageValidation }
                        updates.append((at + 8, offset + offsetDelta))
                    }
                }
            }
        }
        guard !updates.isEmpty else { throw CardError.imageValidation }
        for (at, value) in updates {
            guard value >= 0, UInt64(value) <= UInt64(UInt32.max) else { throw CardError.imageValidation }
            for i in 0..<4 { data[at + i] = UInt8((value >> (8 * (little ? i : 3 - i))) & 255) }
        }
    }
}

private nonisolated final class MotionPhotoXMP: NSObject, XMLParserDelegate {
    private let camera = "http://ns.google.com/photos/1.0/camera/"
    private let container = "http://ns.google.com/photos/1.0/container/"
    private let rdf = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private var output = ""
    private var stack: [(String, String)] = []
    private var mappings: [String: String] = [:]
    private var directories = 0
    private var inserted = false
    private var invalid = false
    private let length: Int
    private let timestamp: Int64
    init(length: Int, timestamp: Int64) { self.length = length; self.timestamp = timestamp }
    private var item: String {
        "<rdf:li rdf:parseType=\"Resource\"><FuyaoContainer:Item FuyaoItem:Mime=\"video/mp4\" FuyaoItem:Semantic=\"MotionPhoto\" FuyaoItem:Length=\"\(length)\"/></rdf:li>"
    }
    private var namespaces: String {
        "xmlns:rdf=\"\(rdf)\" xmlns:FuyaoCamera=\"\(camera)\" xmlns:FuyaoContainer=\"\(container)\" xmlns:FuyaoItem=\"http://ns.google.com/photos/1.0/container/item/\""
    }
    static func updated(_ data: Data?, length: Int, timestamp: Int64) throws -> Data {
        let worker = MotionPhotoXMP(length: length, timestamp: timestamp)
        var input = data ?? Data("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"></rdf:RDF></x:xmpmeta>".utf8)
        // ImageIO's standard XMP APP1 packet includes a terminal NUL byte.
        while input.last == 0 { input.removeLast() }
        guard input.count <= 128 * 1024, let text = String(data: input, encoding: .utf8),
              !text.uppercased().contains("<!DOCTYPE"), !text.uppercased().contains("<!ENTITY") else { throw CardError.imageValidation }
        let parser = XMLParser(data: input)
        parser.shouldProcessNamespaces = true
        parser.shouldReportNamespacePrefixes = true
        parser.shouldResolveExternalEntities = false
        parser.delegate = worker
        guard parser.parse(), worker.inserted, !worker.invalid, worker.directories <= 1 else { throw CardError.imageValidation }
        return Data(worker.output.utf8)
    }
    func parser(_ parser: XMLParser, didStartMappingPrefix prefix: String, toURI namespaceURI: String) { mappings[prefix] = namespaceURI }
    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String: String]) {
        if namespaceURI == container && elementName == "Directory" { directories += 1 }
        if namespaceURI == camera && ["MotionPhoto", "MicroVideo"].contains(elementName) { invalid = true }
        stack.append((elementName, namespaceURI ?? ""))
        output += "<" + (qName ?? elementName)
        for (prefix, uri) in mappings.sorted(by: { $0.key < $1.key }) {
            output += " \(prefix.isEmpty ? "xmlns" : "xmlns:" + prefix)=\"\(escape(uri))\""
        }
        mappings = [:]
        for (key, value) in attributeDict.sorted(by: { $0.key < $1.key }) { output += " \(key)=\"\(escape(value))\"" }
        output += ">"
    }
    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if namespaceURI == rdf && elementName == "Seq", stack.dropLast().last?.0 == "Directory", stack.dropLast().last?.1 == container {
            output += "<rdf:li \(namespaces) rdf:parseType=\"Resource\"><FuyaoContainer:Item FuyaoItem:Mime=\"video/mp4\" FuyaoItem:Semantic=\"MotionPhoto\" FuyaoItem:Length=\"\(length)\"/></rdf:li>"
        }
        if namespaceURI == rdf && elementName == "RDF" {
            output += "<rdf:Description \(namespaces) FuyaoCamera:MotionPhoto=\"1\" FuyaoCamera:MotionPhotoVersion=\"1\" FuyaoCamera:MotionPhotoPresentationTimestampUs=\"\(timestamp)\" FuyaoCamera:MicroVideo=\"1\" FuyaoCamera:MicroVideoVersion=\"1\" FuyaoCamera:MicroVideoOffset=\"\(length)\">"
            if directories == 0 {
                output += "<FuyaoContainer:Directory><rdf:Seq><rdf:li rdf:parseType=\"Resource\"><FuyaoContainer:Item FuyaoItem:Mime=\"image/jpeg\" FuyaoItem:Semantic=\"Primary\"/></rdf:li>\(item)</rdf:Seq></FuyaoContainer:Directory>"
            }
            output += "</rdf:Description>"
            inserted = true
        }
        output += "</" + (qName ?? elementName) + ">"
        _ = stack.popLast()
    }
    func parser(_ parser: XMLParser, foundCharacters string: String) { output += escape(string) }
    func parser(_ parser: XMLParser, foundCDATA CDATABlock: Data) { output += escape(String(decoding: CDATABlock, as: UTF8.self)) }
    private func escape(_ value: String) -> String {
        value.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;")
    }
}
