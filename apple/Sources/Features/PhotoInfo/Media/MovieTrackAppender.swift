import Foundation

/// Adds a timed metadata track without remuxing the source's image, sound or auxiliary tracks.
nonisolated enum MovieTrackAppender {
    private typealias Box = HeifContainer.RawBox

    static func timeScale(of url: URL) throws -> Int32 {
        let bytes = try read(url)
        let moov = try single("moov", in: boxes(bytes, 0, bytes.count))
        let header = try single("mvhd", in: boxes(bytes, moov.payload, moov.end))
        let offset = try headerOffset(header, bytes: bytes, v0: 12, v1: 20)
        let value = uint(bytes, offset)
        guard value > 0, value <= Int32.max else { throw CardError.liveStyleMetadata }
        return Int32(value)
    }

    static func append(metadataMovie: URL, to source: URL, destination: URL, describedVideoID: UInt32) throws {
        let original = try read(source), sidecar = try read(metadataMovie)
        let sourceBoxes = try boxes(original, 0, original.count)
        let sidecarBoxes = try boxes(sidecar, 0, sidecar.count)
        guard !sourceBoxes.contains(where: { ["moof", "mfra"].contains($0.type) }) else { throw CardError.liveStyleMetadata }
        let oldMoov = try single("moov", in: sourceBoxes)
        let addedMoov = try single("moov", in: sidecarBoxes)
        let oldChildren = try boxes(original, oldMoov.payload, oldMoov.end)
        let addedChildren = try boxes(sidecar, addedMoov.payload, addedMoov.end)
        let addedTrack = try single("trak", in: addedChildren)
        let existingTracks = oldChildren.filter { $0.type == "trak" }
        var maximumID: UInt32 = 0
        for track in existingTracks {
            let tkhd = try single("tkhd", in: boxes(original, track.payload, track.end))
            let offset = try headerOffset(tkhd, bytes: original, v0: 12, v1: 20)
            maximumID = max(maximumID, uint(original, offset))
        }
        guard maximumID < UInt32.max - 1, existingTracks.count < 64 else { throw CardError.liveStyleMetadata }
        let trackID = maximumID + 1
        var output = original
        // Keep every original sample at its old offset. Clear the superseded header,
        // including descriptive metadata, before appending its updated replacement.
        replace(&output, oldMoov.start + 4, Array("free".utf8))
        output.replaceSubrange(oldMoov.payload..<oldMoov.end, with: repeatElement(UInt8(0), count: oldMoov.end - oldMoov.payload))
        if let last = sourceBoxes.last, uint(original, last.start) == 0 {
            replace(&output, last.start, be(UInt32(last.end - last.start)))
        }
        var offsets: [(Range<Int>, Int)] = []
        for mdat in sidecarBoxes where mdat.type == "mdat" {
            let newStart = output.count + 8
            output += box("mdat", Array(sidecar[mdat.payload..<mdat.end]))
            offsets.append((mdat.payload..<mdat.end, newStart))
        }
        guard !offsets.isEmpty else { throw CardError.liveStyleMetadata }

        func remap(_ value: UInt64) throws -> UInt64 {
            guard value <= Int.max, let mapping = offsets.first(where: { $0.0.contains(Int(value)) }) else {
                throw CardError.liveStyleMetadata
            }
            return UInt64(mapping.1 + Int(value) - mapping.0.lowerBound)
        }
        func trackBox(_ value: Box, depth: Int = 0) throws -> [UInt8] {
            guard depth < 8 else { throw CardError.liveStyleMetadata }
            if ["trak", "mdia", "minf", "stbl"].contains(value.type) {
                var payload: [UInt8] = []
                for child in try boxes(sidecar, value.payload, value.end) { payload += try trackBox(child, depth: depth + 1) }
                if value.type == "trak" {
                    guard !(try boxes(sidecar, value.payload, value.end)).contains(where: { $0.type == "tref" }) else {
                        throw CardError.liveStyleMetadata
                    }
                    payload += box("tref", box("cdsc", be(describedVideoID)))
                }
                return box(value.type, payload)
            }
            var raw = Array(sidecar[value.start..<value.end])
            if value.type == "tkhd" {
                let offset = try headerOffset(value, bytes: sidecar, v0: 12, v1: 20)
                replace(&raw, offset - value.start, be(trackID))
            } else if value.type == "stco" || value.type == "co64" {
                guard value.end - value.payload >= 8 else { throw CardError.liveStyleMetadata }
                let count = Int(uint(sidecar, value.payload + 4)), size = value.type == "co64" ? 8 : 4
                guard count <= 100_000, value.end - value.payload - 8 == count * size else { throw CardError.liveStyleMetadata }
                var payload = Array(sidecar[value.payload..<value.payload + 8])
                // Always emit 64-bit offsets so adding a track cannot overflow stco.
                for index in 0..<count {
                    let at = value.payload + 8 + index * size
                    let offset = size == 8 ? (UInt64(uint(sidecar, at)) << 32) | UInt64(uint(sidecar, at + 4)) : UInt64(uint(sidecar, at))
                    let mapped = try remap(offset)
                    payload += be(UInt32(mapped >> 32)) + be(UInt32(mapped & 0xffff_ffff))
                }
                return box("co64", payload)
            }
            return raw
        }

        let newMeta = try single("meta", in: addedChildren)
        guard oldChildren.filter({ $0.type == "meta" }).count <= 1 else { throw CardError.liveStyleMetadata }
        var moov: [UInt8] = []
        for child in oldChildren {
            if child.type == "meta" { moov += try mergeMeta(original, child, sidecar, newMeta) }
            else {
                var raw = Array(original[child.start..<child.end])
                if child.type == "mvhd" {
                    guard raw.count >= 12 else { throw CardError.liveStyleMetadata }
                    replace(&raw, raw.count - 4, be(trackID + 1))
                }
                moov += raw
            }
        }
        if !oldChildren.contains(where: { $0.type == "meta" }) { moov += Array(sidecar[newMeta.start..<newMeta.end]) }
        moov += try trackBox(addedTrack)
        output += box("moov", moov)
        guard output.count <= 512 * 1024 * 1024 else { throw CardError.tooLarge }
        try Data(output).write(to: destination, options: .atomic)
    }

    private static func mergeMeta(_ source: [UInt8], _ old: Box, _ sidecar: [UInt8], _ added: Box) throws -> [UInt8] {
        func children(_ data: [UInt8], _ meta: Box) throws -> [Box] {
            guard meta.end - meta.payload >= 8 else { throw CardError.liveStyleMetadata }
            return try boxes(data, meta.payload + (uint(data, meta.payload) == 0 ? 4 : 0), meta.end)
        }
        func keys(_ data: [UInt8], _ items: [Box]) throws -> [[UInt8]] {
            let keys = try single("keys", in: items)
            guard keys.end - keys.payload >= 8 else { throw CardError.liveStyleMetadata }
            let values = try boxes(data, keys.payload + 8, keys.end)
            guard values.count <= 4096, values.count == Int(uint(data, keys.payload + 4)),
                  values.allSatisfy({ $0.type == "mdta" }) else { throw CardError.liveStyleMetadata }
            return values.map { Array(data[$0.start..<$0.end]) }
        }
        let oldChildren = try children(source, old), newChildren = try children(sidecar, added)
        var oldKeys = try keys(source, oldChildren)
        let newKeys = try keys(sidecar, newChildren)
        let oldList = try single("ilst", in: oldChildren), newList = try single("ilst", in: newChildren)
        var entries: [UInt32: [UInt8]] = [:]
        for entry in try boxes(source, oldList.payload, oldList.end) {
            if entry.type == "free" || entry.type == "skip" { continue }
            let index = uint(source, entry.start + 4)
            guard index > 0, index <= oldKeys.count, entries[index] == nil else { throw CardError.liveStyleMetadata }
            entries[index] = Array(source[entry.start..<entry.end])
        }
        for entry in try boxes(sidecar, newList.payload, newList.end) {
            let index = uint(sidecar, entry.start + 4)
            guard index > 0, index <= newKeys.count else { throw CardError.liveStyleMetadata }
            let key = newKeys[Int(index) - 1]
            let target: UInt32
            if let existing = oldKeys.firstIndex(of: key) { target = UInt32(existing + 1) }
            else { oldKeys.append(key); target = UInt32(oldKeys.count) }
            var raw = Array(sidecar[entry.start..<entry.end])
            replace(&raw, 4, be(target))
            entries[target] = raw
        }
        var payload = Array(source[old.payload..<(oldChildren.first?.start ?? old.payload)])
        for child in oldChildren {
            if child.type == "keys" { payload += box("keys", [0, 0, 0, 0] + be(UInt32(oldKeys.count)) + oldKeys.flatMap { $0 }) }
            else if child.type == "ilst" { payload += box("ilst", entries.keys.sorted().flatMap { entries[$0]! }) }
            else { payload += Array(source[child.start..<child.end]) }
        }
        return box("meta", payload)
    }

    private static func read(_ url: URL) throws -> [UInt8] {
        let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size > 0, size <= 512 * 1024 * 1024 else { throw CardError.tooLarge }
        return [UInt8](try Data(contentsOf: url))
    }

    private static func boxes(_ bytes: [UInt8], _ start: Int, _ end: Int) throws -> [Box] {
        try HeifContainer.boxes(in: bytes, from: start, to: end)
    }

    private static func single(_ type: String, in boxes: [Box]) throws -> Box {
        let matches = boxes.filter { $0.type == type }
        guard matches.count == 1 else { throw CardError.liveStyleMetadata }
        return matches[0]
    }

    private static func headerOffset(_ box: Box, bytes: [UInt8], v0: Int, v1: Int) throws -> Int {
        guard box.payload < box.end, bytes[box.payload] <= 1 else { throw CardError.liveStyleMetadata }
        let offset = box.payload + (bytes[box.payload] == 0 ? v0 : v1)
        guard offset <= box.end - 4 else { throw CardError.liveStyleMetadata }
        return offset
    }

    private static func uint(_ bytes: [UInt8], _ at: Int) -> UInt32 {
        bytes[at..<at + 4].reduce(0) { $0 << 8 | UInt32($1) }
    }

    private static func be(_ value: UInt32) -> [UInt8] {
        [UInt8(value >> 24), UInt8((value >> 16) & 255), UInt8((value >> 8) & 255), UInt8(value & 255)]
    }

    private static func replace(_ data: inout [UInt8], _ at: Int, _ value: [UInt8]) { data.replaceSubrange(at..<at + value.count, with: value) }
    private static func box(_ type: String, _ payload: [UInt8]) -> [UInt8] { be(UInt32(payload.count + 8)) + Array(type.utf8) + payload }
}
