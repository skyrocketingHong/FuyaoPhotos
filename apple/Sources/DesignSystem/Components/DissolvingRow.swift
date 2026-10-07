import SwiftUI

struct DissolvingRow<Content: View>: View {
    let removed: Bool
    let onFinished: () -> Void
    @ViewBuilder var content: () -> Content
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.displayScale) private var displayScale
    @Environment(\.layoutDirection) private var layoutDirection
    @State private var size = CGSize.zero
    @State private var snapshot: CGImage?

    private struct Run: Equatable {
        var removed: Bool
        var motion: Bool
        var active: Bool
    }

    var body: some View {
        Group {
            if removed {
                Color.clear
                    .frame(width: size.width, height: size.height)
                    .overlay {
                        if let snapshot { TelegramDustView(image: snapshot, sourceSize: size) }
                    }
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            } else {
                content()
                    .onGeometryChange(for: CGSize.self) { $0.size } action: { size = $0 }
            }
        }
        .task(id: Run(removed: removed, motion: !reduceMotion, active: scenePhase == .active)) {
            guard removed else { snapshot = nil; return }
            guard !reduceMotion, scenePhase == .active, size.width > 0, size.height > 0 else {
                onFinished(); return
            }
            let scale = min(displayScale, 2)
            guard size.width * size.height * scale * scale <= Double(PhotoMotionTokens.snapshotPixelLimit) else {
                onFinished(); return
            }
            let renderer = ImageRenderer(content: content()
                .frame(width: size.width, height: size.height)
                .environment(\.colorScheme, colorScheme)
                .environment(\.dynamicTypeSize, dynamicTypeSize)
                .environment(\.layoutDirection, layoutDirection))
            renderer.scale = scale
            guard let image = renderer.cgImage else { onFinished(); return }
            snapshot = image
            do { try await Task.sleep(for: TelegramDustView.lifetime) } catch { return }
            guard !Task.isCancelled else { return }
            snapshot = nil
            onFinished()
        }
    }
}
