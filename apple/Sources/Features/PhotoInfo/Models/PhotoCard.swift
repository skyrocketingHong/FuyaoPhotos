import Foundation
import CoreGraphics
import Observation

nonisolated enum CardField: String, CaseIterable, Codable, Identifiable {
    case device, author, location, camera, imageSize, focalLength, exposure, aperture, iso
    var id: Self { self }
    var titleKey: String { "card.field.\(rawValue)" }
    var accent: Bool { self == .device || self == .author || self == .location }
    var label: String {
        switch self {
        case .device: ""
        case .author: "SHOT BY"
        case .location: "LOCATION"
        case .camera: "CAMERA"
        case .imageSize: "IMAGE SIZE"
        case .focalLength: "FOCAL LENGTH"
        case .exposure: "EXPOSURE TIME"
        case .aperture: "APERTURE"
        case .iso: "ISO"
        }
    }
}

nonisolated struct PhotoCard: Codable, Equatable, Sendable {
    var fields: [CardField: String] = [:]
    var style = PhotoCardStyle()
    subscript(field: CardField) -> String {
        get { fields[field, default: ""] }
        set { fields[field] = newValue }
    }
    var rows: [(field: CardField, text: String, accent: Bool)] {
        CardField.allCases.compactMap { field in
            let raw = self[field].trimmingCharacters(in: .whitespacesAndNewlines)
            guard !raw.isEmpty else { return nil }
            let value = field == .device && raw.lowercased().hasPrefix("iphone")
                ? "iPHONE" + raw.dropFirst(6).uppercased() : raw.uppercased()
            return (field, field.label.isEmpty ? value : "\(field.label): \(value)", field.accent)
        }
    }
}

nonisolated struct PhotoCardStyle: Codable, Equatable, Sendable {
    var scale = 1.0
    var textScale = 1.0
    var opacity = 0.6
    var blur = 25.0
    var rightInset = 77.0
    var bottomInset = 35.0
    var cornerRadius = 20.0
}

nonisolated enum CardExportFormat: String, CaseIterable, Codable, Identifiable {
    case jpeg, heic, png
    var id: Self { self }
    var title: String { rawValue.uppercased() }
    var fileExtension: String { self == .jpeg ? "jpg" : rawValue }
}

nonisolated struct CardSaveOptions: Codable, Equatable, Sendable {
    var format: CardExportFormat = .jpeg
    var quality = 100.0
    var keepExif = true
    var keepLocation = false
    var keepCaptureTime = true
    var updateOriginal = false
}

@MainActor @Observable final class CardPreferences {
    static let shared = CardPreferences()
    var author: String = UserDefaults.standard.string(forKey: "card.defaultAuthor") ?? "" {
        didSet { UserDefaults.standard.set(author, forKey: "card.defaultAuthor") }
    }
    var resolveLocation: Bool = UserDefaults.standard.bool(forKey: "card.resolveLocation") {
        didSet { UserDefaults.standard.set(resolveLocation, forKey: "card.resolveLocation") }
    }
    var saveOptions: CardSaveOptions = {
        guard let data = UserDefaults.standard.data(forKey: "card.saveOptions"),
              let options = try? JSONDecoder().decode(CardSaveOptions.self, from: data) else { return CardSaveOptions() }
        return options
    }() {
        didSet {
            if let data = try? JSONEncoder().encode(saveOptions) { UserDefaults.standard.set(data, forKey: "card.saveOptions") }
        }
    }
}

nonisolated enum CardError: Error, LocalizedError {
    case invalidImage, tooLarge, overflow, exportFailed, permission, unavailable, unsupportedMedia, hdrFormat, livePairing, videoMetadata
    case imageEncoding, imageValidation, auxiliaryEncoding, librarySave, storageFull, libraryResource
    var errorDescription: String? { NSLocalizedString("card.error.\(self)", comment: "") }
}
