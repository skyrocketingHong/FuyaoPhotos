import SwiftUI

enum PhotoPageLayout {
    static let margin: CGFloat = 20
    static let cardInset: CGFloat = 20
}

struct PhotoPageIntro: View {
    let title: LocalizedStringKey
    let description: LocalizedStringKey
    let symbol: String

    /// The camera-at-work mark: the page symbol framed by viewfinder brackets —
    /// every empty workspace reads as a camera waiting for a photo.
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ZStack {
                Image(systemName: "viewfinder")
                    .font(.largeTitle)
                    .foregroundStyle(.tertiary)
                Image(systemName: symbol)
                    .font(.title3.weight(.medium))
                    .foregroundStyle(.tint)
            }
            .accessibilityHidden(true)
            Text(title).font(.title2.bold())
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
            .contentMargins(.horizontal, PhotoPageLayout.margin, for: .scrollContent)
            .listRowInsets(EdgeInsets(top: 12, leading: PhotoPageLayout.cardInset,
                                     bottom: 12, trailing: PhotoPageLayout.cardInset))
            .scrollEdgeEffectStyle(.soft, for: .top)
            .scrollEdgeEffectHidden(false, for: .top)
    }
}

extension View {
    func photoPageForm() -> some View { modifier(PhotoPageFormStyle()) }
}
