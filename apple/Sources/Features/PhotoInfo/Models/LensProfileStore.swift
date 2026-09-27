import Foundation
import Observation

@MainActor @Observable final class LensProfileStore {
    static let shared = LensProfileStore()
    private(set) var profiles: [LensProfile]
    private let defaults: UserDefaults
    private let key = "card.lensProfiles"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: key),
           let saved = try? JSONDecoder().decode([LensProfile].self, from: data),
           saved.allSatisfy(\.isValid), Set(saved.map(\.id)).count == saved.count {
            profiles = saved
        } else {
            profiles = []
        }
    }

    func save(_ draft: [LensProfile]) throws {
        // ASVS 2.2.1/2.2.3: validate finite ranges and related endpoints at persistence.
        guard draft.allSatisfy(\.isValid), Set(draft.map(\.id)).count == draft.count else {
            throw CocoaError(.coderInvalidValue)
        }
        let data = try JSONEncoder().encode(draft)
        defaults.set(data, forKey: key)
        profiles = draft
    }
}
