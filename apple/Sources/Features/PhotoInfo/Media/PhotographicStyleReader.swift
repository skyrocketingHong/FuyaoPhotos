import Foundation
import ImageIO

nonisolated enum PhotographicStyleReader {
    private static let nameKeys: Set<String> = [
        "PhotographicStyle", "PhotographicStyleName", "SmartStyleName", "CameraStyle",
        "LeicaStyle", "LeicaColorMode"
    ]

    static func name(properties: [String: Any], metadata: CGImageMetadata? = nil, vendorName: String? = nil) -> String? {
        if let metadata {
            var names = Set<String>()
            CGImageMetadataEnumerateTagsUsingBlock(metadata, nil, [kCGImageMetadataEnumerateRecursively: true] as CFDictionary) { _, tag in
                if let key = CGImageMetadataTagCopyName(tag) as String?, nameKeys.contains(key),
                   let name = readableName(CGImageMetadataTagCopyValue(tag) as? String) { names.insert(name) }
                return true
            }
            if names.count == 1 { return names.first }
            if names.count > 1 { return nil }
        }
        if let vendorName = readableName(vendorName) { return vendorName }
        let maker = properties[kCGImagePropertyMakerAppleDictionary as String] as? [String: Any] ?? [:]
        if let modern = maker["84"] {
            return dictionary(modern).flatMap(modernName)
        }
        if let legacy = dictionary(maker["64"]), let number = legacy["_3"] as? NSNumber,
           number.doubleValue.isFinite, (1...5).contains(number.doubleValue), number.doubleValue == Double(number.intValue) {
            // ExifTool Apple SemanticStyle: _3 is the first-generation preset identifier.
            return [1: "Standard", 2: "Vibrant", 3: "Rich Contrast", 4: "Warm", 5: "Cool"][number.intValue]
        }
        return nil
    }

    static func modernName(_ values: [String: Any]) -> String? {
        if let name = readableName(values["Preset"] as? String) { return name }
        // Only the neutral contract is verified; unknown casts must not become a guessed style.
        let neutral: [String: Double] = ["0": 1, "1": 0, "2": 0, "3": 1, "4": 1, "5": 1, "6": 4, "7": 0,
                                        "8": 1, "9": 1, "10": 0, "11": 0, "12": 1]
        guard (0...7).allSatisfy({ values[String($0)] != nil }),
              values.allSatisfy({ key, value in
                  guard let expected = neutral[key], let actual = value as? NSNumber else { return false }
                  return actual.doubleValue == expected
              }) else { return nil }
        return "Standard"
    }

    static func readableName(_ value: String?) -> String? {
        guard let value else { return nil }
        let name = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, name.count <= 128,
              name.rangeOfCharacter(from: .letters) != nil,
              name.rangeOfCharacter(from: .controlCharacters) == nil else { return nil }
        return name
    }

    static func displayName(_ name: String?, prefix: String?) -> String {
        guard let name = readableName(name) else { return "" }
        let prefix = prefix?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !prefix.isEmpty, prefix.count <= 64,
              prefix.unicodeScalars.allSatisfy({ !CharacterSet.controlCharacters.contains($0) }) else { return name }
        if name.caseInsensitiveCompare(prefix) == .orderedSame ||
            name.range(of: prefix + " ", options: [.anchored, .caseInsensitive]) != nil { return name }
        return prefix + " " + name
    }

    private static func dictionary(_ value: Any?) -> [String: Any]? {
        if let dictionary = value as? [String: Any] { return dictionary }
        guard let data = value as? Data, data.count <= 65_536 else { return nil }
        return (try? PropertyListSerialization.propertyList(from: data, options: [], format: nil)) as? [String: Any]
    }
}
