import Foundation

nonisolated struct LensProfile: Codable, Equatable, Identifiable, Sendable {
    var id = UUID()
    var device: String
    var exifModel: String
    var name: String
    var facing: Facing = .unspecified
    var equivalentMin: Double
    var equivalentMax: Double
    var physicalMin: Double?
    var physicalMax: Double?
    var zoomMin: Double?
    var zoomMax: Double?
    var digitalZoomMax: Double?

    enum Facing: String, Codable, CaseIterable, Identifiable, Sendable {
        case unspecified, back, front, external
        var id: Self { self }
        var titleKey: String { "lens.facing.\(rawValue)" }
    }

    var isValid: Bool {
        guard [device, exifModel, name].allSatisfy({ !Self.normalize($0).isEmpty && $0.count <= 256 })
            && Self.validRange(equivalentMin, equivalentMax, limit: 2_000)
            && Self.optionalRange(physicalMin, physicalMax, limit: 1_000)
            && Self.optionalRange(zoomMin, zoomMax, limit: 200)
            && (equivalentMin != equivalentMax || zoomMin == zoomMax) else { return false }
        guard let digitalZoomMax else { return true }
        guard let zoomMax else { return false }
        return digitalZoomMax.isFinite && digitalZoomMax >= zoomMax && digitalZoomMax <= 200
    }

    func acceptsExif(_ model: String) -> Bool { Self.normalize(exifModel) == Self.normalize(model) }

    func containsPhysical(_ value: Double) -> Bool {
        guard let physicalMin, let physicalMax, value.isFinite else { return false }
        return (physicalMin - 0.02 ... physicalMax + 0.02).contains(value)
    }

    func containsNative(_ value: Double) -> Bool {
        (equivalentMin - 0.5 ... equivalentMax + 0.5).contains(value)
    }

    func equivalent(for physical: Double) -> Double? {
        guard let physicalMin, let physicalMax, containsPhysical(physical),
              physicalMin != physicalMax || equivalentMin == equivalentMax else { return nil }
        return Self.interpolate(min(physicalMax, max(physicalMin, physical)),
                                physicalMin, physicalMax, equivalentMin, equivalentMax)
    }

    func zoom(for equivalent: Double) -> Double? {
        guard let zoomMin, let zoomMax, equivalent.isFinite, equivalent > 0 else { return nil }
        if equivalent < equivalentMin { return zoomMin * equivalent / equivalentMin }
        if equivalent > equivalentMax { return zoomMax * equivalent / equivalentMax }
        return Self.interpolate(equivalent, equivalentMin, equivalentMax, zoomMin, zoomMax)
    }

    static func normalize(_ value: String) -> String {
        value.split(whereSeparator: \.isWhitespace).joined(separator: " ").uppercased()
    }

    private static func validRange(_ low: Double, _ high: Double, limit: Double) -> Bool {
        low.isFinite && high.isFinite && low > 0 && high >= low && high <= limit
    }

    private static func optionalRange(_ low: Double?, _ high: Double?, limit: Double) -> Bool {
        if low == nil && high == nil { return true }
        guard let low, let high else { return false }
        return validRange(low, high, limit: limit)
    }

    private static func interpolate(_ value: Double, _ low: Double, _ high: Double,
                                    _ outputLow: Double, _ outputHigh: Double) -> Double {
        high == low ? outputLow : outputLow + (value - low) / (high - low) * (outputHigh - outputLow)
    }
}

nonisolated enum LensProfileResolver {
    struct Match: Equatable, Sendable {
        let profile: LensProfile
        let equivalent: Double
    }

    static func explicitZoom(in lens: String) -> Double? {
        guard let expression = try? NSRegularExpression(pattern: #"(?:^|[\s(])([0-9]+(?:\.[0-9]+)?)\s*[x×](?:$|[\s)])"#, options: .caseInsensitive),
              let match = expression.firstMatch(in: lens, range: NSRange(lens.startIndex..., in: lens)),
              let range = Range(match.range(at: 1), in: lens), let value = Double(lens[range]), value.isFinite, value > 0 else { return nil }
        return value
    }

    static func resolve(model: String, lens: String, equivalent: Double?, physical: Double?,
                        profiles: [LensProfile]) -> Match? {
        guard !LensProfile.normalize(model).isEmpty else { return nil }
        let isFront = lens.range(of: #"\b(front|selfie)\b"#, options: .regularExpression.union(.caseInsensitive)) != nil
        let candidates = profiles.filter { $0.isValid && $0.acceptsExif(model) && (!isFront || $0.facing != .back) }
        let physical = physical.flatMap { $0.isFinite && $0 > 0 ? $0 : nil }
        let equivalent = equivalent.flatMap { $0.isFinite && $0 > 0 ? $0 : nil }
        let matches = candidates.compactMap { profile -> Match? in
            if let physical, profile.physicalMin != nil, !profile.containsPhysical(physical) { return nil }
            guard let mm = equivalent ?? physical.flatMap(profile.equivalent), mm >= profile.equivalentMin - 0.5 else { return nil }
            return Match(profile: profile, equivalent: mm)
        }
        // Physical focal length stays constant when the same lens is digitally cropped.
        let physicalMatches = matches.filter { match in physical.map(match.profile.containsPhysical) ?? false }
        if !physicalMatches.isEmpty { return physicalMatches.count == 1 ? physicalMatches[0] : nil }
        let covered = matches.filter { match in
            (!isFront || match.profile.facing == .front) && covers(match, peers: candidates)
        }
        return covered.count == 1 ? covered[0] : nil
    }

    private static func covers(_ match: Match, peers: [LensProfile]) -> Bool {
        let profile = match.profile
        let mm = match.equivalent
        if profile.containsNative(mm) { return true }
        guard mm > profile.equivalentMax else { return false }
        if let limit = profile.digitalZoomMax { return profile.zoom(for: mm).map { $0 < limit + 0.05 } ?? false }
        func direction(_ profile: LensProfile) -> LensProfile.Facing { profile.facing == .unspecified ? .back : profile.facing }
        guard let next = peers.filter({ $0.id != profile.id && direction($0) == direction(profile)
            && $0.equivalentMin > profile.equivalentMax }).min(by: { $0.equivalentMin < $1.equivalentMin }),
              mm < next.equivalentMin - 0.5 else { return false }
        if let zoom = profile.zoom(for: mm), let nextZoom = next.zoomMin, let endZoom = profile.zoomMax, nextZoom > endZoom {
            return zoom < (ceil(nextZoom * 10 - 1e-6) - 1) / 10 + 0.05
        }
        return true
    }
}
