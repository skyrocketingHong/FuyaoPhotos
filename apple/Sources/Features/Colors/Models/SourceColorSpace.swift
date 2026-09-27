import Foundation

nonisolated extension ColorResultSpace {
    static func matchingProfile(_ name: String) -> Self {
        let key = name.lowercased().filter { $0.isLetter || $0.isNumber }
        if key.contains("displayp3") { return .p3 }
        if ["bt2020", "rec2020", "itur2020", "bt2100", "rec2100", "itur2100"].contains(where: key.contains) { return .rec2020 }
        if key.contains("adobergb") || key.contains("a98") { return .a98 }
        if key.contains("srgb") { return .sRGB }
        if key == "lab" || key.contains("cielab") || key.contains("genericlab") { return .cie }
        return .xyz
    }
}
