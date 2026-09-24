import SwiftUI

private enum CardAdjustment: String, CaseIterable, Identifiable {
    case scale, textScale, opacity, blur, rightInset, bottomInset, cornerRadius
    var id: Self { self }
    var percentage: Bool { self == .scale || self == .textScale || self == .opacity }
    var title: LocalizedStringKey { self == .cornerRadius ? "card.radius" : LocalizedStringKey("card." + rawValue) }
    var range: ClosedRange<Double> {
        switch self {
        case .scale: 0.6...2
        case .textScale: 0.8...1.8
        case .opacity: 0...1
        case .blur: 0...50
        case .rightInset, .bottomInset: 0...250
        case .cornerRadius: 0...40
        }
    }
    var keyPath: WritableKeyPath<PhotoCardStyle, Double> {
        switch self {
        case .scale: \.scale
        case .textScale: \.textScale
        case .opacity: \.opacity
        case .blur: \.blur
        case .rightInset: \.rightInset
        case .bottomInset: \.bottomInset
        case .cornerRadius: \.cornerRadius
        }
    }
    var symbols: (String, String) {
        switch self {
        case .scale: ("rectangle", "rectangle.fill")
        case .textScale: ("textformat.size.smaller", "textformat.size.larger")
        case .opacity: ("rectangle.on.rectangle", "rectangle.fill.on.rectangle.fill")
        case .blur: ("drop", "drop.fill")
        case .rightInset: ("arrow.right.to.line", "arrow.left")
        case .bottomInset: ("arrow.down.to.line", "arrow.up")
        case .cornerRadius: ("square", "capsule")
        }
    }
}

struct CardAdjustmentPanel: View {
    @Bindable var document: CardDocument
    @State private var field: CardField = .author
    @State private var adjustment: CardAdjustment = .scale
    @State private var mode: EditingMode = .information
    @FocusState private var focusedField: CardField?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    private enum EditingMode: Hashable { case information, style }

    var body: some View {
#if os(macOS)
        macPanel
#else
        mobilePanel
#endif
    }

#if os(macOS)
    private var macPanel: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                Picker("card.edit.mode", selection: $mode) {
                    Label("card.information", systemImage: "info.circle").tag(EditingMode.information)
                    Label("card.style", systemImage: "slider.horizontal.3").tag(EditingMode.style)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 12)

                Group {
                    if mode == .information {
                        EditorItemPicker(items: CardField.allCases, selection: $field) {
                            $0 == .focalLength ? "card.field.focalLength.short" : LocalizedStringKey($0.titleKey)
                        }
                    } else {
                        EditorItemPicker(items: CardAdjustment.allCases, selection: $adjustment) { $0.title }
                    }
                }
                .id(mode)
                .frame(height: max(120, min(260, geometry.size.height * 0.38)))

                ScrollView {
                    macEditor
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(20)
                }
                .frame(maxHeight: .infinity)
            }
        }
        .background(Color(nsColor: .controlBackgroundColor))
        .onChange(of: field) { _, _ in focusedField = nil }
        .onChange(of: mode) { _, _ in focusedField = nil }
    }

    @ViewBuilder private var macEditor: some View {
        if mode == .information {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .firstTextBaseline) {
                    Text(LocalizedStringKey(field.titleKey))
                        .font(.title3.weight(.semibold)).foregroundStyle(.yellow)
                    Spacer(minLength: 8)
                    CircularIconButton("card.restore.selected", systemImage: "arrow.counterclockwise") {
                        document.card[field] = document.defaultCard[field]
                    }
                    CircularIconButton("card.restore.all", systemImage: "arrow.counterclockwise.circle") {
                        var card = document.card
                        for item in CardField.allCases { card[item] = document.defaultCard[item] }
                        document.card = card
                    }
                }
                Text(LocalizedStringKey("card.field." + field.rawValue + ".hint"))
                    .font(.subheadline).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                TextField(LocalizedStringKey(field.titleKey), text: $document.card[field], axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .lineLimit(2...5)
                    .foregroundStyle(.yellow)
                    .focused($focusedField, equals: field)
            }
        } else {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .firstTextBaseline) {
                    Text(adjustment.title)
                        .font(.title3.weight(.semibold)).foregroundStyle(.yellow)
                    Spacer(minLength: 8)
                    CircularIconButton("card.restore.selected", systemImage: "arrow.counterclockwise") {
                        document.card.style[keyPath: adjustment.keyPath] = PhotoCardStyle()[keyPath: adjustment.keyPath]
                    }
                    CircularIconButton("card.restore.all", systemImage: "arrow.counterclockwise.circle") {
                        document.card.style = PhotoCardStyle()
                    }
                }
                Text(LocalizedStringKey("card.style." + adjustment.rawValue + ".hint"))
                    .font(.subheadline).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                CardStyleSlider(
                    value: $document.card.style[dynamicMember: adjustment.keyPath],
                    in: adjustment.range,
                    defaultValue: PhotoCardStyle()[keyPath: adjustment.keyPath],
                    label: adjustment.title,
                    minimumSymbol: adjustment.symbols.0,
                    maximumSymbol: adjustment.symbols.1,
                    formattedValue: adjustment.percentage
                        ? document.card.style[keyPath: adjustment.keyPath].formatted(.percent.precision(.fractionLength(0)))
                        : document.card.style[keyPath: adjustment.keyPath].formatted(.number.precision(.fractionLength(0))))
            }
        }
    }
#endif

#if !os(macOS)
    private var mobilePanel: some View {
        VStack(spacing: 12) {
            Picker("card.edit.mode", selection: $mode) {
                Label("card.information", systemImage: "info.circle").tag(EditingMode.information)
                Label("card.style", systemImage: "slider.horizontal.3").tag(EditingMode.style)
            }
            .pickerStyle(.segmented)
            GeometryReader { geometry in
                let contentWidth = min(640, geometry.size.width)
                let selectorWidth = min(200, contentWidth * (dynamicTypeSize.isAccessibilitySize ? 0.32 : 0.4))
                let detailWidth = max(0, contentWidth - selectorWidth - 16)
                HStack(alignment: .top, spacing: 16) {
                    Group {
                        if mode == .information {
                            EditorItemPicker(items: CardField.allCases, selection: $field) {
                                $0 == .focalLength ? "card.field.focalLength.short" : LocalizedStringKey($0.titleKey)
                            }
                        } else {
                            EditorItemPicker(items: CardAdjustment.allCases, selection: $adjustment) { $0.title }
                        }
                    }
                    .id(mode)
                    .frame(width: selectorWidth)

                    MobileCardInspector(document: document, field: field, adjustment: adjustment,
                                        information: mode == .information, selectionID: detailID)
                        .frame(width: detailWidth, height: geometry.size.height)
                }
                .frame(width: contentWidth)
                .frame(maxWidth: .infinity)
            }
            .frame(maxHeight: .infinity)
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }

    private var detailID: String {
        mode == .information ? "field-\(field.rawValue)" : "style-\(adjustment.rawValue)"
    }
#endif
}

#if !os(macOS)
private struct MobileCardInspector: View {
    @Bindable var document: CardDocument
    let field: CardField
    let adjustment: CardAdjustment
    let information: Bool
    let selectionID: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @FocusState private var editingText: Bool

    var body: some View {
        GeometryReader { geometry in
            let height = geometry.size.height
            let buttonHeight: CGFloat = dynamicTypeSize.isAccessibilitySize ? 64 : 48
            let controlHeight = min(dynamicTypeSize.isAccessibilitySize ? 100 : 80, max(68, height * 0.22))
            let descriptionHeight = min(dynamicTypeSize.isAccessibilitySize ? 130 : 88,
                                        max(52, height * (dynamicTypeSize.isAccessibilitySize ? 0.3 : 0.22)))
            let showsDescription = height >= (dynamicTypeSize.isAccessibilitySize ? 270 : 210)
            let showsDetail = height >= (dynamicTypeSize.isAccessibilitySize ? 420 : 280)
            let previewHeight = min(176, max(64, height - controlHeight - descriptionHeight - buttonHeight - 24))

            VStack(alignment: .leading, spacing: 8) {
                if showsDetail { CardDetailPreview(document: document, height: previewHeight) }

                ZStack(alignment: .leading) {
                    if information {
                        TextField("", text: $document.card[field], axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                            .lineLimit(2...3)
                            .foregroundStyle(.yellow)
                            .tint(.yellow)
                            .focused($editingText)
                            .accessibilityLabel(Text(LocalizedStringKey(field.titleKey)))
                            .id(selectionID)
                            .transition(.opacity)
                    } else {
                        CardStyleSlider(
                            value: $document.card.style[dynamicMember: adjustment.keyPath],
                            in: adjustment.range,
                            defaultValue: PhotoCardStyle()[keyPath: adjustment.keyPath],
                            label: adjustment.title,
                            minimumSymbol: adjustment.symbols.0,
                            maximumSymbol: adjustment.symbols.1,
                            formattedValue: adjustment.percentage
                                ? document.card.style[keyPath: adjustment.keyPath].formatted(.percent.precision(.fractionLength(0)))
                                : document.card.style[keyPath: adjustment.keyPath].formatted(.number.precision(.fractionLength(0))))
                            .id(selectionID)
                            .transition(.opacity)
                    }
                }
                .frame(height: controlHeight)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: selectionID)

                Spacer(minLength: 0)

                if showsDescription {
                    ZStack(alignment: .topLeading) {
                        Text(LocalizedStringKey(information
                             ? "card.field." + field.rawValue + ".hint"
                             : "card.style." + adjustment.rawValue + ".hint"))
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .lineLimit(4)
                            .id(selectionID)
                            .transition(.opacity)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: descriptionHeight, alignment: .topLeading)
                    .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: selectionID)
                }

                HStack(spacing: 6) {
                    restoreButton("card.restore.current", symbol: "arrow.counterclockwise",
                                  height: buttonHeight, action: restoreCurrent)
                    restoreButton("card.restore.all", symbol: "arrow.counterclockwise.circle",
                                  height: buttonHeight, action: restoreAll)
                }
                .frame(height: buttonHeight)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .onChange(of: selectionID) { _, _ in editingText = false }
    }

    private func restoreButton(_ title: LocalizedStringKey, symbol: String, height: CGFloat,
                               action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: symbol)
                    .font(.caption)
                    .accessibilityHidden(true)
                Text(title)
                    .font(.caption2.weight(.medium))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
        .frame(maxWidth: .infinity)
    }

    private func restoreCurrent() {
        if information {
            document.card[field] = document.defaultCard[field]
        } else {
            document.card.style[keyPath: adjustment.keyPath] = PhotoCardStyle()[keyPath: adjustment.keyPath]
        }
    }

    private func restoreAll() {
        if information {
            var card = document.card
            for item in CardField.allCases { card[item] = document.defaultCard[item] }
            document.card = card
        } else {
            document.card.style = PhotoCardStyle()
        }
    }
}
#endif

private struct EditorItemPicker<Item: Hashable & Identifiable>: View {
    let items: [Item]
    @Binding var selection: Item
    let title: (Item) -> LocalizedStringKey
    var body: some View {
#if os(macOS)
        List(items,selection:Binding<Item?>(get:{ selection },set:{ if let value=$0 { selection=value } })) { item in
            Text(title(item)).tag(item)
        }
        .listStyle(.plain).scrollContentBackground(.hidden)
#else
        CameraItemSelector(items:items,selection:$selection,title:title)
#endif
    }
}
