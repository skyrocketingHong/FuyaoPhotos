import SwiftUI

struct ColorLoupeContact {
    let location: CGPoint
    let normalizedPoint: CGPoint
    let imageSize: CGSize
}

struct ColorPhotoViewport: View {
    let image: CGImage
    let hdr: Bool
    let point: CGPoint
    var hex: String?
    let onSample: (CGPoint) -> Void
    @State private var contact: ColorLoupeContact?
    @State private var showingLoupe = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topLeading) {
                NativeColorPhotoViewport(image: image, hdr: hdr, point: point, onSample: onSample, onLoupe: updateLoupe)
                if showingLoupe, let contact {
                    let placement = ColorLoupePlacement(finger: contact.location, panel: geometry.size)
                    ColorSamplingLens(image: image, contact: contact, hex: hex)
                        .frame(width: placement.diameter, height: placement.diameter)
                        .transition(reduceMotion ? .opacity : .scale(scale: 0.4,
                            anchor: placement.belowFinger ? .top : .bottom).combined(with: .opacity))
                        .position(placement.center)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
            }
            .onChange(of: geometry.size) { _, _ in updateLoupe(nil) }
        }
        .onChange(of: ObjectIdentifier(image)) { _, _ in updateLoupe(nil) }
        .onChange(of: scenePhase) { _, phase in if phase != .active { updateLoupe(nil) } }
        .onDisappear { showingLoupe = false; contact = nil }
    }

    private func updateLoupe(_ value: ColorLoupeContact?) {
        if let value { contact = value }
        guard showingLoupe != (value != nil) else { return }
        withAnimation(reduceMotion ? .easeOut(duration: 0.12) : .spring(response: value == nil ? 0.45 : 0.3, dampingFraction: 0.7)) {
            showingLoupe = value != nil
        }
    }
}

private struct ColorSamplingLens: View {
    let image: CGImage
    let contact: ColorLoupeContact
    let hex: String?
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        GeometryReader { geometry in
            Canvas { context, size in
                // The sphere doubles the center: 1.5× source drawing gives a 3× sampling point.
                let scale: CGFloat = reduceTransparency ? 3 : 1.5
                let width = contact.imageSize.width * scale
                let height = contact.imageSize.height * scale
                context.draw(Image(decorative: image, scale: 1), in: CGRect(
                    x: size.width / 2 - contact.normalizedPoint.x * width,
                    y: size.height / 2 - contact.normalizedPoint.y * height, width: width, height: height))
            }
            .layerEffect(ShaderLibrary.fuyaoColorLens(.float2(geometry.size)),
                maxSampleOffset: CGSize(width: 20, height: 20), isEnabled: !reduceTransparency)
            .clipShape(.circle)
            .overlay { Circle().strokeBorder(.white.opacity(0.95), lineWidth: 2.5) }
            .overlay {
                Image(systemName: "plus").font(.system(size: 16, weight: .medium))
                    .foregroundStyle(.white).shadow(color: .black, radius: 1)
            }
            .overlay(alignment: .bottom) {
                if let hex {
                    Text(verbatim: hex).font(.system(size: 12, weight: .medium, design: .monospaced))
                        .foregroundStyle(.primary).padding(.horizontal, 5).padding(.vertical, 2)
                        .background(.regularMaterial, in: .capsule).padding(.bottom, 10)
                }
            }
            .shadow(color: .black.opacity(0.28), radius: 7, y: 4)
        }
    }
}
