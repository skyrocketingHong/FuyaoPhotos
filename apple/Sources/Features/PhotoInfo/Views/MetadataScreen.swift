import SwiftUI
import PhotosUI

/// The metadata tab: one page per photo showing the technical metadata report and the
/// photographic styles injection with its own save actions. Photo management stays in
/// the cards tab; this page only reads the shared session's current photo.
struct MetadataScreen: View {
    @Bindable var session: CardSession
    @State private var state = MetadataState()
    @State private var showingPicker = false
    @State private var showingSave = false

    private var errorShown: Binding<Bool> {
        Binding(get: { state.errorMessage != nil }, set: { if !$0 { state.errorMessage = nil } })
    }
    private var savedShown: Binding<Bool> {
        Binding(get: { state.saved && state.errorMessage == nil }, set: { if !$0 { state.saved = false } })
    }

    var body: some View {
        NavigationStack {
            Group {
                if let document = session.current {
                    content(document)
                        .disabled(state.busy)
                        .overlay { if state.busy { busyOverlay } }
                } else {
                    ContentUnavailableView {
                        Label("metadata.empty.title", systemImage: "info.circle")
                    } description: {
                        Text("metadata.empty.description")
                    } actions: {
                        Button("card.open", action: { showingPicker = true })
                            .buttonStyle(.borderedProminent)
                    }
                }
            }
            .navigationTitle("tab.metadata")
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                if session.current != nil {
                    ToolbarItem(placement: .primaryAction) {
                        Button("metadata.save", systemImage: "checkmark") { showingSave = true }
                            .labelStyle(.iconOnly)
                            .buttonBorderShape(.circle)
                            .disabled(state.busy || session.current.map { !state.hasPendingAdd($0) } ?? true)
                            .accessibilityLabel(Text("metadata.save"))
                    }
                }
            }
        }
        .task(id: session.current?.id) { state.refresh(document: session.current) }
        .sheet(isPresented: $showingPicker) { pickerSheet }
        .sheet(isPresented: $showingSave) { saveSheet }
        .alert("metadata.error.title", isPresented: errorShown) {
            Button("done", role: .cancel) { }
        } message: { Text(state.errorMessage ?? "") }
        .alert("metadata.save.complete", isPresented: savedShown) {
            Button("done", role: .cancel) { }
        } message: { Text("metadata.saved") }
        .sensoryFeedback(.success, trigger: state.saved)
    }

    private func content(_ document: CardDocument) -> some View {
        Form {
            if session.documents.count > 1 {
                Section {
                    Picker("metadata.photo.name", selection: $session.selectedID) {
                        ForEach(session.documents) { item in
                            Text(item.originalName).tag(Optional(item.id))
                        }
                    }
                }
            }
            MediaMetadataReportSection(report: state.report)
            stylesSection(document)
        }
        .formStyle(.grouped)
    }

    private func stylesSection(_ document: CardDocument) -> some View {
        Section {
            if !state.supportsInjection(document) {
                LabeledContent("metadata.styles.status") {
                    Text("metadata.styles.value.unavailable")
                }
            } else if state.report == nil {
                HStack(spacing: 12) {
                    ProgressView()
                    Text("metadata.report.loading")
                }
            } else {
                if state.canAddPhotographic(document) {
                    Toggle("metadata.styles.standard", isOn: $state.injectStandard)
                } else {
                    LabeledContent("metadata.styles.standard") {
                        Text("metadata.styles.value.present")
                    }
                }
                if state.canAddTexture(document) {
                    let standardPresent = state.coverage(document).photographic
                        || (state.injectStandard && state.canAddPhotographic(document))
                    Toggle("metadata.styles.texture", isOn: $state.includeTexture)
                        .disabled(!standardPresent)
                } else {
                    LabeledContent("metadata.styles.texture") {
                        Text("metadata.styles.value.present")
                    }
                }
            }
        } header: {
            Text("metadata.styles.header")
        } footer: {
            if !state.supportsInjection(document) {
                Text(state.unavailableReason(document))
            } else {
                Text("metadata.styles.footer")
            }
        }
    }

    private var busyOverlay: some View {
        ProgressView()
            .padding()
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private var pickerSheet: some View {
        NativePhotoPicker { results in
            showingPicker = false
            Task { await session.open(results) }
        }
#if os(macOS)
        .frame(minWidth: 680, idealWidth: 820, minHeight: 520, idealHeight: 620)
        .presentationSizing(.fitted)
#endif
    }

    private var saveSheet: some View {
        MetadataSaveSheet(document: session.current) { updateOriginal in
            guard let document = session.current else { return }
            Task { await state.save(document: document, updateOriginal: updateOriginal) }
        }
#if !os(macOS)
        .presentationDetents([.medium, .large])
#endif
    }
}

struct MetadataSaveSheet: View {
    let document: CardDocument?
    let save: (Bool) -> Void
    @State private var updateOriginal = false
    @Environment(\.dismiss) private var dismiss

    private var canUpdate: Bool {
        guard let document else { return false }
        return document.assetIdentifier != nil && !document.isLive
            && (document.metadata.kind == .stillHEIC || document.metadata.kind == .heicWithAuxiliaryData)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if canUpdate {
                        Picker("metadata.save.destination", selection: $updateOriginal) {
                            Text("metadata.save.copy").tag(false)
                            Text("metadata.save.update").tag(true)
                        }
                    } else {
                        LabeledContent("metadata.save.destination") {
                            Text("metadata.save.copy")
                        }
                    }
                } footer: {
                    if canUpdate && updateOriginal {
                        Text("metadata.save.update.description")
                    } else if document?.isLive == true {
                        Text("metadata.save.update.unavailable.live")
                    } else if document?.assetIdentifier == nil {
                        Text("metadata.save.copy.description")
                    } else {
                        Text("metadata.save.copy.description")
                    }
                }
            }
            .formStyle(.grouped)
            .navigationTitle("metadata.save.options")
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("card.cancel", action: dismiss.callAsFunction) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("metadata.save") {
                        dismiss()
                        save(updateOriginal)
                    }
                }
            }
        }
        .onAppear { if !canUpdate { updateOriginal = false } }
#if os(macOS)
        .frame(minWidth: 480, idealWidth: 560, minHeight: 320, idealHeight: 380)
#endif
    }
}
