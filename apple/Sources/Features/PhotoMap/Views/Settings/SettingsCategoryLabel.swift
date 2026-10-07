import SwiftUI

struct SettingsCategoryLabel: View {
    @Environment(\.backgroundProminence) private var backgroundProminence
    let title: LocalizedStringKey
    let symbol: String

    var body: some View {
        Label {
            Text(title).foregroundStyle(.primary)
        } icon: {
            Image(systemName: symbol)
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(backgroundProminence == .increased
                    ? AnyShapeStyle(.primary)
                    : AnyShapeStyle(.tint))
        }
    }
}
