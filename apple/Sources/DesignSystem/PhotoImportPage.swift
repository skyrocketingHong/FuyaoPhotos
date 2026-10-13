import SwiftUI

struct PhotoImportPage<Actions: View>: View {
    let title: LocalizedStringKey
    let description: LocalizedStringKey
    let symbol: String
    let busy: Bool
    @ViewBuilder let actions: () -> Actions
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.photoImportContentVisible) private var importContentVisible
    @Environment(\.photoImportTransitionCapture) private var transitionCapture
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.locale) private var locale
    @State private var cardFrame = CGRect.zero
    @State private var nativeContext = PhotoImportNativeContext()

    private struct CaptureKey: Equatable {
        let request: PhotoImportTransitionCapture.Request?
        let size: CGSize
        let frame: CGRect
        let colorScheme: ColorScheme
        let dynamicTypeSize: DynamicTypeSize
        let locale: String
        let attached: Bool
    }

    var body: some View {
        GeometryReader { geometry in
            let key = CaptureKey(request: transitionCapture?.request, size: geometry.size, frame: cardFrame,
                colorScheme: colorScheme, dynamicTypeSize: dynamicTypeSize, locale: locale.identifier,
                attached: nativeContext.attached)
            nativeForm(width: geometry.size.width) {
                cardFrame = $0
                transitionCapture?.updateContentFrame($0)
            }
                .opacity(importContentVisible ? 1 : 0)
                .disabled(!importContentVisible)
                .allowsHitTesting(importContentVisible)
                .accessibilityHidden(!importContentVisible)
                .background { PhotoImportNativeContextReader(context: nativeContext).frame(width: 0, height: 0) }
                .task(id: key) { await captureTarget(key) }
        }
    }

    private func nativeForm(width: CGFloat, space: UUID? = nil,
                            measured: @escaping (CGRect) -> Void) -> some View {
        Form {
            Section {
                importCard(width: width)
                    .onGeometryChange(for: CGRect.self) { geometry in
                        if let space { geometry.frame(in: .named(space)) }
                        else { geometry.frame(in: .global) }
                    } action: { measured($0) }
            }
        }
        .photoPageForm()
        .frame(maxWidth: 960)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    @ViewBuilder private func importCard(width: CGFloat) -> some View {
        if width >= 820 && !dynamicTypeSize.isAccessibilitySize {
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

    private func captureTarget(_ key: CaptureKey) async {
        guard let request = key.request, let transitionCapture, key.attached,
              !key.frame.isEmpty, key.size.width > 0, key.size.height > 0,
              transitionCapture.target == nil else { return }
        let measurement = PhotoImportNativeSnapshotter.Measurement()
        let space = UUID()
        let content = nativeForm(width: key.size.width, space: space) { measurement.frame = $0 }
            .coordinateSpace(name: space)
            .environment(\.dynamicTypeSize, dynamicTypeSize)
            .environment(\.locale, locale)
        let background = Form { EmptyView() }
            .photoPageForm()
            .frame(maxWidth: 960)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .environment(\.dynamicTypeSize, dynamicTypeSize)
            .environment(\.locale, locale)
        do {
            let result = try await PhotoImportNativeSnapshotter.capture(content: content, background: background,
                pointSize: key.size, measurement: measurement, colorScheme: key.colorScheme,
                context: nativeContext, deadline: request.deadline)
            try Task.checkCancellation()
            guard abs(result.contentFrame.width - key.frame.width) <= 1,
                  abs(result.contentFrame.height - key.frame.height) <= 1 else {
                transitionCapture.fail(id: request.id)
                return
            }
            let frame = result.frame.offsetBy(dx: key.frame.minX - result.contentFrame.minX,
                dy: key.frame.minY - result.contentFrame.minY)
            transitionCapture.submit(PhotoImportTransitionTarget(requestID: request.id, image: result.image,
                frame: frame, contentFrame: key.frame))
        } catch is CancellationError {
        } catch {
            transitionCapture.fail(id: request.id)
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
                .labelStyle(PhotoImportActionLabelStyle())
                .frame(maxWidth: .infinity)
                .fixedSize(horizontal: false, vertical: true)
        }
#if os(iOS)
        .keyboardShortcut(prominence == .primary ? KeyboardShortcut("o") : nil)
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
