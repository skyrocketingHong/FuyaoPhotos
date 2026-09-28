import SwiftUI

enum PhotoPageLayout {
    static let margin: CGFloat = 20
    static let cardInset: CGFloat = 20
}

struct PhotoPageIntro: View {
    let title: LocalizedStringKey
    let description: LocalizedStringKey
    let symbol: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: symbol)
                .font(.largeTitle)
                .foregroundStyle(.secondary)
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
