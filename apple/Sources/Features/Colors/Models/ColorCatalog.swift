import Foundation

nonisolated enum ColorCatalog {
    static let css: [ColorReference] = {
        var groups: [String: [String]] = [:]
        for line in contents("CssColors", type: "txt").split(separator: "\n") {
            let pair = line.split(separator: "=", maxSplits: 1).map(String.init)
            guard pair.count == 2 else { continue }
            groups[pair[1], default: []].append(pair[0])
        }
        return groups.sorted { $0.key < $1.key }.compactMap { hex, names in
            guard let packed = Int(hex.dropFirst(), radix: 16) else { return nil }
            return ColorReference(name: names.sorted().joined(separator: " / "), rgb: SampleRGB(values:
                SIMD3(Double(packed >> 16 & 255), Double(packed >> 8 & 255), Double(packed & 255)) / 255))
        }
    }()
    private static let cssOK = css.map { ColorConversions.okLabFromSRGB($0.rgb.values) }
    static let ral: [ColorReference] = contents("RalColors", type: "csv").split(separator: "\n").compactMap { line in
        let fields = line.split(separator: ",", maxSplits: 4).map(String.init)
        guard fields.count == 5, let r = Double(fields[1]), let g = Double(fields[2]), let b = Double(fields[3]) else { return nil }
        return ColorReference(name: "\(fields[0]) · \(fields[4])", rgb: SampleRGB(values: SIMD3(r, g, b) / 255))
    }

    static func nearestCSS(_ rgb: SampleRGB, ok: SIMD3<Double>) -> ColorReference? {
        if !rgb.outOfGamut, let exact = css.first(where: { $0.rgb.integers == rgb.integers }) { return exact }
        guard let index = css.indices.min(by: { ColorConversions.distance(cssOK[$0], ok) < ColorConversions.distance(cssOK[$1], ok) }) else { return nil }
        return css[index]
    }
    static func nearestRAL(_ rgb: SampleRGB) -> ColorReference? {
        let value = rgb.integers.map(Double.init)
        let rounded = SIMD3(value[0], value[1], value[2]) / 255
        return ral.min { ColorConversions.distance($0.rgb.values, rounded) < ColorConversions.distance($1.rgb.values, rounded) }
    }
    private static func contents(_ name: String, type: String) -> String {
#if SWIFT_PACKAGE
        let bundle = Bundle.module
#else
        let bundle = Bundle.main
#endif
        guard let url = bundle.url(forResource: name, withExtension: type) else { return "" }
        return (try? String(contentsOf: url, encoding: .utf8)) ?? ""
    }
}
