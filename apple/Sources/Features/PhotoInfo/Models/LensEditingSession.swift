import Foundation
import Observation

@MainActor @Observable final class LensEditingSession: Identifiable {
    let initial: LensProfileDraft
    var draft: LensProfileDraft
    nonisolated let id: UUID
    var hasChanges: Bool {
        if let current = draft.profile, let original = initial.profile { return current != original }
        return draft != initial
    }

    init(draft: LensProfileDraft) { self.id = draft.id; self.initial = draft; self.draft = draft }
}
