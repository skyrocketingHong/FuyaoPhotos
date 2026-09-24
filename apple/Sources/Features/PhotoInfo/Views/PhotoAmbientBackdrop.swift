import SwiftUI

struct PhotoAmbientBackdrop: View {
    let sourceURL: URL
    @State private var image: CGImage?
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black
                if let image, !reduceTransparency {
                    Image(decorative: image, scale: 1, orientation: .up)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                        .blur(radius: 44)
                        .overlay(.black.opacity(0.56))
                        .mask {
                            LinearGradient(stops: [
                                .init(color: .white, location: 0),
                                .init(color: .white, location: 0.38),
                                .init(color: .clear, location: 0.64)
                            ], startPoint: .top, endPoint: .bottom)
                        }
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
        }
        .allowsHitTesting(false)
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
