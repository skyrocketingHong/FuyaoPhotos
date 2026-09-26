import Testing
import Foundation
@testable import PhotoRenderingCore

struct AppleStyleMetadataTests {
    @Test func identityStyleDataKeepsTheVerifiedLatticeLayout() {
        let data = AppleStyleMetadata.identityStyleData()
        #expect(data.count == 51840)
        for block in 0..<864 {
            for index in 0..<30 {
                let at = (block * 30 + index) * 2
                let value = UInt16(data[at + 1]) << 8 | UInt16(data[at])
                #expect(value == ((index == 3 || index == 7 || index == 11) ? 0x3c00 : 0))
            }
        }
    }

    @Test func styleMetadataCarriesTheGoldenContract() throws {
        let payload = AppleStyleMetadata.styleMetadata()
        #expect(String(decoding: payload[0..<8], as: UTF8.self) == "bplist00")
        let trailer = payload.count - 32
        let offsetSize = Int(payload[trailer + 6])
        let refSize = Int(payload[trailer + 7])
        let count = Int(readBE(payload, trailer + 8, 8))
        let tableAt = Int(readBE(payload, trailer + 24, 8))
        let topObject = Int(readBE(payload, trailer + 16, 8))
        #expect([1, 2, 4].contains(offsetSize) && [1, 2].contains(refSize) && count > 17)

        var dataLengths = Set<Int>()
        var topDictCount: Int?
        for index in 0..<count {
            let at = Int(readBE(payload, tableAt + index * offsetSize, offsetSize))
            #expect(at >= 0 && at < trailer)
            let marker = payload[at] & 0xf0
            if marker == 0x40 {
                let (length, header) = plistLength(payload, at)
                if at + header + length <= tableAt { dataLengths.insert(length) }
            }
            if marker == 0xd0 {
                let (length, _) = plistLength(payload, at)
                if at == Int(readBE(payload, tableAt + topObject * offsetSize, offsetSize)) {
                    topDictCount = length
                }
            }
        }
        #expect(topDictCount == 17)
        #expect(dataLengths.contains(51840))
        #expect(dataLengths.contains(2048))
        #expect(dataLengths.contains(516))
    }

    @Test func stylesNoteKeepsAppleEntryLayout() {
        let identifier = "00112233-4455-6677-8899-aabbccddeeff"
        let note = AppleStyleMetadata.stylesNote(identifier: identifier)
        #expect(note.count == 16 + 2 * 12 + 4 + 37 + 91)
        #expect(String(decoding: note[0..<9], as: UTF8.self) == "Apple iOS")
        #expect(note[9] == 0 && note[10] == 0 && note[11] == 1)
        #expect(note[12] == UInt8(ascii: "M") && note[13] == UInt8(ascii: "M"))
        #expect(u16(note, 14) == 2)
        #expect(u16(note, 16) == 43 && u16(note, 18) == 2 && u32(note, 20) == 37 && u32(note, 24) == 44)
        #expect(u16(note, 28) == 84 && u16(note, 30) == 7 && u32(note, 32) == 91 && u32(note, 36) == 81)
        #expect(String(decoding: note[44..<80], as: UTF8.self) == identifier.uppercased())
        #expect(note[80] == 0)
        #expect(Array(note[81..<172]) == AppleStyleGolden.TAG_84)
    }

    @Test func mergedStyleNoteKeepsExistingTags() {
        let existing = AppleStyleMetadata.stylesNote(identifier: "00112233-4455-6677-8899-aabbccddeeff")
        let merged = AppleStyleMetadata.mergedStyleNote(existing: Data(existing), identifier: "aabbccdd-4455-6677-8899-aabbccddee11")
        #expect(merged.count == existing.count)
        #expect(String(decoding: merged[44..<80], as: UTF8.self) == "AABBCCDD-4455-6677-8899-AABBCCDDEE11")
        #expect(Array(merged[81..<172]) == AppleStyleGolden.TAG_84)
    }

    @Test func appleNoteWithStyleSplicesTheTIFF() throws {
        // A minimal MM TIFF with an Exif IFD carrying one capture tag.
        var tiff: [UInt8] = [0x4d, 0x4d, 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08]
        tiff.append(contentsOf: [0x00, 0x01]) // one root entry: ExifIFD pointer
        tiff.append(contentsOf: entry(tag: 0x8769, type: 4, count: 1, value: 26))
        tiff.append(contentsOf: [0, 0, 0, 0])
        tiff.append(contentsOf: [0x00, 0x01]) // Exif IFD: ExposureProgram
        tiff.append(contentsOf: entry(tag: 0x8822, type: 3, count: 1, value: 2))
        tiff.append(contentsOf: [0, 0, 0, 0])

        let identifier = "00112233-4455-6677-8899-aabbccddeeff"
        let output = try AppleStyleMetadata.appleNoteWithStyle(exif: Data(Array("Exif\0\0".utf8) + tiff),
                                                               styleIdentifier: identifier)
        let bytes = Array(output)
        #expect(Array(bytes[0..<6]) == Array("Exif\0\0".utf8))
        let body = Array(bytes.dropFirst(6))
        #expect(u16(body, 2) == 42)
        let rootAt = Int(u32(body, 4))
        let rootCount = Int(u16(body, rootAt))
        #expect(rootCount == 1)
        let pointer = body[rootAt + 2..<rootAt + 14]
        #expect(u16(Array(pointer), 0) == 0x8769)
        let exifAt = Int(u32(Array(pointer), 8))
        let exifCount = Int(u16(body, exifAt))
        #expect(exifCount == 2)
        let tags = (0..<exifCount).map { u16(body, exifAt + 2 + $0 * 12) }
        #expect(tags == [0x8822, 0x927c])
        let noteAt = Int(u32(body, exifAt + 2 + tags.firstIndex(of: 0x927c)! * 12 + 8))
        #expect(String(decoding: body[noteAt..<noteAt + 9], as: UTF8.self) == "Apple iOS")
        let noteCount = u16(body, noteAt + 14)
        #expect(noteCount == 2)
        #expect(u16(body, noteAt + 16) == 43)
        #expect(u16(body, noteAt + 28) == 84)
        #expect(String(decoding: body[(noteAt + 44)..<(noteAt + 80)], as: UTF8.self) == identifier.uppercased())
    }

    @Test func appleNoteWithStyleMergesAnExistingAppleMakerNote() throws {
        // A native Live Photo note: pairing tag 17 without any style entries.
        let pairingID = "99998888-7777-6666-5555-444433332222"
        var existing: [UInt8] = Array("Apple iOS".utf8) + [0, 0, 1, 0x4d, 0x4d]
        existing.append(contentsOf: [0x00, 0x01])
        existing.append(contentsOf: entry(tag: 17, type: 2, count: 37, value: 32))
        existing.append(contentsOf: [0, 0, 0, 0])
        existing.append(contentsOf: Array(pairingID.uppercased().utf8) + [0])

        var tiff: [UInt8] = [0x4d, 0x4d, 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08]
        tiff.append(contentsOf: [0x00, 0x01])
        tiff.append(contentsOf: entry(tag: 0x8769, type: 4, count: 1, value: 26))
        tiff.append(contentsOf: [0, 0, 0, 0])
        tiff.append(contentsOf: [0x00, 0x01]) // Exif IFD: maker note only
        tiff.append(contentsOf: entry(tag: 0x927c, type: 7, count: existing.count, value: 44))
        tiff.append(contentsOf: [0, 0, 0, 0])
        tiff.append(contentsOf: existing)

        let merged = try AppleStyleMetadata.appleNoteWithStyle(exif: Data(Array("Exif\0\0".utf8) + tiff),
                                                               styleIdentifier: "aabbccdd-4455-6677-8899-aabbccddee11")
        let body = Array(merged.dropFirst(6))
        let rootAt = Int(u32(body, 4))
        let exifAt = Int(u32(body, rootAt + 2 + 8))
        let exifCount = Int(u16(body, exifAt))
        #expect(exifCount == 1)
        let noteAt = Int(u32(body, exifAt + 10))
        let noteCount = Int(u16(body, noteAt + 14))
        #expect(noteCount == 3)
        let noteTags = (0..<noteCount).map { u16(body, noteAt + 16 + $0 * 12) }
        #expect(noteTags == [17, 43, 84])
        let payloadBase = noteAt + 16 + noteCount * 12 + 4
        #expect(String(decoding: body[payloadBase..<(payloadBase + 36)], as: UTF8.self) == pairingID.uppercased())
        #expect(String(decoding: body[(payloadBase + 37)..<(payloadBase + 73)], as: UTF8.self)
            == "AABBCCDD-4455-6677-8899-AABBCCDDEE11")
        #expect(Array(body[(payloadBase + 74)..<(payloadBase + 165)]) == AppleStyleGolden.TAG_84)
    }

    private func entry(tag: Int, type: Int, count: Int, value: Int) -> [UInt8] {
        var bytes: [UInt8] = []
        bytes.append(UInt8((tag >> 8) & 0xff)); bytes.append(UInt8(tag & 0xff))
        bytes.append(UInt8((type >> 8) & 0xff)); bytes.append(UInt8(type & 0xff))
        for shift in [24, 16, 8, 0] { bytes.append(UInt8((count >> shift) & 0xff)) }
        for shift in [24, 16, 8, 0] { bytes.append(UInt8((value >> shift) & 0xff)) }
        return bytes
    }

    private func u16(_ bytes: [UInt8], _ at: Int) -> Int { Int(bytes[at]) << 8 | Int(bytes[at + 1]) }

    private func u32(_ bytes: [UInt8], _ at: Int) -> Int {
        Int(bytes[at]) << 24 | Int(bytes[at + 1]) << 16 | Int(bytes[at + 2]) << 8 | Int(bytes[at + 3])
    }

    private func readBE(_ bytes: [UInt8], _ at: Int, _ size: Int) -> UInt64 {
        var value: UInt64 = 0
        for index in 0..<size { value = value << 8 | UInt64(bytes[at + index]) }
        return value
    }

    private func plistLength(_ payload: [UInt8], _ at: Int) -> (length: Int, header: Int) {
        var length = Int(payload[at] & 0x0f)
        var header = 1
        if length == 15 {
            let marker = Int(payload[at + 1] & 0x0f)
            length = Int(readBE(payload, at + 2, 1 << marker))
            header = 2 + (1 << marker)
        }
        return (length, header)
    }
}
