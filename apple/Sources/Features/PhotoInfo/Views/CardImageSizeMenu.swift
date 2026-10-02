import SwiftUI

struct CardImageSizeMenu: View {
    @Bindable var document: CardDocument
    var prepare: () -> Void = {}

    var body: some View {
        Menu("card.imageSize.menu", systemImage: "square.dashed") { actions }
            .buttonStyle(.bordered)
            .frame(minHeight: 44)
            .accessibilityHint(Text("card.imageSize.hint"))
    }

    @ViewBuilder var actions: some View {
        Button(String(format: String.localized("card.imageSize.file"), document.metadata.card[.imageSize])) {
            prepare()
            document.useFileImageSize()
        }
        Button(document.metadata.lensImageSize.map { String(format: String.localized("card.imageSize.lens"), $0) }
               ?? String.localized("card.imageSize.unavailable")) {
            prepare()
            document.useLensImageSize()
        }
        .disabled(document.metadata.lensImageSize == nil)
    }
}
