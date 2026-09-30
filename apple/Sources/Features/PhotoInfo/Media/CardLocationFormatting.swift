import Foundation

nonisolated enum CardLocationFormatting {
    private static let countryCodes = Set(Locale.Region.isoRegions.map(\.identifier))

    static func place(city: String?, country: String?, countryCode: String?) -> String {
        func clean(_ value: String?) -> String {
            value?.split(whereSeparator: \.isWhitespace).joined(separator: " ") ?? ""
        }
        let city = clean(city)
        let code = clean(countryCode).uppercased()
        let localizedCountry = countryCodes.contains(code)
            ? Locale(identifier: "en").localizedString(forRegionCode: code) : nil
        let nation = code == "CN" ? "China" : clean(localizedCountry ?? country)
        var parts: [String] = []
        for part in [city, nation] where !part.isEmpty {
            if !parts.contains(where: { $0.caseInsensitiveCompare(part) == .orderedSame }) { parts.append(part) }
        }
        return parts.joined(separator: ", ")
    }
}
