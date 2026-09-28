import SwiftUI
import PhotosUI

struct MetadataScreen: View {
    @Environment(PhotoWorkspace.self) private var workspace
    @Bindable var state: MetadataState
    @State private var mode = Mode.view
    @State private var showingPicker = false
    @State private var showingSave = false
    @State private var confirmReplace = false
    @State private var replacementIDs: [String]?
    @State private var summaryState = OriginalSummaryState()
    private enum Mode: Hashable { case view, edit }
    private var session: CardSession { workspace.session(for: .metadata) }
    private var replacementTitle: LocalizedStringKey {
        session.documents.count == 1 ? "card.replace.confirm.one" : "card.replace.confirm.many"
    }

    var body: some View {
        @Bindable var session = session
        NavigationStack {
            GeometryReader { geometry in
                let metrics = PhotoPreviewMetrics(available: geometry.size)
                if let document = session.current {
                    PhotoPreviewPage(sourceURL: document.sourceURL, metrics: metrics,
                        imageAspectRatio: CGFloat(document.metadata.width) / CGFloat(max(1, document.metadata.height))) {
                        OriginalSummaryPhoto(document: document, state: summaryState)
                    } accessories: {
                        OriginalSummaryActions(document: document, metrics: metrics, state: summaryState,
                            actions: { openPhotoButton })
                    } content: {
                        detailsForm(document)
                    }
                } else {
                    Form { intro }.photoPageForm()
                }
            }
#if !os(macOS)
            .toolbarVisibility(.hidden, for: .navigationBar)
#endif
            .disabled(state.busy || session.busy)
            .overlay {
                if state.busy || session.busy {
                    ProgressView().padding().background(.regularMaterial, in: .rect(cornerRadius: 16))
                }
            }
        }
        .task(id: session.current?.id) { state.refresh(document: session.current) }
        .onChange(of: workspace.pendingMetadataAssetIDs) { _, _ in handleHandoff() }
        .onChange(of: session.busy) { _, busy in if !busy { handleHandoff() } }
        .onChange(of: workspace.openPickerRequest) { _, requested in
            if requested == .metadata { workspace.openPickerRequest = nil; choosePhoto() }
        }
        .onChange(of: workspace.saveSheetRequest) { _, requested in
            guard requested == .metadata else { return }
            workspace.saveSheetRequest = nil
            if let document = session.current, state.hasPendingAdd(document) { showingSave = true }
        }
        .onAppear(perform: handleHandoff)
        .sheet(isPresented: $showingPicker) {
            NativePhotoPicker { results in
                showingPicker = false
                let target = session
                Task { await target.open(results) }
            }
#if os(macOS)
            .frame(minWidth: 680, idealWidth: 820, minHeight: 520, idealHeight: 620)
#endif
        }
        .sheet(isPresented: $showingSave) {
            MetadataSaveSheet(document: session.current) { updateOriginal in
                guard let document = session.current else { return }
                let owner = session
                Task {
                    guard !owner.busy else { return }
                    owner.cancelLocationLookup()
                    owner.busy = true
                    defer { owner.busy = false }
                    await state.save(document: document, updateOriginal: updateOriginal)
                }
            }
        }
        .alert("metadata.error.title", isPresented: Binding(
            get: { state.errorMessage != nil || session.errorMessage != nil },
            set: { if !$0 { state.errorMessage = nil; session.errorMessage = nil } })) {
                Button("done", role: .cancel) { }
            } message: { Text(state.errorMessage ?? session.errorMessage ?? "") }
        .alert("metadata.save.complete", isPresented: Binding(
            get: { state.saved && state.errorMessage == nil }, set: { if !$0 { state.saved = false } })) {
                Button("done", role: .cancel) { }
            } message: { Text(state.savedToOriginal ? "metadata.saved.updated" : "metadata.saved") }
    }

    private var intro: some View {
        Section {
            PhotoPageIntro(title: "metadata.intro.title", description: "metadata.intro.description", symbol: "info.circle")
            Button("card.open", systemImage: "photo.badge.plus") {
                choosePhoto()
            }
        }
    }

    private var modeControls: some View {
        Picker("tab.metadata", selection: $mode) {
            Text("metadata.mode.view").tag(Mode.view)
            Text("metadata.mode.edit").tag(Mode.edit)
        }
        .pickerStyle(.segmented)
        .padding(.horizontal, PhotoPageLayout.margin)
        .padding(.vertical, 8)
    }

    private var openPhotoButton: some View {
        PhotoPreviewMenu(title: "card.open", systemImage: "photo.badge.plus") {
            Button("card.open", action: choosePhoto)
            if session.documents.count > 1 {
                Picker("metadata.photo.name", selection: Binding(get: { session.selectedID }, set: { session.selectedID = $0 })) {
                    ForEach(session.documents) { Text($0.originalName).tag(Optional($0.id)) }
                }
            }
        }
        .confirmationDialog(replacementTitle, isPresented: $confirmReplace, titleVisibility: .visible) {
            Button("card.replace", role: .destructive) {
                if let ids = replacementIDs {
                    replacementIDs = nil
                    Task { await session.openAssets(ids) }
                } else { showingPicker = true }
            }
            Button("card.cancel", role: .cancel) { replacementIDs = nil }
        }
    }

    private func choosePhoto() {
        replacementIDs = nil
        if session.current != nil { confirmReplace = true }
        else { showingPicker = true }
    }

    private func detailsForm(_ document: CardDocument) -> some View {
        Form { details(document) }
            .photoPageForm()
            .scrollContentBackground(.hidden)
            .safeAreaBar(edge: .top, spacing: 0) { modeControls }
    }

    @ViewBuilder private func details(_ document: CardDocument) -> some View {
        if mode == .view {
            PhotoDetailInformation(asset: CardPhotoLibrary.asset(document.assetIdentifier), document: document,
                                   coordinate: document.location?.coordinate)
        } else {
            Section {
                Toggle("card.save.exif", isOn: $state.options.keepExif).tint(.green)
                Toggle("card.save.location", isOn: $state.options.keepLocation).tint(.green)
                Toggle("card.save.time", isOn: $state.options.keepCaptureTime).tint(.green)
            } header: { Text("card.save.metadata") }
            footer: { Text("metadata.edit.knownFields") }
            Section {
                if !state.supportsInjection(document) {
                    Text(state.unavailableReason(document)).foregroundStyle(.secondary)
                } else if state.report == nil {
                    ProgressView("metadata.report.loading")
                } else {
                    if state.canAddPhotographic(document) {
                        Toggle("metadata.styles.standard", isOn: $state.injectStandard).tint(.green)
                    } else {
                        LabeledContent("metadata.styles.standard") { Text("metadata.styles.value.present").foregroundStyle(.secondary) }
                    }
                    if state.canAddTexture(document) {
                        Toggle("metadata.styles.texture", isOn: $state.includeTexture)
                            .tint(.green)
                            .disabled(!state.coverage(document).photographic && !state.injectStandard)
                    } else {
                        LabeledContent("metadata.styles.texture") { Text("metadata.styles.value.present").foregroundStyle(.secondary) }
                    }
                    Button("metadata.save", systemImage: "square.and.arrow.down") { showingSave = true }
                        .disabled(!state.hasPendingAdd(document))
                }
            } header: { Text("metadata.styles.header") }
            footer: { Text("metadata.styles.footer") }
        }
    }

    private func handleHandoff() {
        guard !session.busy, let ids = workspace.pendingMetadataAssetIDs else { return }
        workspace.pendingMetadataAssetIDs = nil
        if session.current != nil { replacementIDs = ids; confirmReplace = true }
        else { Task { await session.openAssets(ids) } }
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
            && [.stillHEIC, .heicWithAuxiliaryData, .stillJPEG, .ultraHDRJPEG, .stillPNG].contains(document.metadata.kind)
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
                        LabeledContent("metadata.save.destination") { Text("metadata.save.copy") }
                    }
                } footer: {
                    Text(updateOriginal ? "metadata.save.update.description" : "metadata.save.copy.description")
                }
            }
            .photoPageForm()
            .navigationTitle("metadata.save.options")
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("card.cancel", action: dismiss.callAsFunction) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("metadata.save") { dismiss(); save(updateOriginal && canUpdate) }
                }
            }
        }
#if os(macOS)
        .frame(minWidth: 480, idealWidth: 560, minHeight: 320, idealHeight: 380)
#endif
    }
}
