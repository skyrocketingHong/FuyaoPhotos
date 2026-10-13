import SwiftUI

struct PhotoInformationRow: View {
    let title: LocalizedStringKey
    let value: String
    var monospacedDigits = false
    var monospaced = false

    var body: some View {
        LabeledContent {
            Text(value)
                .font(monospaced ? .body.monospaced() : monospacedDigits ? .body.monospacedDigit() : .body)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
        } label: {
            Text(title).foregroundStyle(.primary)
        }
        .labeledContentStyle(AdaptivePhotoInformationRowStyle())
        .listRowInsets(EdgeInsets(top: 12, leading: 20, bottom: 12, trailing: 20))
    }
}

private struct AdaptivePhotoInformationRowStyle: LabeledContentStyle {
    func makeBody(configuration: Configuration) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                configuration.label.fixedSize()
                Spacer(minLength: 16)
                configuration.content
                    .multilineTextAlignment(.trailing)
                    .fixedSize()
            }
            VStack(alignment: .leading, spacing: 6) {
                configuration.label
                    .fixedSize(horizontal: false, vertical: true)
                configuration.content
                    .multilineTextAlignment(.leading)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
