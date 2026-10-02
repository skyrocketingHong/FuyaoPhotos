import SwiftUI

struct PhotoImportPage<Actions: View>: View {
    let title: LocalizedStringKey
    let description: LocalizedStringKey
    let symbol: String
    let busy: Bool
    @ViewBuilder let actions: () -> Actions
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geometry in
            Form {
                Section {
                    if geometry.size.width >= 820 && !dynamicTypeSize.isAccessibilitySize {
                        HStack(alignment: .top, spacing: 32) {
                            introduction
                            importActions.frame(width: 320)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 24) {
                            introduction
                            importActions
                        }
                    }
                }
            }
            .photoPageForm()
            .frame(maxWidth: 960)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }

    private var introduction: some View {
        PhotoPageIntro(title: title, description: description, symbol: symbol, prominent: true)
    }

    private var importActions: some View {
        VStack(alignment: .leading, spacing: 12) {
            actions()
            if busy { ProgressView("photo.import.loading").transition(.opacity) }
        }
        .controlSize(.large)
        .disabled(busy)
        .padding(.vertical, 8)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: busy)
    }
}

struct PhotoImportAction: View {
    enum Prominence { case primary, secondary, tertiary }
    let title: LocalizedStringKey
    let symbol: String
    var prominence = Prominence.secondary
    let action: () -> Void

    var body: some View {
        Group {
            switch prominence {
            case .primary: button.buttonStyle(.borderedProminent)
            case .secondary: button.buttonStyle(.bordered)
            case .tertiary: button.buttonStyle(.borderless)
            }
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
        .keyboardShortcut(prominence == .primary ? KeyboardShortcut("o") : nil)
#endif
    }
}
