import Foundation

/// Editor canvas style: keep the darkroom look or let system chrome follow the device appearance.
enum CardAppearance: String, CaseIterable {
    case darkroom
    case system

    static let storageKey = "cardAppearance"
}
