import SwiftUI
#if os(iOS)
import UIKit
#endif

enum CardAdjustment: String, CaseIterable, Identifiable {
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

@MainActor @Observable final class CardInspectorSelection {
    enum Mode: Hashable { case information, style }
    var field: CardField = .author
    var adjustment: CardAdjustment = .scale
    var mode: Mode = .information
}

struct CardAdjustmentPanel: View {
    @Bindable var document: CardDocument
    @Bindable var selection: CardInspectorSelection
    @Binding var textEditingActive: Bool
    var expanded = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
#if os(macOS)
        RegularCardInspector(document: document, selection: selection, textEditingActive: $textEditingActive)
#else
        if expanded {
            RegularCardInspector(document: document, selection: selection, textEditingActive: $textEditingActive)
        } else { panel }
#endif
    }

    private var panel: some View {
        GeometryReader { geometry in
            let contentWidth = max(0, geometry.size.width - 40)
            let selectorWidth = min(180, contentWidth * 0.32)
            let detailWidth = max(0, contentWidth - selectorWidth - 12)
            VStack(spacing: 12) {
                Picker("card.edit.mode", selection: $selection.mode) {
                    Label("card.information", systemImage: "info.circle").tag(CardInspectorSelection.Mode.information)
                    Label("card.style", systemImage: "slider.horizontal.3").tag(CardInspectorSelection.Mode.style)
                }
                .pickerStyle(.segmented)
                HStack(alignment: .top, spacing: 12) {
                    ZStack {
                        if geometry.size.height < 300 || dynamicTypeSize.isAccessibilitySize {
                            Menu {
                                if selection.mode == .information {
                                    Picker("card.information", selection: $selection.field) {
                                        ForEach(CardField.allCases) { Text(LocalizedStringKey($0.titleKey)).tag($0) }
                                    }
                                } else {
                                    Picker("card.style", selection: $selection.adjustment) {
                                        ForEach(CardAdjustment.allCases) { Text($0.title).tag($0) }
                                    }
                                }
                                Button("card.restore.current", systemImage: "arrow.counterclockwise") {
                                    if selection.mode == .information { document.card[selection.field] = document.defaultCard[selection.field] }
                                    else { document.card.style[keyPath: selection.adjustment.keyPath] = PhotoCardStyle()[keyPath: selection.adjustment.keyPath] }
                                }
                                Button("card.restore.all", systemImage: "arrow.counterclockwise.circle") {
                                    if selection.mode == .information {
                                        for item in CardField.allCases { document.card[item] = document.defaultCard[item] }
                                    } else { document.card.style = PhotoCardStyle() }
                                }
                            } label: {
                                Label(selection.mode == .information ? LocalizedStringKey(selection.field.titleKey) : selection.adjustment.title,
                                      systemImage: "chevron.up.chevron.down")
                            }
                            .frame(minHeight: 44)
                        } else if selection.mode == .information {
                            EditorItemPicker(items: CardField.allCases, selection: $selection.field) {
                                $0 == .focalLength ? "card.field.focalLength.short" : LocalizedStringKey($0.titleKey)
                            }
                            .transition(.opacity)
                        } else {
                            EditorItemPicker(items: CardAdjustment.allCases, selection: $selection.adjustment) { $0.title }
                                .transition(.opacity)
                        }
                    }
                    .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: selection.mode)
                    .frame(width: selectorWidth)

                    MobileCardInspector(document: document, field: selection.field, adjustment: selection.adjustment,
                                        information: selection.mode == .information, selectionID: detailID,
                                        textEditingActive: $textEditingActive)
                        .frame(width: detailWidth)
                        .frame(maxHeight: .infinity)
                }
                .frame(width: contentWidth)
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)

        }
        .onChange(of: selection.mode) { _, _ in textEditingActive = false }
    }

    private var detailID: String {
        selection.mode == .information ? "field-\(selection.field.rawValue)" : "style-\(selection.adjustment.rawValue)"
    }
}

private struct MobileCardInspector: View {
    @Bindable var document: CardDocument
    let field: CardField
    let adjustment: CardAdjustment
    let information: Bool
    let selectionID: String
    @Binding var textEditingActive: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @FocusState private var editingText: Bool

    var body: some View {
        GeometryReader { geometry in
            let height = geometry.size.height
            let compact = height < 240 || dynamicTypeSize.isAccessibilitySize
            let buttonHeight: CGFloat = compact ? 0 : 44
            let controlHeight: CGFloat = min(dynamicTypeSize.isAccessibilitySize ? 88 : 64, max(44, height - 24))
            let descriptionHeight: CGFloat = compact ? 0 : 34
            let availablePreviewHeight = height - controlHeight - descriptionHeight - buttonHeight - 24
            let previewHeight = availablePreviewHeight >= 44
                ? min(geometry.size.width / CardDetailPreview.referenceAspect, availablePreviewHeight) : 0
            inspectorContents(controlHeight: controlHeight, descriptionHeight: descriptionHeight,
                              buttonHeight: buttonHeight, previewHeight: previewHeight, compact: compact)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .onChange(of: selectionID) { _, _ in editingText = false; textEditingActive = false }
        .onChange(of: document.id) { _, _ in editingText = false; textEditingActive = false }
        .onChange(of: editingText) { _, active in if !active { textEditingActive = false } }
        .onChange(of: document.card[field]) { oldValue, newValue in
            if editingText && oldValue != newValue { textEditingActive = true }
        }
#if os(iOS)
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidHideNotification)) { _ in
            editingText = false
            textEditingActive = false
        }
#endif
    }

    private func inspectorContents(controlHeight: CGFloat, descriptionHeight: CGFloat,
                                   buttonHeight: CGFloat, previewHeight: CGFloat, compact: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if previewHeight > 0 {
                CardDetailPreview(document: document, processing: textEditingActive,
                                  highlightedField: information ? field : nil,
                                  highlightedStyle: information ? nil : adjustment)
                    .frame(maxWidth: .infinity)
                    .frame(height: previewHeight)
            }

            ZStack(alignment: .leading) {
                if information {
                    TextField("", text: $document.card[field], axis: .vertical)
                        .textFieldStyle(.plain)
                        .lineLimit(1...2)
                        .foregroundStyle(.primary)
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .frame(height: controlHeight, alignment: .top)
                        .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 12))
                        .focused($editingText)
                        .accessibilityLabel(Text(LocalizedStringKey(field.titleKey)))
                        .id(selectionID)
                        .transition(.opacity)
                } else {
                    CardStyleSlider(
                        value: $document.card.style[dynamicMember: adjustment.keyPath],
                        range: adjustment.range,
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

            Text(LocalizedStringKey(information
                 ? "card.field." + field.rawValue + ".hint"
                 : "card.style." + adjustment.rawValue + ".hint"))
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(height: descriptionHeight, alignment: .topLeading)
                .clipped()
                .id(selectionID)
                .transition(.opacity)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: selectionID)

            HStack(spacing: 6) {
                restoreButton("card.restore.current", symbol: "arrow.counterclockwise",
                              height: buttonHeight, action: restoreCurrent)
                restoreButton("card.restore.all", symbol: "arrow.counterclockwise.circle",
                              height: buttonHeight, action: restoreAll)
            }
            .frame(height: buttonHeight)
            .clipped()
            .accessibilityHidden(compact)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
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

private struct EditorItemPicker<Item: Hashable & Identifiable>: View {
    let items: [Item]
    @Binding var selection: Item
    let title: (Item) -> LocalizedStringKey
    var body: some View {
        CameraItemSelector(items:items,selection:$selection,title:title)
    }
}
