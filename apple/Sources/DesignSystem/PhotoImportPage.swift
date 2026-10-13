import SwiftUI

struct PhotoImportPage<Actions: View>: View {
    let title: LocalizedStringKey
    let description: LocalizedStringKey
    let symbol: String
    let busy: Bool
    @ViewBuilder let actions: () -> Actions
    @Environment(\.self) private var environment
    @Environment(\.photoImportContentVisible) private var importContentVisible
    @Environment(\.photoImportTransitionCapture) private var transitionCapture
    @State private var cardFrame = CGRect.zero
    @State private var nativeContext = PhotoImportNativeContext()

    private struct CaptureKey: Equatable {
        let request: PhotoImportTransitionCapture.Request?
        let size: CGSize
        let frame: CGRect
        let appearance: PhotoImportAppearance
        let busy: Bool
        let attached: Bool
    }

    var body: some View {
        GeometryReader { geometry in
            let appearance = PhotoImportAppearance(environment: environment)
            let key = CaptureKey(request: transitionCapture?.request, size: geometry.size, frame: cardFrame,
                appearance: appearance, busy: busy, attached: nativeContext.attached)
            importForm(width: geometry.size.width) {
                cardFrame = $0
                transitionCapture?.updateContentFrame($0)
            }
            .opacity(importContentVisible ? 1 : 0)
            .animation(nil, value: importContentVisible)
            .allowsHitTesting(importContentVisible)
            .accessibilityHidden(!importContentVisible)
            .background { PhotoImportNativeContextReader(context: nativeContext).frame(width: 0, height: 0) }
            .onChange(of: appearance) { _, updated in
                PhotoDepartureDiagnostics.emit("target.appearance-changed")
                if let capture = transitionCapture, let target = capture.target,
                   target.appearance != updated { capture.invalidate(id: target.requestID) }
            }
            .onChange(of: busy) { _, value in
                if value, let request = transitionCapture?.request { transitionCapture?.invalidate(id: request.id) }
            }
            .task(id: key) { await captureTarget(key) }
        }
    }

    private func importForm(width: CGFloat, space: UUID? = nil,
                            measured: @escaping (CGRect) -> Void) -> some View {
        PhotoImportForm(title: title, description: description, symbol: symbol, busy: busy,
            availableWidth: width, measurementSpace: space, measured: measured, actions: actions)
    }

    private func captureTarget(_ key: CaptureKey) async {
        PhotoDepartureDiagnostics.emit("target.task", values: [key.request == nil ? 0 : 1, key.attached ? 1 : 0,
            Double(key.frame.width), Double(key.frame.height), Double(key.size.width), Double(key.size.height)])
        guard let request = key.request, let transitionCapture, key.attached,
              !key.frame.isEmpty, key.size.width > 0, key.size.height > 0 else { return }
        if key.busy { transitionCapture.invalidate(id: request.id); return }
        if let target = transitionCapture.target {
            if target.appearance != key.appearance { transitionCapture.invalidate(id: request.id) }
            return
        }
        guard let attempt = transitionCapture.nextAttempt(id: request.id) else { return }
        PhotoDepartureDiagnostics.emit("target.capture-start")
        let measurement = PhotoImportNativeSnapshotter.Measurement()
        let space = UUID()
        let content = importForm(width: key.size.width, space: space) { measurement.frame = $0 }
            .coordinateSpace(name: space)
            .modifier(PhotoImportSnapshotAppearance(appearance: key.appearance))
        let background = PhotoImportFormBackground()
            .modifier(PhotoImportSnapshotAppearance(appearance: key.appearance))
        do {
            let result = try await PhotoImportNativeSnapshotter.capture(content: content, background: background,
                pointSize: key.size, measurement: measurement, colorScheme: key.appearance.colorScheme,
                displayScale: key.appearance.displayScale, context: nativeContext, deadline: request.deadline)
            try Task.checkCancellation()
            guard abs(result.contentFrame.width - key.frame.width) <= 1,
                  abs(result.contentFrame.height - key.frame.height) <= 1 else {
                PhotoDepartureDiagnostics.emit("target.size-mismatch", values: [Double(result.contentFrame.width),
                    Double(result.contentFrame.height), Double(key.frame.width), Double(key.frame.height)])
                transitionCapture.fail(attempt)
                return
            }
            let frame = result.frame.offsetBy(dx: key.frame.minX - result.contentFrame.minX,
                dy: key.frame.minY - result.contentFrame.minY)
            guard cardFrame == key.frame, transitionCapture.request?.id == request.id else {
                PhotoDepartureDiagnostics.emit("target.changed-before-submit")
                return
            }
            transitionCapture.submit(PhotoImportTransitionTarget(attempt: attempt, image: result.image,
                frame: frame, contentFrame: key.frame, appearance: key.appearance))
        } catch is CancellationError {
            PhotoDepartureDiagnostics.emit("target.capture-cancelled")
        } catch {
            PhotoDepartureDiagnostics.emit("target.capture-error")
            if let failure = error as? PhotoImportNativeSnapshotter.Failure {
                switch failure {
                case .unavailable: PhotoDepartureDiagnostics.emit("target.capture-unavailable")
                case .expired: PhotoDepartureDiagnostics.emit("target.capture-expired")
                case .invalidBounds: PhotoDepartureDiagnostics.emit("target.capture-invalid-bounds")
                }
            }
            transitionCapture.fail(attempt)
        }
    }
}
