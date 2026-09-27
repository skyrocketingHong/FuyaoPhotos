import SwiftUI
import PhotosUI

/// The metadata tab: one page per photo showing the technical metadata report and the
/// photographic styles injection with its own save actions. It holds its own photo
/// session by default; Settings can make it share the cards session's photos.
struct MetadataScreen: View {
    @Bindable var session: CardSession
    @Environment(PhotoWorkspace.self) private var workspace
    @AppStorage("metadata.sharesCards") private var sharesCards = false
    @State private var state = MetadataState()
    @State private var showingPicker = false

    private var errorShown: Binding<Bool> {
        Binding(get: { state.errorMessage != nil }, set: { if !$0 { state.errorMessage = nil } })
    }
    private var savedShown: Binding<Bool> {
        Binding(get: { state.saved && state.errorMessage == nil }, set: { if !$0 { state.saved = false } })
    }

    var body: some View {
        Group {
            if sharesCards {
                metadataBody(sourcing: session)
            } else {
                metadataBody(sourcing: workspace.metadataSession)
            }
        }
        .task(id: handoffKey) { state.refresh(document: activeSession.current) }
        .onChange(of: sharesCards) { _, _ in state.refresh(document: activeSession.current) }
        .onChange(of: workspace.pendingMetadataAssetIDs) { _, _ in handlePendingHandoff() }
        .onAppear(perform: handlePendingHandoff)
        .sheet(isPresented: $showingPicker) { pickerSheet }
        .alert("metadata.error.title", isPresented: errorShown) {
            Button("done", role: .cancel) { }
        } message: { Text(state.errorMessage ?? "") }
        .alert("metadata.save.complete", isPresented: savedShown) {
            Button("done", role: .cancel) { }
        } message: {
            if state.savedToOriginal {
                Text("metadata.saved.updated")
            } else {
                Text("metadata.saved")
            }
        }
        .sensoryFeedback(.success, trigger: state.saved)
    }

    private var handoffKey: UUID? { activeSession.current?.id }

    private var activeSession: any MetadataPhotoSourcing { sharesCards ? session : workspace.metadataSession }

    private func metadataBody(sourcing: some MetadataPhotoSourcing) -> some View {
        return Group {
            if let document = sourcing.current {
                MetadataContent(document: document, sourcing: sourcing, state: state,
                                openInCards: { openInCards(document) },
                                showOnMap: { workspace.selectedTab = .map })
                    .disabled(state.busy)
                    .overlay { if state.busy { busyOverlay } }
            } else {
                Form {
                    MetadataIntroSection()
                    Section {
                        ContentUnavailableView {
                            Label("metadata.empty.title", systemImage: "photo")
                        } description: {
                            Text("metadata.empty.description")
                        } actions: {
                            Button("card.open", action: { showingPicker = true })
                                .buttonStyle(.borderedProminent)
                        }
                    }
                }
                .formStyle(.grouped)
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
            Task { await activeSession.open(results) }
        }
#if os(macOS)
        .frame(minWidth: 680, idealWidth: 820, minHeight: 520, idealHeight: 620)
        .presentationSizing(.fitted)
#endif
    }

    private func handlePendingHandoff() {
        guard !activeSession.busy, let ids = workspace.pendingMetadataAssetIDs else { return }
        workspace.pendingMetadataAssetIDs = nil
        Task { await activeSession.openAssets(ids) }
    }

    /// Sends the photo to the cards tab: a library photo is loaded into the cards
    /// session there; a picker-only import (no asset identifier) stays disabled.
    private func openInCards(_ document: CardDocument) {
        guard let identifier = document.assetIdentifier else { return }
        workspace.editPhotos([identifier])
    }
}

/// Body of the metadata page: the report, the styles section, and cross-tab handoffs.
private struct MetadataContent<Sourcing: MetadataPhotoSourcing>: View {
    let document: CardDocument
    var sourcing: Sourcing
    @Bindable var state: MetadataState
    let openInCards: () -> Void
    let showOnMap: () -> Void
    @State private var showingSave = false

    var body: some View {
        Form {
            MetadataIntroSection()
            if sourcing.documents.count > 1 {
                Section {
                    let session = sourcing
                    Picker("metadata.photo.name", selection: Binding(get: { session.selectedID }, set: { session.selectedID = $0 })) {
                        ForEach(sourcing.documents) { item in
                            Text(item.originalName).tag(Optional(item.id))
                        }
                    }
                }
            }
            MediaMetadataReportSection(report: state.report, showsDescriptions: true)
            stylesSection(document)
            Section {
                Button("metadata.open.cards", systemImage: "photo.badge.plus") { openInCards() }
                    .disabled(document.assetIdentifier == nil)
                if document.location != nil {
                    Button("metadata.open.map", systemImage: "map") { showOnMap() }
                }
            } header: {
                Text("metadata.section.openIn")
            } footer: {
                Text("metadata.section.openIn.footer")
            }
        }
        .formStyle(.grouped)
        .sheet(isPresented: $showingSave) {
            MetadataSaveSheet(document: document) { updateOriginal in
                Task { await state.save(document: document, updateOriginal: updateOriginal) }
            }
#if !os(macOS)
            .presentationDetents([.medium, .large])
#endif
        }
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
                Button {
                    showingSave = true
                } label: {
                    Label("metadata.save", systemImage: "square.and.arrow.down")
                }
                .disabled(state.busy || !state.hasPendingAdd(document))
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
}

private struct MetadataIntroSection: View {
    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 10) {
                Image(systemName: "info.circle.fill")
                    .font(.title)
                    .foregroundStyle(.tint)
                    .frame(width: 56, height: 56)
                    .background(Color.accentColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
                    .accessibilityHidden(true)
                Text("metadata.intro.title")
                    .font(.title2.weight(.bold))
                Text("metadata.intro.description")
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, 8)
        }
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
