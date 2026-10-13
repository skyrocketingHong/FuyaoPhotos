import Foundation

nonisolated public struct PhotoImportTargetLifecycle: Sendable {
    public struct Attempt: Equatable, Sendable {
        public let requestID: UUID
        fileprivate let generation: UInt64
    }

    private var requestID: UUID?
    private var generation: UInt64 = 0
    private var ready = false

    public init() {}

    public mutating func begin(id: UUID) {
        requestID = id
        generation &+= 1
        ready = false
    }

    public mutating func nextAttempt(id: UUID) -> Attempt? {
        guard requestID == id, !ready else { return nil }
        generation &+= 1
        return Attempt(requestID: id, generation: generation)
    }

    public mutating func complete(_ attempt: Attempt) -> Bool {
        guard accepts(attempt), !ready else { return false }
        ready = true
        return true
    }

    public mutating func fail(_ attempt: Attempt) -> Bool {
        guard accepts(attempt), !ready else { return false }
        return cancel(id: attempt.requestID)
    }

    @discardableResult
    public mutating func cancel(id: UUID? = nil) -> Bool {
        guard requestID != nil, id == nil || requestID == id else { return false }
        requestID = nil
        generation &+= 1
        ready = false
        return true
    }

    private func accepts(_ attempt: Attempt) -> Bool {
        requestID == attempt.requestID && generation == attempt.generation
    }
}
