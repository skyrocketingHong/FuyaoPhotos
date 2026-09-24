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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
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
                        document.card[field] = document.metadata.card[field]
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
                }
                Text(LocalizedStringKey("card.style." + adjustment.rawValue + ".hint"))
                    .font(.subheadline).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(adjustment.percentage
                     ? document.card.style[keyPath: adjustment.keyPath].formatted(.percent.precision(.fractionLength(0)))
                     : document.card.style[keyPath: adjustment.keyPath].formatted(.number.precision(.fractionLength(0))))
                    .font(.title3.monospacedDigit()).foregroundStyle(.yellow)
                Slider(value: $document.card.style[dynamicMember: adjustment.keyPath], in: adjustment.range) {
                    Text(adjustment.title)
                } minimumValueLabel: {
                    Image(systemName: adjustment.symbols.0).accessibilityHidden(true)
                } maximumValueLabel: {
                    Image(systemName: adjustment.symbols.1).accessibilityHidden(true)
                }
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
                let selectorWidth = min(200, geometry.size.width * 0.4)
                let detailWidth = max(0, geometry.size.width - selectorWidth - 16)
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

                    ZStack(alignment: .topLeading) {
                        if mode == .information {
                            MobileInformationDetail(document: document, field: field)
                                .id(detailID)
                                .transition(.opacity)
                        } else {
                            MobileStyleDetail(document: document, adjustment: adjustment)
                                .id(detailID)
                                .transition(.opacity)
                        }
                    }
                    .frame(width: detailWidth, height: max(0, geometry.size.height - 12), alignment: .topLeading)
                    .animation(.easeInOut(duration: reduceMotion ? 0.12 : 0.22), value: detailID)
                    .padding(.top, 12)
                }
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
private struct MobileInformationDetail: View {
    @Bindable var document: CardDocument
    let field: CardField
    @ScaledMetric(relativeTo: .body) private var controlHeight: CGFloat = 104

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            MobileDetailDescription(key: LocalizedStringKey("card.field." + field.rawValue + ".hint"))

            TextField("", text: $document.card[field], axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .lineLimit(4...4)
                .foregroundStyle(.yellow)
                .frame(height: controlHeight, alignment: .top)
                .accessibilityLabel(Text(LocalizedStringKey(field.titleKey)))

            Button("card.restore.selected") {
                document.card[field] = document.metadata.card[field]
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .frame(height: 44, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }
}

private struct MobileStyleDetail: View {
    @Bindable var document: CardDocument
    let adjustment: CardAdjustment
    @ScaledMetric(relativeTo: .body) private var controlHeight: CGFloat = 104

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            MobileDetailDescription(key: LocalizedStringKey("card.style." + adjustment.rawValue + ".hint"))

            VStack(alignment: .leading, spacing: 12) {
                Text(adjustment.percentage
                     ? document.card.style[keyPath: adjustment.keyPath].formatted(.percent.precision(.fractionLength(0)))
                     : document.card.style[keyPath: adjustment.keyPath].formatted(.number.precision(.fractionLength(0))))
                    .font(.headline.monospacedDigit())
                    .foregroundStyle(.yellow)
                Slider(value: $document.card.style[dynamicMember: adjustment.keyPath], in: adjustment.range) {
                    Text(adjustment.title)
                } minimumValueLabel: {
                    Image(systemName: adjustment.symbols.0).accessibilityHidden(true)
                } maximumValueLabel: {
                    Image(systemName: adjustment.symbols.1).accessibilityHidden(true)
                }
            }
            .frame(height: controlHeight, alignment: .top)

            Button("card.restore.selected") {
                document.card.style[keyPath: adjustment.keyPath] = PhotoCardStyle()[keyPath: adjustment.keyPath]
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .frame(height: 44, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }
}

private struct MobileDetailDescription: View {
    let key: LocalizedStringKey
    @ScaledMetric(relativeTo: .subheadline) private var reservedHeight: CGFloat = 120

    var body: some View {
        Text(key)
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .lineLimit(6)
            .minimumScaleFactor(0.9)
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .frame(height: reservedHeight)
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
