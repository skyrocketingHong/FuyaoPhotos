import SwiftUI

struct LensProfileEditor: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var draft: LensProfileDraft
    @State private var confirmDiscard = false
    private let initial: LensProfileDraft
    private let apply: (LensProfile) -> Void

    init(draft: LensProfileDraft, apply: @escaping (LensProfile) -> Void) {
        initial = draft
        _draft = State(initialValue: draft)
        self.apply = apply
    }

    private var hasChanges: Bool {
        if let profile = draft.profile, let original = initial.profile { return profile != original }
        return draft != initial
    }

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                if geometry.size.width >= 820 && !dynamicTypeSize.isAccessibilitySize {
                    HStack(alignment: .top, spacing: 0) {
                        Form { identity; physical }.formStyle(.grouped)
                        Form { equivalent; zoom; validation }.formStyle(.grouped)
                    }
                } else {
                    Form { identity; equivalent; physical; zoom; validation }.formStyle(.grouped)
                }
            }
            .navigationTitle(initial.isNew ? "lens.add" : "lens.edit")
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("cancel") { if hasChanges { confirmDiscard = true } else { dismiss() } }
                        .keyboardShortcut(.cancelAction)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("lens.apply") {
                        if let profile = draft.profile { apply(profile); dismiss() }
                    }
                    .disabled(draft.profile == nil || !hasChanges)
                    .keyboardShortcut(.defaultAction)
                }
            }
            .confirmationDialog("lens.discard.title", isPresented: $confirmDiscard, titleVisibility: .visible) {
                Button("lens.discard", role: .destructive) { dismiss() }
                Button("lens.keepEditing", role: .cancel) { }
            } message: { Text("lens.editor.discard.message") }
        }
        .interactiveDismissDisabled(hasChanges)
#if os(macOS)
        .frame(minWidth: 540, idealWidth: 880, minHeight: 560, idealHeight: 680)
#endif
    }

    private var identity: some View {
        Section {
            identityField("lens.device", text: $draft.device)
            identityField("lens.exifModel", text: $draft.exifModel)
            identityField("lens.name", text: $draft.name)
            Picker("lens.facing", selection: $draft.facing) {
                ForEach(LensProfile.Facing.allCases) { direction in
                    Text(LocalizedStringKey(direction.titleKey)).tag(direction)
                }
            }
        } header: { Text("lens.identity.header") }
        footer: { Text("lens.identity.footer") }
    }

    private func identityField(_ title: LocalizedStringKey, text: Binding<String>) -> some View {
        LabeledContent(title) {
            TextField(title, text: text, axis: .vertical)
                .labelsHidden()
                .multilineTextAlignment(.trailing)
#if !os(macOS)
                .textInputAutocapitalization(.never)
#endif
                .autocorrectionDisabled()
        }
    }

    private var equivalent: some View {
        Section {
            numberField("lens.range.min", text: $draft.equivalentMin, unit: "mm")
            numberField("lens.range.max", text: $draft.equivalentMax, unit: "mm")
        } header: { Text("lens.equivalent.header") }
        footer: { Text("lens.equivalent.footer") }
    }

    private var physical: some View {
        Section {
            numberField("lens.range.min", text: $draft.physicalMin, unit: "mm")
            numberField("lens.range.max", text: $draft.physicalMax, unit: "mm")
        } header: { Text("lens.physical.header") }
        footer: { Text("lens.physical.footer") }
    }

    private var zoom: some View {
        Section {
            numberField("lens.range.min", text: $draft.zoomMin, unit: "×")
            numberField("lens.range.max", text: $draft.zoomMax, unit: "×")
            numberField("lens.digitalMax", text: $draft.digitalZoomMax, unit: "×")
        } header: { Text("lens.zoom.header") }
        footer: { Text("lens.zoom.footer") }
    }

    private var validation: some View {
        Section {
            if let issue = draft.validationKey {
                Label(LocalizedStringKey(issue), systemImage: "info.circle")
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Text("lens.apply.footer").font(.footnote).foregroundStyle(.secondary)
        }
    }

    private func numberField(_ title: LocalizedStringKey, text: Binding<String>, unit: String) -> some View {
        LabeledContent(title) {
            HStack {
                TextField(title, text: text)
#if !os(macOS)
                    .keyboardType(.decimalPad)
#endif
                    .monospacedDigit()
                    .multilineTextAlignment(.trailing)
                    .labelsHidden()
                    .accessibilityLabel(title)
                    .accessibilityValue(Text(verbatim: "\(text.wrappedValue) \(unit)"))
                Text(unit).foregroundStyle(.secondary).accessibilityHidden(true)
            }
        }
    }
}

struct LensProfileDraft: Equatable, Identifiable {
    let id: UUID
    let isNew: Bool
    var device = ""
    var exifModel = ""
    var name = ""
    var facing = LensProfile.Facing.unspecified
    var equivalentMin = ""
    var equivalentMax = ""
    var physicalMin = ""
    var physicalMax = ""
    var zoomMin = ""
    var zoomMax = ""
    var digitalZoomMax = ""

    init(profile: LensProfile? = nil) {
        id = profile?.id ?? UUID()
        isNew = profile == nil
        guard let profile else { return }
        device = profile.device; exifModel = profile.exifModel; name = profile.name; facing = profile.facing
        equivalentMin = Self.number(profile.equivalentMin); equivalentMax = Self.number(profile.equivalentMax)
        physicalMin = Self.number(profile.physicalMin); physicalMax = Self.number(profile.physicalMax)
        zoomMin = Self.number(profile.zoomMin); zoomMax = Self.number(profile.zoomMax)
        digitalZoomMax = Self.number(profile.digitalZoomMax)
    }

    var profile: LensProfile? {
        guard validationKey == nil, let low = Self.parse(equivalentMin), let high = Self.parse(equivalentMax) else { return nil }
        return LensProfile(id: id, device: device.trimmingCharacters(in: .whitespacesAndNewlines),
                           exifModel: exifModel.trimmingCharacters(in: .whitespacesAndNewlines),
                           name: name.trimmingCharacters(in: .whitespacesAndNewlines), facing: facing,
                           equivalentMin: low, equivalentMax: high,
                           physicalMin: Self.parse(physicalMin), physicalMax: Self.parse(physicalMax),
                           zoomMin: Self.parse(zoomMin), zoomMax: Self.parse(zoomMax), digitalZoomMax: Self.parse(digitalZoomMax))
    }

    var validationKey: String? {
        if [device, exifModel, name].contains(where: { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) { return "lens.validation.identity" }
        if [device, exifModel, name].contains(where: { $0.count > 256 }) { return "lens.validation.length" }
        if !Self.validRange(equivalentMin, equivalentMax, limit: 2_000, optional: false) { return "lens.validation.equivalent" }
        if !Self.validRange(physicalMin, physicalMax, limit: 1_000) { return "lens.validation.physical" }
        if !Self.validRange(zoomMin, zoomMax, limit: 200) { return "lens.validation.zoom" }
        if Self.parse(equivalentMin) == Self.parse(equivalentMax), Self.parse(zoomMin) != Self.parse(zoomMax) { return "lens.validation.fixed" }
        if !digitalZoomMax.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            guard let value = Self.parse(digitalZoomMax), let high = Self.parse(zoomMax), value >= high, value <= 200 else { return "lens.validation.digital" }
        }
        return nil
    }

    private static func validRange(_ low: String, _ high: String, limit: Double, optional: Bool = true) -> Bool {
        if optional && low.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && high.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return true }
        guard let low = parse(low), let high = parse(high) else { return false }
        return low > 0 && high >= low && high <= limit
    }

    private static func parse(_ value: String) -> Double? {
        let text = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let separator = Locale.current.decimalSeparator ?? "."
        let normalized = separator == "." ? text : text.replacingOccurrences(of: separator, with: ".")
        guard let number = Double(normalized), number.isFinite else { return nil }
        return number
    }

    private static func number(_ value: Double?) -> String {
        value?.formatted(.number.grouping(.never).precision(.fractionLength(0...8))) ?? ""
    }
}
