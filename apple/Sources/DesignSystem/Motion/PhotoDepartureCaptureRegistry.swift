import Foundation

nonisolated public struct PhotoDepartureCaptureRegistry<Value> {
    private var captures: [UUID: Value] = [:]
    private var order: [UUID] = []

    public init() {}

    public mutating func register(_ value: Value, ownerID: UUID) {
        captures[ownerID] = value
        order.removeAll { $0 == ownerID }
        order.append(ownerID)
    }

    public mutating func remove(ownerID: UUID) {
        captures.removeValue(forKey: ownerID)
        order.removeAll { $0 == ownerID }
    }

    public func capture(for ownerID: UUID) -> Value? { captures[ownerID] }

    /// Replacement imports retain their existing most-recent active-surface fallback.
    public var replacementCandidates: [Value] { order.reversed().compactMap { captures[$0] } }
}
