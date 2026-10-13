import Foundation
import Testing
import PhotoMotionCore

struct PhotoImportTargetLifecycleTests {
    @Test func layoutRecaptureRejectsTheOlderResultAndFailure() throws {
        let id = UUID()
        var state = PhotoImportTargetLifecycle()
        state.begin(id: id)
        let firstValue = state.nextAttempt(id: id)
        let first = try #require(firstValue)
        let secondValue = state.nextAttempt(id: id)
        let second = try #require(secondValue)
        let oldCompleted = state.complete(first)
        let oldFailed = state.fail(first)
        let newCompleted = state.complete(second)
        let duplicateCompleted = state.complete(second)
        #expect(!oldCompleted)
        #expect(!oldFailed)
        #expect(newCompleted)
        #expect(!duplicateCompleted)
    }

    @Test func newImportCancelsAnInFlightTargetBeforeItCanPublish() throws {
        let id = UUID()
        var state = PhotoImportTargetLifecycle()
        state.begin(id: id)
        let attemptValue = state.nextAttempt(id: id)
        let attempt = try #require(attemptValue)
        let cancelled = state.cancel(id: id)
        let completed = state.complete(attempt)
        let failed = state.fail(attempt)
        let next = state.nextAttempt(id: id)
        #expect(cancelled)
        #expect(!completed)
        #expect(!failed)
        #expect(next == nil)
    }

    @Test func appearanceInvalidationAlsoCancelsAnAlreadyPreparedTarget() throws {
        let id = UUID()
        var state = PhotoImportTargetLifecycle()
        state.begin(id: id)
        let attemptValue = state.nextAttempt(id: id)
        let attempt = try #require(attemptValue)
        let prepared = state.complete(attempt)
        let invalidated = state.cancel(id: id)
        let lateCompleted = state.complete(attempt)
        let next = state.nextAttempt(id: id)
        #expect(prepared)
        #expect(invalidated)
        #expect(!lateCompleted)
        #expect(next == nil)
    }

    @Test func lateCallbacksFromOneCloseCannotClearTheNextClose() throws {
        let firstID = UUID(), secondID = UUID()
        var state = PhotoImportTargetLifecycle()
        state.begin(id: firstID)
        let firstValue = state.nextAttempt(id: firstID)
        let first = try #require(firstValue)
        state.begin(id: secondID)
        let secondValue = state.nextAttempt(id: secondID)
        let second = try #require(secondValue)
        let oldCancelled = state.cancel(id: firstID)
        let oldFailed = state.fail(first)
        let oldCompleted = state.complete(first)
        let newCompleted = state.complete(second)
        #expect(!oldCancelled)
        #expect(!oldFailed)
        #expect(!oldCompleted)
        #expect(newCompleted)
    }
}
