import SwiftUI

struct PhotoAmbientBackdrop: View {
    let sourceURL: URL
    var featherEdges = true
    @State private var image: CGImage?
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.colorSchemeContrast) private var colorSchemeContrast

    private var surfaceOpacity: Double {
        colorSchemeContrast == .increased ? 0.96 : (colorScheme == .dark ? 0.80 : 0.88)
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if let image, !reduceTransparency {
                    Image(decorative: image, scale: 1, orientation: .up)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                        .blur(radius: 44)
                        .overlay(PhotoPreviewTheme.surface.opacity(surfaceOpacity))
                        .mask {
                            LinearGradient(stops: [
                                .init(color: featherEdges ? .clear : .white, location: 0),
                                .init(color: .white, location: 0.16),
                                .init(color: .white, location: 0.4),
                                .init(color: .clear, location: 1)
                            ], startPoint: .top, endPoint: .bottom)
                        }
                        .mask {
                            if featherEdges {
                                LinearGradient(stops: [
                                    .init(color: .clear, location: 0),
                                    .init(color: .white, location: 0.16),
                                    .init(color: .white, location: 0.84),
                                    .init(color: .clear, location: 1)
                                ], startPoint: .leading, endPoint: .trailing)
                            } else { Rectangle() }
                        }
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .task(id: AmbientKey(sourceURL: sourceURL, reduceTransparency: reduceTransparency)) {
            image = nil
            guard !reduceTransparency else { return }
            do {
                let thumbnail = try await CardImageProcessor.shared.preview(
                    sourceURL, card: PhotoCard(), hdr: false, maxDimension: 700)
                try Task.checkCancellation()
                image = thumbnail
            } catch is CancellationError {
            } catch {
                image = nil
            }
        }
    }
}

private struct AmbientKey: Hashable {
    let sourceURL: URL
    let reduceTransparency: Bool
}
