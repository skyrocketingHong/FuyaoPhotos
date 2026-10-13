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
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .title2) private var symbolWidth: CGFloat = 32
    @ScaledMetric(relativeTo: .largeTitle) private var prominentSymbolWidth: CGFloat = 44

    private var headingLayout: AnyLayout {
        dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 12))
            : AnyLayout(HStackLayout(alignment: .firstTextBaseline, spacing: 16))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            headingLayout {
                Image(systemName: symbol)
                    .font(prominent ? .largeTitle : .title2)
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(Color.accentColor)
                    .frame(width: prominent ? prominentSymbolWidth : symbolWidth)
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
            .tint(.accentColor)
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
