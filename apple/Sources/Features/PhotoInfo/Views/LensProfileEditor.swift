import SwiftUI

struct LensProfileEditor: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Bindable var session: LensEditingSession
    var inline = false
    let apply: (LensProfile) -> Void
    let close: () -> Void
    @State private var confirmDiscard = false
    private var draft: LensProfileDraft { session.draft }
    private var title: LocalizedStringKey { session.initial.isNew ? "lens.add" : "lens.edit" }

    var body: some View {
        VStack(spacing: 0) {
            if inline {
                HStack {
                    Text(title).font(.headline)
                    Spacer()
                    cancelButton
                    applyButton
                }.padding(20)
            }
            GeometryReader { geometry in
                if geometry.size.width >= 820 && !dynamicTypeSize.isAccessibilitySize {
                    HStack(alignment: .top, spacing: 0) {
                        Form { identity; physical }.photoPageForm()
                        Form { equivalent; zoom; validation }.photoPageForm()
                    }
                } else {
                    Form { identity; equivalent; physical; zoom; validation }.photoPageForm()
                }
            }
            .navigationTitle(title)
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                if !inline {
                    ToolbarItem(placement: .cancellationAction) { cancelButton }
                    ToolbarItem(placement: .confirmationAction) { applyButton }
                }
            }
            .confirmationDialog("lens.discard.title", isPresented: $confirmDiscard, titleVisibility: .visible) {
                Button("lens.discard", role: .destructive, action: close)
                Button("lens.keepEditing", role: .cancel) { }
            } message: { Text("lens.editor.discard.message") }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var cancelButton: some View {
        Button("cancel") { if session.hasChanges { confirmDiscard = true } else { close() } }
            .keyboardShortcut(.cancelAction)
    }

    private var applyButton: some View {
        Button("lens.apply") { if let profile = draft.profile { apply(profile); close() } }
            .disabled(draft.profile == nil || !session.hasChanges)
            .keyboardShortcut(.defaultAction)
    }

    private var identity: some View {
        Section {
            identityField("lens.device", text: $session.draft.device)
            identityField("lens.exifModel", text: $session.draft.exifModel)
            identityField("lens.name", text: $session.draft.name)
            identityField("lens.stylePrefix", text: $session.draft.stylePrefix)
            Text("lens.stylePrefix.footer").font(.footnote).foregroundStyle(.secondary)
            numberField("lens.originalMegapixels", text: $session.draft.originalMegapixels, unit: "MP")
            Text("lens.originalMegapixels.footer").font(.footnote).foregroundStyle(.secondary)
            Picker("lens.facing", selection: $session.draft.facing) {
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
            numberField("lens.range.min", text: $session.draft.equivalentMin, unit: "mm")
            numberField("lens.range.max", text: $session.draft.equivalentMax, unit: "mm")
        } header: { Text("lens.equivalent.header") }
        footer: { Text("lens.equivalent.footer") }
    }

    private var physical: some View {
        Section {
            numberField("lens.range.min", text: $session.draft.physicalMin, unit: "mm")
            numberField("lens.range.max", text: $session.draft.physicalMax, unit: "mm")
        } header: { Text("lens.physical.header") }
        footer: { Text("lens.physical.footer") }
    }

    private var zoom: some View {
        Section {
            numberField("lens.range.min", text: $session.draft.zoomMin, unit: "×")
            numberField("lens.range.max", text: $session.draft.zoomMax, unit: "×")
            numberField("lens.digitalMax", text: $session.draft.digitalZoomMax, unit: "×")
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
