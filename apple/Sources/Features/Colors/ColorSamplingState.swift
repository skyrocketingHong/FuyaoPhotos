import SwiftUI

@MainActor @Observable final class ColorSamplingState {
    var image: CGImage?
    var sample: PhotoColorSample?
    var information: PhotoColorDescription?
    var busy = false
    var error: String?
    var hdr = true
    var point = CGPoint(x: 0.5, y: 0.5)
    @ObservationIgnored private var sampling: Task<Void, Never>?
    @ObservationIgnored private var documentID: UUID?
    @ObservationIgnored var departureCapture: PhotoViewportCapture?

    func load(_ document: CardDocument?) async {
        sampling?.cancel()
        documentID = document?.id
        image = nil; sample = nil; information = nil; error = nil
        guard let document else { busy = false; return }
        busy = true
        defer { if documentID == document.id { busy = false } }
        do {
            let native = try await CardImageProcessor.shared.preview(document.sourceURL, card: PhotoCard(),
                hdr: document.metadata.hdr, maxDimension: 2400)
            let description = try await PhotoColorSampler.shared.describe(document.sourceURL)
            try Task.checkCancellation()
            image = native
            information = description
            point = CGPoint(x: 0.5, y: 0.5)
            sample(at: point, document: document)
        } catch is CancellationError { }
        catch { self.error = (error as? CardError)?.localizedDescription ?? CardError.invalidImage.localizedDescription }
    }

    func sample(at point: CGPoint, document: CardDocument) {
        self.point = CGPoint(x: min(1, max(0, point.x)), y: min(1, max(0, point.y)))
        sampling?.cancel()
        let point = self.point
        sampling = Task {
            do {
                let result = try await PhotoColorSampler.shared.sample(document.sourceURL,
                    normalizedX: point.x, normalizedY: point.y)
                try Task.checkCancellation()
                guard documentID == document.id else { return }
                sample = result
                error = nil
            } catch is CancellationError { }
            catch { self.error = CardError.invalidImage.localizedDescription }
        }
    }
}
