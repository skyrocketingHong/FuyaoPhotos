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
    @State private var progress = 0.0

    private struct Run: Equatable {
        var removed: Bool
        var motion: Bool
        var active: Bool
    }

    var body: some View {
        Group {
            if removed {
                ZStack {
                    if let snapshot {
                        DissolveSnapshot(progress: progress, image: snapshot, sourceSize: size,
                            reverse: layoutDirection == .rightToLeft)
                    }
                }
                .frame(width: size.width, height: size.height)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            } else {
                content()
                    .onGeometryChange(for: CGSize.self) { $0.size } action: { size = $0 }
            }
        }
        .task(id: Run(removed: removed, motion: !reduceMotion, active: scenePhase == .active)) {
            guard removed else { snapshot = nil; progress = 0; return }
            guard !reduceMotion, scenePhase == .active, size.width > 0, size.height > 0 else {
                onFinished(); return
            }
            let scale = min(displayScale, 2)
            guard size.width * size.height * scale * scale <= Double(PhotoMotionTokens.snapshotPixelLimit) else {
                onFinished(); return
            }
            // Snapshot only this ordinary form row; the native HDR image never enters this renderer.
            let renderer = ImageRenderer(content: content()
                .frame(width: size.width, height: size.height)
                .environment(\.colorScheme, colorScheme)
                .environment(\.dynamicTypeSize, dynamicTypeSize)
                .environment(\.layoutDirection, layoutDirection))
            renderer.scale = scale
            guard let image = renderer.cgImage else { onFinished(); return }
            snapshot = image
            withAnimation(.linear(duration: Double(PhotoMotionTokens.dissolveMillis) / 1000)) { progress = 1 }
            do { try await Task.sleep(for: .milliseconds(PhotoMotionTokens.dissolveMillis)) }
            catch { return }
            guard !Task.isCancelled else { return }
            snapshot = nil
            onFinished()
        }
    }
}

@Animatable
private struct DissolveSnapshot: View {
    var progress: Double
    @AnimatableIgnored let image: CGImage
    @AnimatableIgnored let sourceSize: CGSize
    @AnimatableIgnored let reverse: Bool

    var body: some View {
        Canvas { context, _ in
            let grid = ParticleMotion.grid(width: sourceSize.width, height: sourceSize.height)
            guard grid.count > 0 else { return }
            let resolved = context.resolve(Image(decorative: image, scale: 1))
            let width = sourceSize.width / Double(grid.columns)
            let height = sourceSize.height / Double(grid.rows)
            for row in 0..<grid.rows {
                for column in 0..<grid.columns {
                    let index = row * grid.columns + (reverse ? grid.columns - 1 - column : column)
                    let frame = ParticleMotion.frame(index: index, columns: grid.columns, progress: progress, reverse: reverse)
                    guard frame.alpha > 0 else { continue }
                    let rect = CGRect(x: Double(column) * width, y: Double(row) * height, width: width, height: height)
                    var particle = context
                    particle.opacity = frame.alpha
                    particle.translateBy(x: rect.midX + frame.x, y: rect.midY + frame.y)
                    particle.scaleBy(x: frame.scale, y: frame.scale)
                    particle.translateBy(x: -rect.midX, y: -rect.midY)
                    particle.clip(to: Path(rect))
                    particle.draw(resolved, in: CGRect(origin: .zero, size: sourceSize))
                }
            }
        }
    }
}
