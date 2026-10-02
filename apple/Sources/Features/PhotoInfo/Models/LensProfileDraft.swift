import Foundation

nonisolated struct LensProfileDraft: Equatable, Identifiable {
    let id: UUID
    let isNew: Bool
    var device = ""
    var exifModel = ""
    var name = ""
    var stylePrefix = ""
    var originalMegapixels = ""
    var facing = LensProfile.Facing.unspecified
    var equivalentMin = ""
    var equivalentMax = ""
    var physicalMin = ""
    var physicalMax = ""
    var zoomMin = ""
    var zoomMax = ""
    var digitalZoomMax = ""
    var cameraID: String?
    var hardwareDevice: String?
    var hardwareModel: String?

    init(profile: LensProfile? = nil) {
        id = profile?.id ?? UUID()
        isNew = profile == nil
        guard let profile else { return }
        device = profile.device; exifModel = profile.exifModel; name = profile.name; facing = profile.facing
        stylePrefix = profile.stylePrefix ?? ""
        originalMegapixels = profile.originalMegapixels.map {
            $0.formatted(.number.grouping(.never).precision(.significantDigits(1...17)))
        } ?? ""
        equivalentMin = Self.number(profile.equivalentMin); equivalentMax = Self.number(profile.equivalentMax)
        physicalMin = Self.number(profile.physicalMin); physicalMax = Self.number(profile.physicalMax)
        zoomMin = Self.number(profile.zoomMin); zoomMax = Self.number(profile.zoomMax)
        digitalZoomMax = Self.number(profile.digitalZoomMax)
        cameraID = profile.cameraID; hardwareDevice = profile.hardwareDevice; hardwareModel = profile.hardwareModel
    }

    var profile: LensProfile? {
        guard validationKey == nil, let low = Self.parse(equivalentMin), let high = Self.parse(equivalentMax) else { return nil }
        return LensProfile(id: id, device: device.trimmingCharacters(in: .whitespacesAndNewlines),
                           exifModel: exifModel.trimmingCharacters(in: .whitespacesAndNewlines),
                           name: name.trimmingCharacters(in: .whitespacesAndNewlines), facing: facing,
                           equivalentMin: low, equivalentMax: high,
                           physicalMin: Self.parse(physicalMin), physicalMax: Self.parse(physicalMax),
                           zoomMin: Self.parse(zoomMin), zoomMax: Self.parse(zoomMax), digitalZoomMax: Self.parse(digitalZoomMax),
                           cameraID: cameraID, hardwareDevice: hardwareDevice, hardwareModel: hardwareModel,
                           stylePrefix: stylePrefix.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil
                                : stylePrefix.trimmingCharacters(in: .whitespacesAndNewlines),
                           originalMegapixels: Self.parse(originalMegapixels))
    }

    var validationKey: String? {
        if [device, exifModel, name].contains(where: { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) { return "lens.validation.identity" }
        if [device, exifModel, name].contains(where: { $0.count > 256 }) { return "lens.validation.length" }
        if stylePrefix.count > 64 || stylePrefix.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) { return "lens.validation.stylePrefix" }
        if !originalMegapixels.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            guard let value = Self.parse(originalMegapixels), value > 0, value <= 1_000 else { return "lens.validation.originalMegapixels" }
        }
        if !Self.validRange(equivalentMin, equivalentMax, limit: 2_000, optional: false) { return "lens.validation.equivalent" }
        if !Self.validRange(physicalMin, physicalMax, limit: 1_000) { return "lens.validation.physical" }
        if !Self.validRange(zoomMin, zoomMax, limit: 200) { return "lens.validation.zoom" }
        if Self.parse(equivalentMin) == Self.parse(equivalentMax), Self.parse(zoomMin) != Self.parse(zoomMax) { return "lens.validation.fixed" }
        if !digitalZoomMax.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            guard let value = Self.parse(digitalZoomMax), let high = Self.parse(zoomMax), value >= high, value <= 200 else { return "lens.validation.digital" }
        }
        return nil
    }

    private static func validRange(_ low: String, _ high: String, limit: Double, optional: Bool = true) -> Bool {
        if optional && low.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && high.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return true }
        guard let low = parse(low), let high = parse(high) else { return false }
        return low > 0 && high >= low && high <= limit
    }

    private static func parse(_ value: String) -> Double? {
        let text = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let separator = Locale.current.decimalSeparator ?? "."
        let normalized = separator == "." ? text : text.replacingOccurrences(of: separator, with: ".")
        guard let number = Double(normalized), number.isFinite else { return nil }
        return number
    }

    private static func number(_ value: Double?) -> String {
        value?.formatted(.number.grouping(.never).precision(.fractionLength(0...8))) ?? ""
    }
}
