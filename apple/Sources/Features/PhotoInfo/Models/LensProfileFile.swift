import Foundation

nonisolated struct LensProfileFile: Codable, Equatable, Sendable {
    static let maximumBytes = 128 * 1024
    var format = "fuyaophotos.lenses"
    var version = 1
    var device: String
    var exifModel: String
    var lenses: [Lens]
    var hardwareModel: String?

    struct Lens: Codable, Equatable, Sendable {
        var name: String
        var facing: LensProfile.Facing
        var equivalentMin: Double
        var equivalentMax: Double
        var physicalMin: Double?
        var physicalMax: Double?
        var zoomMin: Double?
        var zoomMax: Double?
        var digitalZoomMax: Double?
        var id: String?
    }

    init(profiles: [LensProfile]) throws {
        guard let first = profiles.first, profiles.count <= 64,
              profiles.allSatisfy({ $0.isValid && $0.device == first.device && $0.acceptsExif(first.exifModel) }) else {
            throw LensProfileFileError.invalid
        }
        device = first.device; exifModel = first.exifModel
        let models = Set(profiles.compactMap { $0.hardwareDevice ?? $0.hardwareModel }.filter { !$0.isEmpty })
        hardwareModel = models.count == 1 ? models.first : nil
        lenses = profiles.map { Lens(name: $0.name, facing: $0.facing,
            equivalentMin: $0.equivalentMin, equivalentMax: $0.equivalentMax,
            physicalMin: $0.physicalMin, physicalMax: $0.physicalMax,
            zoomMin: $0.zoomMin, zoomMax: $0.zoomMax, digitalZoomMax: $0.digitalZoomMax, id: $0.cameraID) }
    }

    func profiles() throws -> [LensProfile] {
        // ASVS 1.5.2 / 2.2.1: one bounded, versioned device document with finite calibration ranges.
        guard format == "fuyaophotos.lenses", version == 1, (1...64).contains(lenses.count),
              [device, exifModel].allSatisfy(Self.validText) else { throw LensProfileFileError.invalid }
        let values = lenses.map { LensProfile(device: device, exifModel: exifModel, name: $0.name,
            facing: $0.facing, equivalentMin: $0.equivalentMin, equivalentMax: $0.equivalentMax,
            physicalMin: $0.physicalMin, physicalMax: $0.physicalMax, zoomMin: $0.zoomMin,
            zoomMax: $0.zoomMax, digitalZoomMax: $0.digitalZoomMax, cameraID: $0.id, hardwareModel: hardwareModel) }
        guard values.allSatisfy({ $0.isValid && Self.validText($0.name) }) else { throw LensProfileFileError.invalid }
        return values
    }

    static func decode(_ data: Data) throws -> Self {
        guard data.count > 0, data.count <= maximumBytes else { throw LensProfileFileError.invalid }
        do {
            let file = try JSONDecoder().decode(Self.self, from: data)
            _ = try file.profiles()
            return file
        } catch { throw LensProfileFileError.invalid }
    }

    func encoded() throws -> Data {
        _ = try profiles()
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        let data = try encoder.encode(self)
        guard data.count <= Self.maximumBytes else { throw LensProfileFileError.invalid }
        return data
    }

    func merging(into existing: [LensProfile]) throws -> [LensProfile] {
        let incoming = try profiles()
        let retained = existing.filter { !$0.acceptsExif(exifModel) }
        guard retained.count + incoming.count <= 64 else { throw LensProfileFileError.invalid }
        return retained + incoming
    }

    var filename: String {
        let stem = device.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) || $0 == " " || $0 == "-" }
            .map(String.init).joined().trimmingCharacters(in: .whitespaces).prefix(80)
        return "\(stem.isEmpty ? "Camera" : String(stem)).json"
    }

    private static func validText(_ text: String) -> Bool {
        !LensProfile.normalize(text).isEmpty && text.count <= 256
            && text.unicodeScalars.allSatisfy { !CharacterSet.controlCharacters.contains($0) }
    }
}

nonisolated enum LensProfileFileError: Error, LocalizedError {
    case invalid
    var errorDescription: String? { NSLocalizedString("lens.file.invalid", comment: "") }
}
