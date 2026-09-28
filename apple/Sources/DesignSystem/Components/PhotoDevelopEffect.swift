import SwiftUI

/// The develop settle: when a photo first arrives in a workspace, a brief exposure flash
/// fades out over the media — the print develops. It runs once per view insertion (opening
/// a photo, not paging between already-open ones) and is skipped entirely under Reduce Motion.
struct PhotoDevelopEffect: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var developed = false

    func body(content: Content) -> some View {
        content
            .overlay {
                Rectangle()
                    .fill(.white)
                    // An overlay veil never touches the HDR image itself, so the EDR
                    // preview stays intact while the exposure settles.
                    .opacity(developed || reduceMotion ? 0 : 0.16)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
            .task {
                guard !developed else { return }
                if reduceMotion { developed = true }
                else { withAnimation(.easeOut(duration: 0.32)) { developed = true } }
            }
    }
}

extension View {
    /// Exposure settle for arriving photos; see ``PhotoDevelopEffect``.
    func photoDevelopEffect() -> some View { modifier(PhotoDevelopEffect()) }
}
