import SwiftUI

struct PhotoImportPage<Actions: View>: View {
    let title: LocalizedStringKey
    let description: LocalizedStringKey
    let symbol: String
    let busy: Bool
    @ViewBuilder let actions: () -> Actions

    var body: some View {
        Form {
            Section {
                PhotoPageIntro(title: title, description: description, symbol: symbol)
                VStack(spacing: 12) { actions() }
                    .controlSize(.large)
                    .disabled(busy)
                    .padding(.vertical, 8)
                if busy { ProgressView("photo.import.loading") }
            }
        }
        .photoPageForm()
        .frame(maxWidth: 720)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}

struct PhotoImportAction: View {
    let title: LocalizedStringKey
    let symbol: String
    var primary = false
    let action: () -> Void

    var body: some View {
        Group {
            if primary { button.buttonStyle(.borderedProminent) }
            else { button.buttonStyle(.bordered) }
        }
        .tint(.accentColor)
    }

    private var button: some View {
        Button(action: action) {
            Label(title, systemImage: symbol)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
                .fixedSize(horizontal: false, vertical: true)
        }
#if os(iOS)
        .keyboardShortcut(primary ? KeyboardShortcut("o") : nil)
#endif
    }
}
