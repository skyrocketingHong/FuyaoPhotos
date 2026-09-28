import SwiftUI

struct PhotoInformationRow: View {
    let title: LocalizedStringKey
    let value: String
    var monospacedDigits = false

    var body: some View {
        LabeledContent {
            Text(value)
                .font(monospacedDigits ? .body.monospacedDigit() : .body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.trailing)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
        } label: {
            Text(title).foregroundStyle(.primary)
        }
        .listRowInsets(EdgeInsets(top: 12, leading: 20, bottom: 12, trailing: 20))
    }
}
