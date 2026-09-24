import SwiftUI

struct PhotoInformationRow: View {
    let title: LocalizedStringKey
    let value: String
    var monospacedDigits = false

    var body: some View {
        LabeledContent {
            Text(value)
                .font(monospacedDigits ? .subheadline.monospacedDigit() : .subheadline)
                .multilineTextAlignment(.trailing)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
        } label: {
            Text(title).foregroundStyle(.secondary)
        }
        .font(.subheadline)
        .listRowInsets(EdgeInsets(top: 7, leading: 16, bottom: 7, trailing: 16))
    }
}
