import SwiftUI
import PhotosUI

struct PhotoCardScreen: View {
    @Bindable var session: CardSession
    @State private var preview: CardPreviewState
    @State private var inspector: CardInspectorSelection
    @Environment(PhotoWorkspace.self) private var workspace
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @AppStorage(CardAppearance.storageKey) private var cardAppearance = CardAppearance.system.rawValue
    /// Zoom-transition identity shared by the canvas photo and the fullscreen push.
    @Namespace private var fullScreenZoom
    private var forcedDarkroom: Bool { cardAppearance == CardAppearance.darkroom.rawValue }
    @State private var showingPicker = false
    @State private var showingSave = false
    @State private var saveDetent = PresentationDetent.medium
    @State private var showingReplace = false
    @State private var showingClose = false
    @State private var showingToolbarReplace = false
    @State private var replacementIDs: [String]?
    private var hasUnsavedChanges: Bool { workspace.hasPendingEdits(in: session) }

    @MainActor init(session: CardSession, preview: CardPreviewState? = nil, inspector: CardInspectorSelection? = nil) {
        self.session = session
        _preview = State(initialValue: preview ?? CardPreviewState())
        _inspector = State(initialValue: inspector ?? CardInspectorSelection())
    }

    private var replaceTitle: LocalizedStringKey {
        session.documents.count == 1 ? "card.replace.confirm.one" : "card.replace.confirm.many"
    }

    private var saveActionTitle: LocalizedStringKey {
        session.documents.count == 1 ? "card.save.action.one" : "card.save.action.many"
    }

    var body: some View {
        NavigationStack {
            editorContent
                .modifier(PhotoDepartureOverlay(session: session, page: .cards, imageCapture: {
                    guard var frame = preview.departureFrame else { return nil }
                    if preview.original { frame.overlay = nil }
                    return frame
                }))
                .tint(PhotoPreviewTheme.accent)
                .toolbar { editorToolbar }
#if os(macOS)
                .navigationTitle("tab.cards")
                .navigationSubtitle(session.current?.originalName ?? "")
#endif
                .disabled(session.busy)
                .overlay {
                    ZStack { if session.current != nil { busyOverlay } }
                        .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: session.busy)
                }
        }
        .transformEnvironment(\.colorScheme) { scheme in
            if forcedDarkroom { scheme = .dark }
        }
        .sheet(isPresented: $showingPicker) { pickerSheet }
        .sheet(isPresented: $showingSave) { saveSheet }
        .fileExporter(isPresented: $session.showingFileExporter, items: session.filesForExport, contentTypes: [.jpeg],
            onCompletion: session.completeFileExport, onCancellation: session.discardFileExport)
        .modifier(SessionAlerts(session: session))
        .sensoryFeedback(.success, trigger: session.savedCount) { (_: Int?, newValue: Int?) in newValue != nil }
        .onChange(of: workspace.pendingAssetIDs) { (_: [String]?, _: [String]?) in handlePendingImport() }
        .onChange(of: session.busy) { (_: Bool, busy: Bool) in if !busy { handlePendingImport() } }
        .onChange(of: workspace.openPickerRequest) { _, requested in
            if requested == .cards { workspace.openPickerRequest = nil; choosePhotos() }
        }
        .onChange(of: workspace.saveSheetRequest) { _, requested in
            if requested == .cards, session.current != nil {
                workspace.saveSheetRequest = nil; presentSaveOptions()
            }
        }
        .onChange(of: CardPreferences.shared.resolveLocation) { (_: Bool, enabled: Bool) in
            if !enabled { session.cancelLocationLookup() }
        }
        .onAppear {
            handlePendingImport()
            if workspace.openPickerRequest == .cards { workspace.openPickerRequest = nil; choosePhotos() }
            if workspace.saveSheetRequest == .cards, session.current != nil {
                workspace.saveSheetRequest = nil; presentSaveOptions()
            }
        }
    }

    private struct SessionAlerts: ViewModifier {
        let session: CardSession
        private var errorShown: Binding<Bool> {
            Binding(get: { session.errorMessage != nil }, set: { if !$0 { session.dismissError() } })
        }
        private var savedShown: Binding<Bool> {
            Binding(get: { session.savedCount != nil && session.errorMessage == nil },
                    set: { if !$0 { session.savedCount = nil } })
        }
        func body(content: Content) -> some View {
            content
                .alert("card.error.title", isPresented: errorShown) {
                    Button("done") { session.dismissError() }
                } message: { Text(session.errorMessage ?? "") }
                .alert(session.savedToFiles ? "card.files.complete" : "card.save.complete", isPresented: savedShown) {
                    if !session.savedToFiles {
                        Button("photo.open.application") {
                            Task {
                                if !(await PhotosApplication.open()) { session.errorMessage = String.localized("photo.open.failed") }
                            }
                        }
                    }
                    Button("done", role: .cancel) { session.savedCount = nil }
                } message: {
                    if session.savedToFiles {
                        Text("card.files.saved")
                    } else if session.documents.count > 1, let count = session.savedCount {
                        if count == 1 { Text("card.saved.one") }
                        else { Text("card.saved \(count)") }
                    }
                }
        }
    }

    private var editorContent: some View {
        Group {
            if session.current != nil {
                CardCanvas(session: session, zoom: fullScreenZoom,
                    replaceConfirmation: $showingReplace,
                    closeConfirmation: $showingClose,
                    open: choosePhotos,
                    save: presentSaveOptions,
                    close: closeSessionIfSafe,
                    confirmReplace: replacePhotos,
                    confirmClose: { session.clear() }, preview: preview, inspectorSelection: inspector)
                // The canvas has no vertically scrolling content; a top scroll edge here only
                // hazes the photo without ever reacting to scrolling.
                .scrollEdgeEffectHidden(true, for: .top)
            }
            else {
                PhotoImportPage(title: "tab.cards", description: "card.empty.description", symbol: "photo.badge.plus", busy: session.busy) {
                    PhotoImportAction(title: "card.open", symbol: "photo.badge.plus", prominence: .primary, action: choosePhotos)
                    PhotoImportAction(title: "package.import.action", symbol: "square.and.arrow.down") { workspace.showingPackagePicker = true }
                }
            }
        }
#if !os(macOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackgroundVisibility(.hidden, for: .navigationBar)
        .toolbarVisibility(.hidden, for: .navigationBar)
        .toolbarColorScheme(forcedDarkroom ? .dark : nil, for: .navigationBar)
#endif
    }

    @ToolbarContentBuilder private var editorToolbar: some ToolbarContent {
        if let document = session.current {
            ToolbarItem(placement: .primaryAction) {
                Button("card.open", systemImage: "photo.badge.plus", action: choosePhotosFromToolbar)
                    .labelStyle(.iconOnly).buttonBorderShape(.circle)
#if os(iOS)
                    .keyboardShortcut("o")
#endif
                    .confirmationDialog(replaceTitle, isPresented: $showingToolbarReplace, titleVisibility: .visible) {
                        Button("card.replace", role: .destructive, action: replacePhotos)
                        Button("card.cancel", role: .cancel) { replacementIDs = nil }
                    }
            }
            ToolbarItem(placement: .primaryAction) {
                Button(action: presentSaveOptions) {
                    SaveProgressLabel(title: saveActionTitle, active: session.saving,
                        completed: session.progress, total: session.total, compact: true)
                }
                    .buttonBorderShape(.circle)
#if os(iOS)
                    .keyboardShortcut("s")
#endif
            }
            ToolbarItem(placement: .primaryAction) {
                PhotoCloseButton(session: session)
            }
            ToolbarItem(placement: .primaryAction) {
                Menu("card.more", systemImage: "ellipsis") {
                    Button("package.import.action", systemImage: "square.and.arrow.down") { workspace.showingPackagePicker = true }
                    Button("card.style.reset", systemImage: "arrow.counterclockwise") {
                        document.card.style = PhotoCardStyle()
                    }
                    if let url = document.exportURL, !document.isLive || document.exportIsMotionPhoto {
                        ShareLink(item: url) { Label("card.share", systemImage: "square.and.arrow.up") }
                    }
                }
                .labelStyle(.iconOnly).buttonBorderShape(.circle)
                .menuIndicator(.hidden)
            }
        }
    }

    @ViewBuilder private var busyOverlay: some View {
        if session.busy && !session.saving {
            ProgressView(value: Double(session.progress), total: Double(max(1, session.total))) {
                Text("card.processing \(session.progress) \(session.total)")
            }
            .padding().frame(maxWidth: 300)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
            .transition(.opacity)
        }
    }

    private func openInMetadata(_ document: CardDocument) {
        guard let identifier = document.assetIdentifier else { return }
        workspace.openInMetadata([identifier])
    }

    private func closeSessionIfSafe() {
        if hasUnsavedChanges { showingClose = true } else { session.clear() }
    }

    private var pickerSheet: some View {
        NativePhotoPicker { results in
            showingPicker = false
            Task { await session.open(results) }
        }
        .photoPickerPresentation()
    }

    private var saveSheet: some View {
        CardSaveSheet(photoCount: session.documents.count,
            canUpdate: session.canUpdateOriginals,
            hasHDR: session.documents.contains { $0.metadata.hdr || $0.metadata.hasPortraitData },
            hasLive: session.documents.contains { $0.isLive },
            requiresHEIC: session.documents.contains { $0.metadata.nativeEditingData }) { options in
                Task { await session.save(options: options) }
            }
#if !os(macOS)
            .presentationSizing(.form)
            .presentationDetents([.medium, .large], selection: $saveDetent)
            .presentationContentInteraction(.scrolls)
#endif
    }

    private func choosePhotos() {
        replacementIDs = nil
        if session.current != nil { showingReplace = true } else { showingPicker = true }
    }
    private func choosePhotosFromToolbar() {
        replacementIDs = nil
        if session.current != nil { showingToolbarReplace = true } else { showingPicker = true }
    }
    private func presentSaveOptions() {
        saveDetent = horizontalSizeClass == .regular ? .large : .medium
        showingSave = true
    }
    private func replacePhotos() {
        if let ids = replacementIDs {
            replacementIDs = nil
            Task { await session.openAssets(ids) }
        } else { showingPicker = true }
    }
    private func handlePendingImport() {
        guard !session.busy, let ids = workspace.pendingAssetIDs else { return }
        workspace.pendingAssetIDs = nil
        if session.current != nil { replacementIDs = ids; showingReplace = true }
        else { Task { await session.openAssets(ids) } }
    }
}
