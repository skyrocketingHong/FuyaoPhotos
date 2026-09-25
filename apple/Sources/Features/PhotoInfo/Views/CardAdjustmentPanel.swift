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

struct CardAdjustmentPanel: View {
    @Bindable var document: CardDocument
    @Binding var textEditingActive: Bool
    @State private var field: CardField = .author
    @State private var adjustment: CardAdjustment = .scale
    @State private var mode: EditingMode = .information
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    private enum EditingMode: Hashable { case information, style }

    var body: some View {
        panel
    }

    private var panel: some View {
        GeometryReader { geometry in
            let contentWidth = min(640, geometry.size.width)
            let selectorWidth = min(200, contentWidth * (dynamicTypeSize.isAccessibilitySize ? 0.32 : 0.4))
            let detailWidth = max(0, contentWidth - selectorWidth - 16)
            VStack(spacing: 12) {
                Picker("card.edit.mode", selection: $mode) {
                    Label("card.information", systemImage: "info.circle").tag(EditingMode.information)
                    Label("card.style", systemImage: "slider.horizontal.3").tag(EditingMode.style)
                }
                .pickerStyle(.segmented)
                HStack(alignment: .top, spacing: 16) {
                    ZStack {
                        if mode == .information {
                            EditorItemPicker(items: CardField.allCases, selection: $field) {
                                $0 == .focalLength ? "card.field.focalLength.short" : LocalizedStringKey($0.titleKey)
                            }
                            .transition(.opacity)
                        } else {
                            EditorItemPicker(items: CardAdjustment.allCases, selection: $adjustment) { $0.title }
                                .transition(.opacity)
                        }
                    }
                    .animation(.easeInOut(duration: 0.18), value: mode)
                    .frame(width: selectorWidth)

                    MobileCardInspector(document: document, field: field, adjustment: adjustment,
                                        information: mode == .information, selectionID: detailID,
                                        textEditingActive: $textEditingActive)
                        .frame(width: detailWidth)
                        .frame(maxHeight: .infinity)
                }
                .frame(width: contentWidth)
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 8)
            .background(alignment: .topTrailing) {
                PhotoAmbientBackdrop(sourceURL: document.sourceURL)
                    .frame(width: detailWidth + selectorWidth * 0.18,
                           height: min(294, geometry.size.height * 0.62))
                    .mask {
                        LinearGradient(stops: [
                            .init(color: .clear, location: 0),
                            .init(color: .white, location: 0.18),
                            .init(color: .white, location: 0.82),
                            .init(color: .clear, location: 1)
                        ], startPoint: .leading, endPoint: .trailing)
                    }
                    .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topTrailing)
                    .allowsHitTesting(false)
            }
        }
        .onChange(of: mode) { _, _ in textEditingActive = false }
    }

    private var detailID: String {
        mode == .information ? "field-\(field.rawValue)" : "style-\(adjustment.rawValue)"
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
            let buttonHeight: CGFloat = dynamicTypeSize.isAccessibilitySize ? 64 : 48
            let controlHeight = min(dynamicTypeSize.isAccessibilitySize ? 100 : 76, max(68, height * 0.20))
            let descriptionHeight = min(dynamicTypeSize.isAccessibilitySize ? 130 : 76,
                                        max(52, height * (dynamicTypeSize.isAccessibilitySize ? 0.3 : 0.19)))
            let showsDescription = height >= (dynamicTypeSize.isAccessibilitySize ? 270 : 210)
            let showsDetail = height >= (dynamicTypeSize.isAccessibilitySize ? 420 : 280)
            let previewRoom = max(64, height - controlHeight - descriptionHeight - buttonHeight - 24)
            let previewHeight = min(geometry.size.width / CardDetailPreview.referenceAspect, 220, previewRoom)

            VStack(alignment: .leading, spacing: 8) {
                if showsDetail {
                    CardDetailPreview(document: document, height: previewHeight, processing: textEditingActive,
                                      highlightedField: information ? field : nil,
                                      highlightedStyle: information ? nil : adjustment)
                }

                ZStack(alignment: .leading) {
                    if information {
                        TextField("", text: $document.card[field], axis: .vertical)
                            .textFieldStyle(.plain)
                            .lineLimit(2...3)
                            .foregroundStyle(.yellow)
                            .tint(.yellow)
                            .padding(10)
                            .frame(height: controlHeight - 8, alignment: .top)
                            .glassEffect(.clear.interactive(), in: .rect(cornerRadius: 12))
                            .padding(4)
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

                Spacer(minLength: 0)

                if showsDescription {
                    ZStack(alignment: .topLeading) {
                        Text(LocalizedStringKey(information
                             ? "card.field." + field.rawValue + ".hint"
                             : "card.style." + adjustment.rawValue + ".hint"))
                            .font(.subheadline)
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
