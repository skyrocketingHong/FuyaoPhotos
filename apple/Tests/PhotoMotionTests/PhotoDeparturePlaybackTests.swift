import Foundation
import Testing
import PhotoMotionCore

struct PhotoDeparturePlaybackTests {
    @Test func retainedSharedPagesCannotConsumeOrReplayAnotherOwnersEvent() {
        let now = ContinuousClock.now
        let owner = UUID()
        let otherPage = UUID()
        let otherWindow = UUID()
        var state = PhotoDeparturePlayback()
        let ticket = state.publish(ownerID: owner, now: now, lifetime: .seconds(4))
        #expect(state.claim(id: ticket.id, ownerID: otherPage, now: now) == nil)
        #expect(state.claim(id: ticket.id, ownerID: otherWindow, now: now) == nil)
        #expect(state.claim(id: ticket.id, ownerID: owner, now: now) == ticket)
        #expect(state.claim(id: ticket.id, ownerID: owner, now: now.advanced(by: .milliseconds(100))) == nil)
        #expect(!state.hidesImportContent(ownerID: otherPage, now: now))
    }

    @Test func leavingBeforeTheFirstFramePermanentlyCancelsTheEvent() {
        let now = ContinuousClock.now
        let owner = UUID()
        var state = PhotoDeparturePlayback()
        let ticket = state.publish(ownerID: owner, now: now, lifetime: .seconds(4))
        let cancelled = state.cancel(ownerID: owner)
        #expect(cancelled)
        #expect(state.claim(id: ticket.id, ownerID: owner, now: now.advanced(by: .milliseconds(10))) == nil)
        #expect(state.claim(id: ticket.id, ownerID: owner, now: now.advanced(by: .seconds(5))) == nil)
        #expect(!state.hidesImportContent(ownerID: owner, now: now))
    }

    @Test func interruptingAClaimedEventDoesNotQueueItForTheNextActivation() {
        let now = ContinuousClock.now
        let owner = UUID()
        var state = PhotoDeparturePlayback()
        let ticket = state.publish(ownerID: owner, now: now, lifetime: .seconds(4))
        #expect(state.claim(id: ticket.id, ownerID: owner, now: now) != nil)
        let cancelled = state.cancel(ownerID: owner)
        #expect(cancelled)
        #expect(!state.isCurrent(id: ticket.id, ownerID: owner, now: now.advanced(by: .seconds(1))))
        #expect(state.claim(id: ticket.id, ownerID: owner, now: now.advanced(by: .seconds(1))) == nil)
    }

    @Test func lateConsumptionKeepsTheOriginalDeadlineAndWaitsForTheRenderTail() {
        let now = ContinuousClock.now
        let owner = UUID()
        var state = PhotoDeparturePlayback()
        let ticket = state.publish(ownerID: owner, now: now, lifetime: .seconds(4))
        #expect(state.hidesImportContent(ownerID: owner, now: now))
        let claimed = state.claim(id: ticket.id, ownerID: owner, now: now.advanced(by: .seconds(1)))
        #expect(claimed?.expiresAt == now.advanced(by: .seconds(4)))
        #expect(state.hidesImportContent(ownerID: owner, now: now.advanced(by: .seconds(1))))
        let revealed = state.reveal(id: ticket.id, ownerID: owner, now: now.advanced(by: .milliseconds(1250)))
        #expect(revealed)
        #expect(!state.hidesImportContent(ownerID: owner, now: now.advanced(by: .milliseconds(1250))))
        #expect(state.isCurrent(id: ticket.id, ownerID: owner, now: now.advanced(by: .milliseconds(1250))))
        #expect(!state.isCurrent(id: ticket.id, ownerID: owner, now: ticket.expiresAt))
    }

    @Test func expiredUnclaimedEventsCannotRestartWhenATabReturns() {
        let now = ContinuousClock.now
        let owner = UUID()
        var state = PhotoDeparturePlayback()
        let ticket = state.publish(ownerID: owner, now: now, lifetime: .seconds(4))
        #expect(state.claim(id: ticket.id, ownerID: owner, now: ticket.expiresAt) == nil)
        #expect(!state.hidesImportContent(ownerID: owner, now: ticket.expiresAt))
        let finished = state.finish(id: ticket.id, ownerID: owner)
        #expect(finished)
        #expect(state.ticket == nil)
    }

    @Test func oldCancellationCannotClearANewerCloseOrReplacement() {
        let now = ContinuousClock.now
        let owner = UUID()
        var state = PhotoDeparturePlayback()
        let first = state.publish(ownerID: owner, now: now, lifetime: .seconds(4))
        #expect(state.claim(id: first.id, ownerID: owner, now: now) != nil)
        let second = state.publish(ownerID: owner, now: now.advanced(by: .seconds(1)),
            lifetime: .seconds(4))
        let oldFinished = state.finish(id: first.id, ownerID: owner)
        #expect(!oldFinished)
        let oldRevealed = state.reveal(id: first.id, ownerID: owner, now: now.advanced(by: .seconds(2)))
        #expect(!oldRevealed)
        #expect(state.hidesImportContent(ownerID: owner, now: now.advanced(by: .seconds(2))))
        #expect(state.ticket == second)
        #expect(state.claim(id: second.id, ownerID: owner, now: now.advanced(by: .seconds(1))) == second)
        let wrongOwnerCancelled = state.cancel(ownerID: UUID())
        #expect(!wrongOwnerCancelled)
        #expect(state.ticket == second)
    }
}
