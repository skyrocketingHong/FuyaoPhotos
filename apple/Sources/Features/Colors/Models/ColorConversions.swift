import Foundation

nonisolated struct ColorReadout: Identifiable, Sendable {
    let label: String
    let value: String
    var id: String { label }
}

nonisolated enum ColorResultSpace: String, CaseIterable, Identifiable, Sendable {
    case sRGB, p3 = "Display P3", rec2020 = "Rec.2020", a98 = "A98 RGB", cie = "CIE", okLab = "OKLab", xyz = "XYZ", css = "CSS", ral = "RAL"
    var id: Self { self }
}

nonisolated struct SampleRGB: Sendable {
    let values: SIMD3<Double>
    var clipped: SIMD3<Double> { SIMD3(min(1, max(0, values.x)), min(1, max(0, values.y)), min(1, max(0, values.z))) }
    var integers: [Int] { [clipped.x, clipped.y, clipped.z].map { Int(($0 * 255).rounded()) } }
    var hex: String { String(format: "#%02X%02X%02X", integers[0], integers[1], integers[2]) }
    var rgb: String { integers.map(String.init).joined(separator: ", ") }
    var outOfGamut: Bool { [values.x, values.y, values.z].contains { $0 < -0.0001 || $0 > 1.0001 } }
    func css(_ space: String) -> String { "color(\(space) \(ColorConversions.text(values)))" }
}

nonisolated struct ColorReference: Sendable {
    let name: String
    let rgb: SampleRGB
}

nonisolated struct SampledPhotoColor: Sendable {
    let x: Int
    let y: Int
    let srgb: SampleRGB
    let p3: SampleRGB
    let rec2020: SampleRGB
    let a98: SampleRGB
    let cssRec2020: SampleRGB
    let xyzD65: SIMD3<Double>
    let xyzD50: SIMD3<Double>
    let lab: SIMD3<Double>
    let okLab: SIMD3<Double>
    let hsl: SIMD3<Double>
    let cmyk: SIMD4<Double>
    let cssReference: ColorReference?
    let ralReference: ColorReference?

    func readouts(_ space: ColorResultSpace) -> [ColorReadout] {
        func row(_ label: String, _ value: String) -> ColorReadout { ColorReadout(label: label, value: value) }
        func rgb(_ value: SampleRGB, _ css: String) -> [ColorReadout] {
            [row("HEX", value.hex), row("RGB", value.rgb), row("CSS", css)]
        }
        switch space {
        case .sRGB:
            return rgb(srgb, srgb.css("srgb")) + [
                row("HSL", String(format: "hsl(%.2f %.2f%% %.2f%%)", hsl.x, hsl.y * 100, hsl.z * 100)),
                row("CMYK", [cmyk.x, cmyk.y, cmyk.z, cmyk.w].map { String(format: "%.2f%%", $0 * 100) }.joined(separator: ", "))]
        case .p3: return rgb(p3, p3.css("display-p3"))
        case .rec2020: return rgb(rec2020, cssRec2020.css("rec2020"))
        case .a98: return rgb(a98, a98.css("a98-rgb"))
        case .cie:
            return [row("Lab (D50)", "lab(\(ColorConversions.number(lab.x))% \(ColorConversions.number(lab.y)) \(ColorConversions.number(lab.z)))"),
                    row("LCH (D50)", ColorConversions.polar(lab, name: "lch", threshold: 0.0015))]
        case .okLab:
            return [row("OKLab", "oklab(\(ColorConversions.text(okLab)))"),
                    row("OKLCH", ColorConversions.polar(okLab, name: "oklch", threshold: 0.000004))]
        case .xyz:
            return [row("XYZ D50", "color(xyz-d50 \(ColorConversions.text(xyzD50)))"),
                    row("XYZ D65", "color(xyz-d65 \(ColorConversions.text(xyzD65)))")]
        case .css:
            guard let reference = cssReference else { return [] }
            return [row("CSS", reference.name), row("HEX", reference.rgb.hex), row("RGB", reference.rgb.rgb),
                    row("ΔE OK", ColorConversions.number(ColorConversions.distance(okLab,
                        ColorConversions.okLabFromSRGB(reference.rgb.values))))]
        case .ral:
            guard let reference = ralReference else { return [] }
            return [row("RAL", reference.name), row("HEX", reference.rgb.hex), row("RGB", reference.rgb.rgb)]
        }
    }
}

// Same D50/D65 and CSS Color 4 conversions as FuyaoColorPicker.
nonisolated enum ColorConversions {
    static func sample(linearP3: SIMD3<Double>, x: Int, y: Int) -> SampledPhotoColor {
        let xyz = multiply([
            [0.4865709486482162, 0.26566769316909306, 0.1982172852343625],
            [0.2289745640697488, 0.6917385218365064, 0.079286914093745],
            [0, 0.04511338185890264, 1.043944368900976]], linearP3)
        let srgb = SampleRGB(values: map(multiply([
            [3.2409699419045226, -1.537383177570094, -0.4986107602930034],
            [-0.9692436362808796, 1.8759675015077202, 0.04155505740717559],
            [0.05563007969699366, -0.20397695888897652, 1.0569715142428786]], xyz), encodeSRGB))
        let d50 = multiply([
            [1.0479297925449969, 0.022946870601609652, -0.05019226628920524],
            [0.02962780877005599, 0.9904344267538799, -0.017073799063418826],
            [-0.009243040646204504, 0.015055191490298152, 0.7518742814281371]], xyz)
        let rec = multiply([
            [30757411.0 / 17917100, -6372589.0 / 17917100, -4539589.0 / 17917100],
            [-19765991.0 / 29648200, 47925759.0 / 29648200, 467509.0 / 29648200],
            [792561.0 / 44930125, -1921689.0 / 44930125, 42328811.0 / 44930125]], xyz)
        let adobe = multiply([
            [1829569.0 / 896150, -506331.0 / 896150, -308931.0 / 896150],
            [-851781.0 / 878810, 1648619.0 / 878810, 36519.0 / 878810],
            [16779.0 / 1248040, -147721.0 / 1248040, 1266979.0 / 1248040]], xyz)
        let lab = labD50(d50)
        let ok = oklab(xyz)
        let clipped = srgb.clipped
        let maximum = max(clipped.x, max(clipped.y, clipped.z))
        let minimum = min(clipped.x, min(clipped.y, clipped.z))
        let delta = maximum - minimum
        let light = (maximum + minimum) / 2
        var hue = 0.0
        if delta > 0 {
            if maximum == clipped.x { hue = 60 * ((clipped.y - clipped.z) / delta).truncatingRemainder(dividingBy: 6) }
            else if maximum == clipped.y { hue = 60 * ((clipped.z - clipped.x) / delta + 2) }
            else { hue = 60 * ((clipped.x - clipped.y) / delta + 4) }
        }
        let hsl = SIMD3((hue + 360).truncatingRemainder(dividingBy: 360), delta == 0 ? 0 : delta / (1 - abs(2 * light - 1)), light)
        let cmyk = maximum == 0 ? SIMD4(0.0, 0, 0, 1) : SIMD4((maximum - clipped.x) / maximum,
            (maximum - clipped.y) / maximum, (maximum - clipped.z) / maximum, 1 - maximum)
        return SampledPhotoColor(x: x, y: y, srgb: srgb, p3: SampleRGB(values: map(linearP3, encodeSRGB)),
            rec2020: SampleRGB(values: map(rec) { value in
                let v = abs(value)
                return (value < 0 ? -1 : 1) * (v < 0.018053968510807 ? 4.5 * v : 1.09929682680944 * pow(v, 0.45) - 0.09929682680944)
            }), a98: SampleRGB(values: map(adobe) { signedPower($0, 256.0 / 563) }),
            cssRec2020: SampleRGB(values: map(rec) { signedPower($0, 1 / 2.4) }),
            xyzD65: xyz, xyzD50: d50, lab: lab, okLab: ok, hsl: hsl, cmyk: cmyk,
            cssReference: ColorCatalog.nearestCSS(srgb, ok: ok), ralReference: ColorCatalog.nearestRAL(srgb))
    }

    static func okLabFromSRGB(_ rgb: SIMD3<Double>) -> SIMD3<Double> {
        oklab(multiply([[0.41239079926595934, 0.35758433938387796, 0.1804807884018343],
                       [0.21263900587151027, 0.7151686787677559, 0.07219231536073371],
                       [0.01933081871559182, 0.11919477979462599, 0.9505321522496607]], map(rgb) {
            $0 <= 0.04045 ? $0 / 12.92 : pow(($0 + 0.055) / 1.055, 2.4)
        }))
    }

    static func oklab(_ xyz: SIMD3<Double>) -> SIMD3<Double> {
        let lms = map(multiply([[0.819022437996703, 0.3619062600528904, -0.1288737815209879],
            [0.0329836539323885, 0.9292868615863434, 0.0361446663506424],
            [0.0481771893596242, 0.2642395317527308, 0.6335478284694309]], xyz), cbrt)
        return multiply([[0.210454268309314, 0.7936177747023054, -0.0040720430116193],
            [1.9779985324311684, -2.42859224204858, 0.450593709617411],
            [0.0259040424655478, 0.7827717124575296, -0.8086757549230774]], lms)
    }

    static func labD50(_ xyz: SIMD3<Double>) -> SIMD3<Double> {
        let white = SIMD3(0.3457 / 0.3585, 1, (1 - 0.3457 - 0.3585) / 0.3585)
        let f = map(xyz / white) { $0 > 216.0 / 24389 ? cbrt($0) : ((24389.0 / 27) * $0 + 16) / 116 }
        return SIMD3(116 * f.y - 16, 500 * (f.x - f.y), 200 * (f.y - f.z))
    }

    static func encodeSRGB(_ value: Double) -> Double {
        let v = abs(value)
        return (value < 0 ? -1 : 1) * (v <= 0.0031308 ? v * 12.92 : 1.055 * pow(v, 1 / 2.4) - 0.055)
    }
    static func signedPower(_ value: Double, _ exponent: Double) -> Double { (value < 0 ? -1 : 1) * pow(abs(value), exponent) }
    static func multiply(_ matrix: [[Double]], _ value: SIMD3<Double>) -> SIMD3<Double> {
        let v = matrix.map { $0[0] * value.x + $0[1] * value.y + $0[2] * value.z }
        return SIMD3(v[0], v[1], v[2])
    }
    static func map(_ value: SIMD3<Double>, _ transform: (Double) -> Double) -> SIMD3<Double> { SIMD3(transform(value.x), transform(value.y), transform(value.z)) }
    static func distance(_ a: SIMD3<Double>, _ b: SIMD3<Double>) -> Double {
        let v = a - b
        return sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    }
    static func number(_ value: Double) -> String { String(format: "%.5g", abs(value) < 0.000005 ? 0 : value) }
    static func text(_ value: SIMD3<Double>) -> String { [value.x, value.y, value.z].map(number).joined(separator: " ") }
    static func polar(_ value: SIMD3<Double>, name: String, threshold: Double) -> String {
        let chroma = hypot(value.y, value.z)
        let hue = (atan2(value.z, value.y) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
        return "\(name)(\(number(value.x))\(name == "lch" ? "%" : "") \(number(chroma)) \(chroma <= threshold ? "none" : number(hue)))"
    }
}
