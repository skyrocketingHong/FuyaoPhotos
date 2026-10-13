import SwiftUI

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
    @FocusState.Binding var focusedField: CardField?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
#if os(macOS)
        RegularCardInspector(document: document, selection: selection, textEditingActive: $textEditingActive,
                             focusedField: $focusedField)
#else
        if expanded {
            RegularCardInspector(document: document, selection: selection, textEditingActive: $textEditingActive,
                                 focusedField: $focusedField)
        } else { panel }
#endif
    }

    private var panel: some View {
        GeometryReader { geometry in
            let contentWidth = max(0, geometry.size.width - 40)
            let selectorWidth = min(180, contentWidth * 0.32)
            let detailWidth = max(0, contentWidth - selectorWidth - 12)
            let usesMenu = geometry.size.height < 320 || dynamicTypeSize.isAccessibilitySize
            let condensed = geometry.size.height < 160
            let stacked = usesMenu && !condensed
            let editorLayout = stacked
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
                : AnyLayout(HStackLayout(alignment: .top, spacing: 12))
            VStack(spacing: 12) {
                if !condensed {
                    Picker("card.edit.mode", selection: modeSelection) {
                        Label("card.information", systemImage: "info.circle").tag(CardInspectorSelection.Mode.information)
                        Label("card.style", systemImage: "slider.horizontal.3").tag(CardInspectorSelection.Mode.style)
                    }
                    .pickerStyle(.segmented)
                }
                editorLayout {
                    ZStack {
                        if usesMenu {
                            CardInspectorSelectionMenu(document: document, selection: selection,
                                                       includesMode: condensed, prepare: finishEditing)
                        } else if selection.mode == .information {
                            EditorItemPicker(items: CardField.allCases, selection: fieldSelection) {
                                $0 == .focalLength ? "card.field.focalLength.short" : LocalizedStringKey($0.titleKey)
                            }
                            .transition(.opacity)
                        } else {
                            EditorItemPicker(items: CardAdjustment.allCases, selection: adjustmentSelection) { $0.title }
                                .transition(.opacity)
                        }
                    }
                    .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: selection.mode)
                    .frame(width: stacked ? nil : selectorWidth)
                    .frame(maxWidth: stacked ? .infinity : nil, alignment: .leading)

                    MobileCardInspector(document: document, field: selection.field, adjustment: selection.adjustment,
                                        information: selection.mode == .information, selectionID: detailID,
                                        compact: usesMenu, textEditingActive: $textEditingActive,
                                        focusedField: $focusedField)
                        .frame(width: stacked ? contentWidth : detailWidth)
                        .frame(maxHeight: .infinity)
                }
                .frame(width: contentWidth)
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, condensed ? 0 : 8)

        }
        .onChange(of: selection.mode) { _, _ in finishEditing() }
#if os(iOS)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                if focusedField != nil {
                    Spacer()
                    Button("done", action: finishEditing)
                }
            }
        }
#endif
    }

    private var modeSelection: Binding<CardInspectorSelection.Mode> {
        Binding(get: { selection.mode }, set: { value in
            guard value != selection.mode else { return }
            finishEditing()
            selection.mode = value
        })
    }

    private var fieldSelection: Binding<CardField> {
        Binding(get: { selection.field }, set: { value in
            guard selection.mode == .information, value != selection.field else { return }
            finishEditing()
            selection.field = value
        })
    }

    private var adjustmentSelection: Binding<CardAdjustment> {
        Binding(get: { selection.adjustment }, set: { value in
            guard selection.mode == .style, value != selection.adjustment else { return }
            finishEditing()
            selection.adjustment = value
        })
    }

    private func finishEditing() {
        focusedField = nil
        textEditingActive = false
    }

    private var detailID: String {
        selection.mode == .information ? "field-\(selection.field.rawValue)" : "style-\(selection.adjustment.rawValue)"
    }
}

private struct CardInspectorSelectionMenu: View {
    @Bindable var document: CardDocument
    @Bindable var selection: CardInspectorSelection
    var includesMode = false
    let prepare: () -> Void

    var body: some View {
        Menu {
            if includesMode {
                Picker("card.edit.mode", selection: Binding(get: { selection.mode }, set: {
                    prepare()
                    selection.mode = $0
                })) {
                    Text("card.information").tag(CardInspectorSelection.Mode.information)
                    Text("card.style").tag(CardInspectorSelection.Mode.style)
                }
            }
            if selection.mode == .information {
                Picker("card.information", selection: Binding(get: { selection.field }, set: {
                    prepare()
                    selection.field = $0
                })) {
                    ForEach(CardField.allCases) { Text(LocalizedStringKey($0.titleKey)).tag($0) }
                }
                if selection.field == .imageSize {
                    CardImageSizeMenu(document: document, prepare: prepare).actions
                }
            } else {
                Picker("card.style", selection: Binding(get: { selection.adjustment }, set: {
                    prepare()
                    selection.adjustment = $0
                })) {
                    ForEach(CardAdjustment.allCases) { Text($0.title).tag($0) }
                }
            }
            Section {
                Button("card.restore.current", systemImage: "arrow.counterclockwise") {
                    prepare()
                    if selection.mode == .information {
                        document.restoreField(selection.field, preferLensPixelCount: CardPreferences.shared.preferLensPixelCount)
                    } else {
                        document.card.style[keyPath: selection.adjustment.keyPath] = PhotoCardStyle()[keyPath: selection.adjustment.keyPath]
                    }
                }
                Button("card.restore.all", systemImage: "arrow.counterclockwise.circle") {
                    prepare()
                    if selection.mode == .information {
                        document.restoreInformation(preferLensPixelCount: CardPreferences.shared.preferLensPixelCount)
                    } else { document.card.style = PhotoCardStyle() }
                }
            }
        } label: {
            Label(selection.mode == .information ? LocalizedStringKey(selection.field.titleKey) : selection.adjustment.title,
                  systemImage: "chevron.up.chevron.down")
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(.bordered)
        .frame(minHeight: 44)
    }
}

private struct MobileCardInspector: View {
    @Bindable var document: CardDocument
    let field: CardField
    let adjustment: CardAdjustment
    let information: Bool
    let selectionID: String
    let compact: Bool
    @Binding var textEditingActive: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState.Binding var focusedField: CardField?
    @ScaledMetric(relativeTo: .body) private var preferredControlHeight: CGFloat = 72

    var body: some View {
        GeometryReader { geometry in
            let height = geometry.size.height
            let buttonHeight: CGFloat = compact ? 0 : 44
            let controlHeight = min(preferredControlHeight, max(44, height - (compact ? 0 : 24)))
            let descriptionHeight: CGFloat = compact ? 0 : 44
            let availablePreviewHeight = height - controlHeight - descriptionHeight - buttonHeight - (compact ? 8 : 24)
            let previewHeight = availablePreviewHeight >= 44
                ? min(geometry.size.width / CardDetailPreview.referenceAspect, availablePreviewHeight) : 0
            inspectorContents(controlHeight: controlHeight, descriptionHeight: descriptionHeight,
                              buttonHeight: buttonHeight, previewHeight: previewHeight, compact: compact)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .onChange(of: document.card[field]) { oldValue, newValue in
            if focusedField == field && oldValue != newValue { textEditingActive = true }
        }
    }

    private func inspectorContents(controlHeight: CGFloat, descriptionHeight: CGFloat,
                                   buttonHeight: CGFloat, previewHeight: CGFloat, compact: Bool) -> some View {
        VStack(alignment: .leading, spacing: compact ? 0 : 8) {
            if previewHeight > 0 {
                CardDetailPreview(document: document, processing: textEditingActive,
                                  highlightedField: information ? field : nil,
                                  highlightedStyle: information ? nil : adjustment)
                    .frame(maxWidth: .infinity)
                    .frame(height: previewHeight)
                    .padding(.bottom, compact ? 8 : 0)
            }

            ZStack(alignment: .leading) {
                if information {
                    TextField("", text: $document.card[field], axis: .vertical)
                        .textFieldStyle(.plain)
                        .lineLimit(1...2)
                        .foregroundStyle(.primary)
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .frame(height: controlHeight, alignment: .leading)
                        .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 12))
                        .focused($focusedField, equals: field)
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
                            : document.card.style[keyPath: adjustment.keyPath].formatted(.number.precision(.fractionLength(0))),
                        showsLabels: controlHeight >= preferredControlHeight)
                        .id(selectionID)
                        .transition(.opacity)
                }
            }
            .frame(height: controlHeight)
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: selectionID)

            Group {
                if information && field == .imageSize {
                    CardImageSizeMenu(document: document) { focusedField = nil; textEditingActive = false }
                } else {
                    Text(LocalizedStringKey(information
                         ? "card.field." + field.rawValue + ".hint"
                         : "card.style." + adjustment.rawValue + ".hint"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(height: descriptionHeight, alignment: .topLeading)
                .clipped()
                .disabled(compact)
                .allowsHitTesting(!compact)
                .accessibilityHidden(compact)
                .id(selectionID)
                .transition(.opacity)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: selectionID)

            HStack(spacing: 6) {
                restoreButton("card.restore.current",
                              height: buttonHeight, action: restoreCurrent)
                restoreButton("card.restore.all",
                              height: buttonHeight, action: restoreAll)
            }
            .frame(height: buttonHeight)
            .clipped()
            .disabled(compact)
            .allowsHitTesting(!compact)
            .accessibilityHidden(compact)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }

    private func restoreButton(_ title: LocalizedStringKey, height: CGFloat,
                               action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.callout)
                .lineLimit(2)
                .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
        .tint(.accentColor)
        .frame(maxWidth: .infinity)
    }

    private func restoreCurrent() {
        focusedField = nil
        textEditingActive = false
        if information {
            document.restoreField(field, preferLensPixelCount: CardPreferences.shared.preferLensPixelCount)
        } else {
            document.card.style[keyPath: adjustment.keyPath] = PhotoCardStyle()[keyPath: adjustment.keyPath]
        }
    }

    private func restoreAll() {
        focusedField = nil
        textEditingActive = false
        if information {
            document.restoreInformation(preferLensPixelCount: CardPreferences.shared.preferLensPixelCount)
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
