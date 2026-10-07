import Foundation

nonisolated public struct PhotoDeparturePlayback: Sendable {
    public struct Ticket: Equatable, Sendable {
        public let id: UUID
        public let ownerID: UUID
        public let expiresAt: ContinuousClock.Instant
    }

    public private(set) var ticket: Ticket?
    private var claimed = false
    private var revealed = false

    public init() {}

    public mutating func publish(ownerID: UUID, now: ContinuousClock.Instant, lifetime: Duration) -> Ticket {
        let deadline = now.advanced(by: lifetime)
        let ticket = Ticket(id: UUID(), ownerID: ownerID, expiresAt: deadline)
        self.ticket = ticket
        claimed = false
        revealed = false
        return ticket
    }

    public mutating func claim(id: UUID, ownerID: UUID, now: ContinuousClock.Instant) -> Ticket? {
        guard !claimed, isCurrent(id: id, ownerID: ownerID, now: now), let ticket else { return nil }
        claimed = true
        return ticket
    }

    public func isCurrent(id: UUID, ownerID: UUID, now: ContinuousClock.Instant) -> Bool {
        guard let ticket else { return false }
        return ticket.id == id && ticket.ownerID == ownerID && now < ticket.expiresAt
    }

    public func hidesImportContent(ownerID: UUID, now: ContinuousClock.Instant) -> Bool {
        guard let ticket else { return false }
        return !revealed && ticket.ownerID == ownerID && now < ticket.expiresAt
    }

    @discardableResult
    public mutating func reveal(id: UUID, ownerID: UUID, now: ContinuousClock.Instant) -> Bool {
        guard !revealed, isCurrent(id: id, ownerID: ownerID, now: now) else { return false }
        revealed = true
        return true
    }

    @discardableResult
    public mutating func finish(id: UUID, ownerID: UUID) -> Bool {
        guard ticket?.id == id, ticket?.ownerID == ownerID else { return false }
        ticket = nil
        claimed = false
        revealed = false
        return true
    }

    @discardableResult
    public mutating func cancel(ownerID: UUID) -> Bool {
        guard let ticket else { return false }
        return finish(id: ticket.id, ownerID: ownerID)
    }
}
