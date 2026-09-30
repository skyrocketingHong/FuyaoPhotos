import Foundation

/// Replaces metadata atoms in place so sample offsets, timing and audio/video bytes remain unchanged.
nonisolated enum MovieMetadataCleaner {
    /// AVAssetWriter has already filtered descriptive/timed metadata. Clear only its new absolute header dates.
    static func clearContainerDates(at url: URL) throws {
        var data = try Data(contentsOf: url)
        guard data.count <= 512 * 1024 * 1024 else { throw CardError.tooLarge }
        var count = 0
        func uint(_ at: Int) -> Int { data[at..<at + 4].reduce(0) { $0 << 8 | Int($1) } }
        func walk(_ start: Int, _ end: Int, _ depth: Int) throws {
            guard depth <= 8 else { throw CardError.videoMetadata }
            var at = start
            while at < end {
                count += 1
                guard count <= 100_000, end - at >= 8 else { throw CardError.videoMetadata }
                var size = uint(at), header = 8
                if size == 1 {
                    guard end - at >= 16, uint(at + 8) == 0 else { throw CardError.videoMetadata }
                    size = uint(at + 12); header = 16
                }
                if size == 0 { size = end - at }
                guard size >= header, size <= end - at else { throw CardError.videoMetadata }
                let type = String(decoding: data[at + 4..<at + 8], as: UTF8.self)
                let payload = at + header
                if ["moov", "trak", "mdia"].contains(type) { try walk(payload, at + size, depth + 1) }
                else if ["mvhd", "tkhd", "mdhd"].contains(type) {
                    guard at + size - payload >= 4, data[payload] <= 1 else { throw CardError.videoMetadata }
                    let length = data[payload] == 1 ? 16 : 8
                    guard at + size - payload >= 4 + length else { throw CardError.videoMetadata }
                    data.replaceSubrange(payload + 4..<payload + 4 + length, with: repeatElement(UInt8(0), count: length))
                }
                at += size
            }
        }
        try walk(0, data.count, 0)
        try data.write(to: url, options: .atomic)
    }

    private struct Box {
        let start: Int
        let end: Int
        let payload: Int
        let type: String
    }

    static func copy(from source: URL, to destination: URL, options: CardSaveOptions,
                     verifiedRenderingTracks: Bool = false) throws {
        var data = try Data(contentsOf: source)
        guard data.count <= 512 * 1024 * 1024 else { throw CardError.tooLarge }
        if options.keepExif && options.keepLocation && options.keepCaptureTime {
            try data.write(to: destination, options: .atomic)
            return
        }
        let original = data
        var changedRanges: [Range<Int>] = []
        var count = 0
        func uint(_ at: Int) throws -> Int {
            guard at >= 0, at <= data.count - 4 else { throw CardError.videoMetadata }
            return data[at..<at + 4].reduce(0) { $0 << 8 | Int($1) }
        }
        func boxes(_ start: Int, _ end: Int) throws -> [Box] {
            var result: [Box] = []
            var at = start
            while at < end {
                count += 1
                guard count <= 100_000, end - at >= 8 else { throw CardError.videoMetadata }
                var size = try uint(at)
                var header = 8
                if size == 1 {
                    guard end - at >= 16, try uint(at + 8) == 0 else { throw CardError.videoMetadata }
                    size = try uint(at + 12); header = 16
                }
                if size == 0 { size = end - at }
                guard size >= header, size <= end - at else { throw CardError.videoMetadata }
                result.append(Box(start: at, end: at + size, payload: at + header,
                    type: String(data: data[at + 4..<at + 8], encoding: .isoLatin1) ?? ""))
                at += size
            }
            return result
        }
        func zero(_ range: Range<Int>) {
            guard !range.isEmpty else { return }
            changedRanges.append(range)
            data.replaceSubrange(range, with: repeatElement(UInt8(0), count: range.count))
        }
        func remove(_ box: Box) {
            changedRanges.append(box.start + 4..<box.start + 8)
            data.replaceSubrange(box.start + 4..<box.start + 8, with: Data("free".utf8))
            zero(box.payload..<box.end)
        }
        func keep(_ key: String) -> Bool {
            LivePhotoMovie.keep(key, options: options)
        }
        func cleanMeta(_ box: Box) throws {
            let children = try boxes(box.payload + (uint(box.payload) == 0 ? 4 : 0), box.end)
            var keys: [Int: String] = [:]
            if let keyBox = children.first(where: { $0.type == "keys" }) {
                let total = try uint(keyBox.payload + 4)
                guard total <= 4096 else { throw CardError.videoMetadata }
                var at = keyBox.payload + 8
                for index in 1...max(1, total) where index <= total {
                    let size = try uint(at)
                    guard size >= 8, size <= keyBox.end - at else { throw CardError.videoMetadata }
                    keys[index] = String(data: data[at + 8..<at + size], encoding: .utf8) ?? ""
                    at += size
                }
                guard at == keyBox.end else { throw CardError.videoMetadata }
            }
            for child in children {
                if child.type == "ilst" {
                    for item in try boxes(child.payload, child.end) {
                        let key = keys.isEmpty ? item.type : keys[try uint(item.start + 4), default: ""]
                        if !keep(key) { remove(item) }
                    }
                } else if child.type == "hdlr" {
                    guard child.end - child.payload >= 24 else { throw CardError.videoMetadata }
                    zero(child.payload + 24..<child.end)
                } else if child.type != "keys" { remove(child) }
            }
        }
        let containers: Set<String> = ["moov", "trak", "mdia", "minf", "stbl", "edts", "dinf"]
        let structure: Set<String> = ["ftyp", "mdat", "wide", "iods", "vmhd", "smhd", "nmhd", "gmhd", "dref", "elst", "stsd", "stts", "ctts", "stss", "stsz", "stz2", "stsc", "stco", "co64", "sdtp", "sgpd", "sbgp", "cslg"]
        func walk(_ start: Int, _ end: Int, depth: Int) throws {
            guard depth <= 12 else { throw CardError.videoMetadata }
            for box in try boxes(start, end) {
                if containers.contains(box.type) { try walk(box.payload, box.end, depth: depth + 1) }
                else if box.type == "meta" { try cleanMeta(box) }
                else if box.type == "udta" {
                    for item in try boxes(box.payload, box.end) {
                        if item.type == "meta" { try cleanMeta(item) }
                        else if item.type == "tagc", verifiedRenderingTracks {
                            let value = String(data: data[item.payload..<item.end], encoding: .utf8) ?? ""
                            guard ["sky", "smart-style-linear-thumbnail", "smart-style-delta-map", "person", "skin"].contains(where: {
                                value == "com.apple.quicktime.video-map." + $0
                            }) else { throw CardError.videoMetadata }
                        }
                        else if !keep(item.type) { remove(item) }
                    }
                } else if box.type == "tref", verifiedRenderingTracks {
                    for reference in try boxes(box.payload, box.end) {
                        guard ["vmap", "cdsc", "cdep"].contains(reference.type),
                              reference.end > reference.payload, (reference.end - reference.payload) % 4 == 0 else {
                            throw CardError.videoMetadata
                        }
                    }
                } else if box.type == "tapt", verifiedRenderingTracks {
                    for aperture in try boxes(box.payload, box.end) {
                        guard ["clef", "prof", "enof"].contains(aperture.type),
                              aperture.end - aperture.payload == 12 else { throw CardError.videoMetadata }
                    }
                } else if ["mvhd", "tkhd", "mdhd"].contains(box.type) {
                    guard box.end - box.payload >= 4 else { throw CardError.videoMetadata }
                    let version = data[box.payload]
                    let bytes = version == 1 ? 16 : 8
                    guard version <= 1, box.end - box.payload >= 4 + bytes else { throw CardError.videoMetadata }
                    if !options.keepCaptureTime { zero(box.payload + 4..<box.payload + 4 + bytes) }
                } else if box.type == "hdlr" {
                    guard box.end - box.payload >= 24 else { throw CardError.videoMetadata }
                    zero(box.payload + 24..<box.end)
                } else if ["free", "skip", "XMP_", "xml "].contains(box.type) { remove(box) }
                else if !structure.contains(box.type) { throw CardError.videoMetadata }
            }
        }
        let roots = try boxes(0, data.count)
        guard roots.contains(where: { $0.type == "ftyp" }), roots.contains(where: { $0.type == "mdat" }),
              roots.contains(where: { $0.type == "moov" }) else { throw CardError.videoMetadata }
        try walk(0, data.count, depth: 0)
        var cursor = 0
        for range in changedRanges.sorted(by: { $0.lowerBound < $1.lowerBound }) {
            guard range.lowerBound >= cursor, original[cursor..<range.lowerBound] == data[cursor..<range.lowerBound] else {
                throw CardError.videoMetadata
            }
            cursor = range.upperBound
        }
        guard original[cursor...] == data[cursor...] else { throw CardError.videoMetadata }
        for box in roots where box.type == "mdat" {
            guard original[box.start..<box.end] == data[box.start..<box.end] else { throw CardError.videoMetadata }
        }
        guard data.count == original.count else { throw CardError.videoMetadata }
        try data.write(to: destination, options: .atomic)
    }
}
