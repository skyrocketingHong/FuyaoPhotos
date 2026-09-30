import Foundation

nonisolated enum AppleCameraNames {
    struct Camera: Equatable {
        let name: String
        let focalLength: Double
        let nativeZoom: Double

        func zoom(at equivalent: Double) -> Double? {
            guard equivalent.isFinite, equivalent > 0 else { return nil }
            return equivalent / focalLength * nativeZoom
        }
    }

    static func resolve(make: String, model: String, lens: String, cameraType: Int?) -> Camera? {
        let model = model.split(whereSeparator: \.isWhitespace).joined(separator: " ").lowercased()
        guard make.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == "apple" else { return nil }
        let pro17 = ["iphone 17 pro", "iphone 17 pro max"].contains(model)
        let pro18 = ["iphone 18 pro", "iphone 18 pro max"].contains(model)
        let air = model == "iphone air"
        guard pro17 || pro18 || air else { return nil }
        let description = lens.lowercased()
        guard cameraType != 6, !description.contains("front"), !description.contains("selfie") else { return nil }

        let role: Int
        if cameraType == 0 { role = 0 }
        else if cameraType == 1 { role = 1 }
        else if description.contains("ultra wide") { role = 0 }
        else if description.contains("telephoto") { role = 2 }
        else if description.contains("fusion main") { role = 1 }
        else {
            // LensModel records the lens's maximum aperture, not the shot's FNumber.
            guard description.contains("back"),
                  let range = description.range(of: #"f/([0-9]+(?:\.[0-9]+)?)$"#, options: .regularExpression),
                  let aperture = Double(description[range].dropFirst(2)) else { return nil }
            if abs(aperture - (pro18 ? 1.48 : air ? 1.6 : 1.78)) < 0.001 { role = 1 }
            else if !air && abs(aperture - 2.2) < 0.001 { role = 0 }
            else if !air && abs(aperture - 2.8) < 0.001 { role = 2 }
            else { return nil }
        }
        switch role {
        case 0 where !air: return Camera(name: "Fusion Ultra Wide", focalLength: 13, nativeZoom: 0.5)
        case 1: return Camera(name: "Fusion Main", focalLength: air ? 26 : 24, nativeZoom: 1)
        case 2 where !air: return Camera(name: "Fusion Telephoto", focalLength: 100, nativeZoom: 4)
        default: return nil
        }
    }
}
