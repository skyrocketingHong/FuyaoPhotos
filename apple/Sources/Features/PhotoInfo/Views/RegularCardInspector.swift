import SwiftUI

struct RegularCardInspector: View {
    @Bindable var document: CardDocument
    @Bindable var selection: CardInspectorSelection
    @Binding var textEditingActive: Bool
    @FocusState private var focusedField: CardField?

    var body: some View {
        VStack(spacing: 0) {
            Picker("card.edit.mode", selection: $selection.mode) {
                Text("card.information").tag(CardInspectorSelection.Mode.information)
                Text("card.style").tag(CardInspectorSelection.Mode.style)
            }
            .pickerStyle(.segmented)
            .padding(20)
            Form {
                Section {
                    CardDetailPreview(document: document, processing: textEditingActive,
                        highlightedField: selection.mode == .information ? focusedField : nil,
                        highlightedStyle: selection.mode == .style ? selection.adjustment : nil)
                        .frame(height: 160)
                }
                if selection.mode == .information {
                    Section("card.information") {
                        ForEach(CardField.allCases) { field in
                            VStack(alignment: .leading, spacing: 6) {
                                Text(LocalizedStringKey(field.titleKey)).font(.subheadline).foregroundStyle(.secondary)
                                TextField(LocalizedStringKey(field.titleKey), text: $document.card[field], axis: .vertical)
                                    .labelsHidden().textFieldStyle(.roundedBorder)
                                    .lineLimit(1...5)
                                    .focused($focusedField, equals: field)
                            }
                        }
                    }
                } else {
                    Section("card.style") {
                        ForEach(CardAdjustment.allCases) { adjustment in
                            CardStyleSlider(value: $document.card.style[dynamicMember: adjustment.keyPath], range: adjustment.range,
                                defaultValue: PhotoCardStyle()[keyPath: adjustment.keyPath], label: adjustment.title,
                                minimumSymbol: adjustment.symbols.0, maximumSymbol: adjustment.symbols.1,
                                formattedValue: adjustment.percentage
                                    ? document.card.style[keyPath: adjustment.keyPath].formatted(.percent.precision(.fractionLength(0)))
                                    : document.card.style[keyPath: adjustment.keyPath].formatted(.number.precision(.fractionLength(0))))
                                .onChange(of: document.card.style[keyPath: adjustment.keyPath]) { _, _ in selection.adjustment = adjustment }
                        }
                    }
                }
            }
            .photoPageForm()
            ViewThatFits(in: .horizontal) {
                HStack { restoreActions }
                VStack { restoreActions }
            }
            .buttonStyle(.bordered)
            .padding(20)
        }
        .onChange(of: focusedField) { _, field in
            if let field { selection.field = field }
            else { textEditingActive = false }
        }
        .onChange(of: document.card) { _, _ in textEditingActive = focusedField != nil }
        .onChange(of: selection.mode) { _, _ in focusedField = nil; textEditingActive = false }
        .onDisappear { textEditingActive = false }
    }

    @ViewBuilder private var restoreActions: some View {
        Button("card.restore.current", systemImage: "arrow.counterclockwise") {
            focusedField = nil
            if selection.mode == .information { document.card[selection.field] = document.defaultCard[selection.field] }
            else { document.card.style[keyPath: selection.adjustment.keyPath] = PhotoCardStyle()[keyPath: selection.adjustment.keyPath] }
        }
        Button("card.restore.all", systemImage: "arrow.counterclockwise.circle") {
            focusedField = nil
            if selection.mode == .information {
                for field in CardField.allCases { document.card[field] = document.defaultCard[field] }
            } else { document.card.style = PhotoCardStyle() }
        }
    }
}
