import SwiftUI

extension EnvironmentValues {
    @Entry var photoImportContentVisible = true
}

struct PhotoImportForm<Actions: View>: View {
    let title: LocalizedStringKey
    let description: LocalizedStringKey
    let symbol: String
    let busy: Bool
    let availableWidth: CGFloat
    var measurementSpace: UUID?
    var measured: (CGRect) -> Void = { _ in }
    @ViewBuilder let actions: () -> Actions
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Form {
            Section {
                importCard
                    .onGeometryChange(for: CGRect.self) { geometry in
                        if let measurementSpace { geometry.frame(in: .named(measurementSpace)) }
                        else { geometry.frame(in: .global) }
                    } action: { measured($0) }
            }
        }
        .modifier(PhotoImportFormLayout())
    }

    @ViewBuilder private var importCard: some View {
        if availableWidth >= 820 && !dynamicTypeSize.isAccessibilitySize {
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

    private var introduction: some View {
        PhotoPageIntro(title: title, description: description, symbol: symbol, prominent: true)
    }

    private var importActions: some View {
        VStack(alignment: .leading, spacing: 12) {
            actions()
            if busy { ProgressView("photo.import.loading").transition(.opacity) }
        }
        .controlSize(.regular)
        .disabled(busy)
        .padding(.vertical, 8)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: busy)
    }
}

struct PhotoImportFormBackground: View {
    var body: some View { Form { EmptyView() }.modifier(PhotoImportFormLayout()) }
}

private struct PhotoImportFormLayout: ViewModifier {
    func body(content: Content) -> some View {
        content.photoPageForm()
            .frame(maxWidth: 960)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}

struct PhotoImportAction: View {
    enum Prominence { case primary, secondary, tertiary }
    let title: LocalizedStringKey
    let symbol: String
    var prominence = Prominence.secondary
    let action: () -> Void
    @Environment(\.photoImportContentVisible) private var importContentVisible

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
        Button {
            guard importContentVisible else { return }
            action()
        } label: {
            Label(title, systemImage: symbol)
                .labelStyle(PhotoImportActionLabelStyle())
                .frame(maxWidth: .infinity)
                .fixedSize(horizontal: false, vertical: true)
        }
        .focusable(importContentVisible, interactions: .activate)
#if os(iOS)
        .keyboardShortcut(importContentVisible && prominence == .primary ? KeyboardShortcut("o") : nil)
#endif
    }
}

private struct PhotoImportActionLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        // Form's automatic label tint must not override the native button foreground.
        HStack(spacing: 8) {
            configuration.icon
                .symbolRenderingMode(.monochrome)
                .accessibilityHidden(true)
            configuration.title
        }
    }
}
