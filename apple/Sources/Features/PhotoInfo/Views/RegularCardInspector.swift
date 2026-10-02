import SwiftUI

struct RegularCardInspector: View {
    @Bindable var document: CardDocument
    @Bindable var selection: CardInspectorSelection
    @Binding var textEditingActive: Bool
    @FocusState private var focusedField: CardField?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geometry in
            let detailHeight: CGFloat = dynamicTypeSize.isAccessibilitySize || geometry.size.height < 480
                ? 0 : geometry.size.height < 640 ? 112 : 160
            VStack(spacing: 0) {
                Picker("card.edit.mode", selection: $selection.mode) {
                    Text("card.information").tag(CardInspectorSelection.Mode.information)
                    Text("card.style").tag(CardInspectorSelection.Mode.style)
                }
                .pickerStyle(.segmented)
                .padding(20)
                if detailHeight > 0 {
                    CardDetailPreview(document: document, processing: textEditingActive,
                        highlightedField: selection.mode == .information ? focusedField : nil,
                        highlightedStyle: selection.mode == .style ? selection.adjustment : nil)
                        .frame(height: detailHeight)
                        .padding(.horizontal, PhotoPageLayout.margin)
                        .padding(.bottom, 16)
                }
                Form {
                    if selection.mode == .information {
                        Section("card.group.photo") { fieldRows([.device, .camera, .imageSize]) }
                        Section("card.group.capture") { fieldRows([.focalLength, .exposure, .aperture, .iso, .photographicStyle]) }
                        Section("card.group.credit") { fieldRows([.author, .location]) }
                    } else {
                        Section("card.group.layout") { styleRows([.scale, .textScale, .rightInset, .bottomInset]) }
                        Section("card.group.appearance") { styleRows([.opacity, .blur, .cornerRadius]) }
                    }
                }
                .photoPageForm()
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: selection.mode)
                ViewThatFits(in: .horizontal) {
                    HStack { restoreActions }
                    VStack { restoreActions }
                }
                .buttonStyle(.bordered)
                .padding(20)
            }
        }
        .onChange(of: focusedField) { _, field in
            if let field { selection.field = field }
            else { textEditingActive = false }
        }
        .onChange(of: document.card) { _, _ in textEditingActive = focusedField != nil }
        .onChange(of: document.id) { _, _ in focusedField = nil; textEditingActive = false }
        .onChange(of: selection.mode) { _, _ in focusedField = nil; textEditingActive = false }
        .onDisappear { textEditingActive = false }
    }

    private func fieldRows(_ fields: [CardField]) -> some View {
        ForEach(fields) { field in
            VStack(alignment: .leading, spacing: 6) {
                Text(LocalizedStringKey(field.titleKey)).font(.subheadline).foregroundStyle(.secondary)
                TextField(LocalizedStringKey(field.titleKey), text: $document.card[field], axis: .vertical)
                    .labelsHidden().textFieldStyle(.roundedBorder)
                    .lineLimit(1...5)
                    .focused($focusedField, equals: field)
                if field == .imageSize {
                    CardImageSizeMenu(document: document) { focusedField = nil; textEditingActive = false }
                }
            }
        }
    }

    private func styleRows(_ adjustments: [CardAdjustment]) -> some View {
        ForEach(adjustments) { adjustment in
            VStack(alignment: .leading, spacing: 8) {
                Text(adjustment.title).font(.subheadline).foregroundStyle(.secondary)
                CardStyleSlider(value: styleBinding(adjustment), range: adjustment.range,
                    defaultValue: PhotoCardStyle()[keyPath: adjustment.keyPath], label: adjustment.title,
                    minimumSymbol: adjustment.symbols.0, maximumSymbol: adjustment.symbols.1,
                    formattedValue: adjustment.percentage
                        ? document.card.style[keyPath: adjustment.keyPath].formatted(.percent.precision(.fractionLength(0)))
                        : document.card.style[keyPath: adjustment.keyPath].formatted(.number.precision(.fractionLength(0))))
                    .frame(minHeight: 44)
            }
        }
    }

    private func styleBinding(_ adjustment: CardAdjustment) -> Binding<Double> {
        Binding(get: { document.card.style[keyPath: adjustment.keyPath] }, set: { value in
            selection.adjustment = adjustment
            document.card.style[keyPath: adjustment.keyPath] = value
        })
    }

    private var restoreTitle: String {
        let key = selection.mode == .information ? selection.field.titleKey
            : selection.adjustment == .cornerRadius ? "card.radius" : "card." + selection.adjustment.rawValue
        return String(format: String.localized("card.restore.named"), String.localized(key))
    }

    @ViewBuilder private var restoreActions: some View {
        Button(restoreTitle, systemImage: "arrow.counterclockwise") {
            focusedField = nil
            if selection.mode == .information { document.restoreField(selection.field, preferLensPixelCount: CardPreferences.shared.preferLensPixelCount) }
            else { document.card.style[keyPath: selection.adjustment.keyPath] = PhotoCardStyle()[keyPath: selection.adjustment.keyPath] }
        }
        Button("card.restore.all", systemImage: "arrow.counterclockwise.circle") {
            focusedField = nil
            if selection.mode == .information {
                document.restoreInformation(preferLensPixelCount: CardPreferences.shared.preferLensPixelCount)
            } else { document.card.style = PhotoCardStyle() }
        }
    }
}
