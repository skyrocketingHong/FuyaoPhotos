import SwiftUI

struct CircularIconToggle: View {
    let title: LocalizedStringKey
    let systemImage: String?
    let imageAsset: String?
    @Binding var isOn: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(_ title: LocalizedStringKey, systemImage: String, isOn: Binding<Bool>) {
        self.title = title; self.systemImage = systemImage; imageAsset = nil; _isOn = isOn
    }

    init(_ title: LocalizedStringKey, imageAsset: String, isOn: Binding<Bool>) {
        self.title = title; self.imageAsset = imageAsset; systemImage = nil; _isOn = isOn
    }

    var body: some View {
        Toggle(isOn: $isOn) {
            Label {
                Text(title)
            } icon: {
                if let imageAsset { PhotoPreviewActionIcon(image: Image(imageAsset)) }
                else if let systemImage {
                    PhotoPreviewActionIcon(image: Image(systemName: systemImage))
                        .contentTransition(reduceMotion ? .identity : .symbolEffect(.replace))
                        .animation(reduceMotion ? nil : .smooth(duration: 0.2), value: systemImage)
                }
            }
            .labelStyle(.iconOnly)
        }
        .toggleStyle(.button)
        .photoIconControlStyle()
        .accessibilityLabel(Text(title))
        .help(Text(title))
    }
}
