import SwiftUI

enum PhotoPageLayout {
    static let margin: CGFloat = PhotoPreviewMetrics.pageMargin
    static let cardInset: CGFloat = 20
}

struct PhotoPageIntro: View {
    let title: LocalizedStringKey
    let description: LocalizedStringKey
    let symbol: String
    var prominent = false

    /// The camera-at-work mark frames the page symbol with viewfinder brackets,
    /// every empty workspace reads as a camera waiting for a photo.
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 16) {
                ZStack {
                    Image(systemName: "viewfinder")
                        .font(.largeTitle)
                        .foregroundStyle(.tertiary)
                    Image(systemName: symbol)
                        .font(.title3.weight(.medium))
                        .foregroundStyle(.tint)
                }
                .accessibilityHidden(true)
                Text(title)
                    .font(prominent ? .title.bold() : .title2.bold())
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
            }
            Text(description)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
    }
}

struct PhotoPageFormStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .formStyle(.grouped)
            .tint(.secondary)
            .toggleStyle(NativeFormToggleStyle())
#if !os(macOS)
            .contentMargins(.horizontal, PhotoPageLayout.margin, for: .scrollContent)
            .listRowInsets(EdgeInsets(top: 12, leading: PhotoPageLayout.cardInset,
                                     bottom: 12, trailing: PhotoPageLayout.cardInset))
#else
            .controlSize(.regular)
#endif
            .scrollEdgeEffectStyle(.soft, for: .top)
            .scrollEdgeEffectHidden(false, for: .top)
    }
}

extension View {
    func photoPageForm() -> some View { modifier(PhotoPageFormStyle()) }
}
