import SwiftUI
import PhotosUI

struct PhotoCardScreen: View {
    @Bindable var session: CardSession
    @Environment(PhotoWorkspace.self) private var workspace
    @AppStorage(CardAppearance.storageKey) private var cardAppearance = CardAppearance.darkroom.rawValue
    private var forcedDarkroom: Bool { cardAppearance != CardAppearance.system.rawValue }
    @State private var showingPicker = false
    @State private var showingSave = false
    @State private var saveDetent = PresentationDetent.medium
    @State private var showingReplace = false
    @State private var showingClose = false
    @State private var showingToolbarReplace = false
    @State private var showingToolbarClose = false
    @State private var showingExternalReplace = false
    @State private var replacementIDs: [String]?

    private var replaceTitle: LocalizedStringKey {
        session.documents.count == 1 ? "card.replace.confirm.one" : "card.replace.confirm.many"
    }

    private var closeTitle: LocalizedStringKey {
        session.documents.count == 1 ? "card.close.confirm.one" : "card.close.confirm.many"
    }

    private var closeActionTitle: LocalizedStringKey {
        session.documents.count == 1 ? "card.close.one" : "card.close.many"
    }

    private var saveActionTitle: LocalizedStringKey {
        session.documents.count == 1 ? "card.save.action.one" : "card.save.action.many"
    }

    var body: some View {
        NavigationStack {
            editorContent
                .toolbar { editorToolbar }
                .disabled(session.busy)
                .overlay { busyOverlay }
        }
        .background(Color.black.ignoresSafeArea())
        .transformEnvironment(\.colorScheme) { scheme in
            if forcedDarkroom { scheme = .dark }
        }
        .tint(.yellow)
        .sheet(isPresented: $showingPicker) { pickerSheet }
        .sheet(isPresented: $showingSave) { saveSheet }
        .confirmationDialog(replaceTitle, isPresented: $showingExternalReplace, titleVisibility: .visible) {
            Button("card.replace", role: .destructive, action: replacePhotos)
            Button("card.cancel", role: .cancel) { replacementIDs = nil }
        }
        .modifier(SessionAlerts(session: session))
        .sensoryFeedback(.success, trigger: session.savedCount) { (_: Int?, newValue: Int?) in newValue != nil }
        .onChange(of: workspace.pendingAssetIDs) { (_: [String]?, _: [String]?) in handlePendingImport() }
        .onChange(of: session.busy) { (_: Bool, busy: Bool) in if !busy { handlePendingImport() } }
        .onChange(of: CardPreferences.shared.resolveLocation) { (_: Bool, enabled: Bool) in
            if !enabled { session.cancelLocationLookup() }
        }
        .onAppear(perform: handlePendingImport)
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
                .alert("card.save.complete", isPresented: savedShown) {
                    Button("photo.open.library") {
                        Task {
                            if !(await PhotosApplication.open()) { session.errorMessage = String.localized("photo.open.failed") }
                        }
                    }
                    Button("done", role: .cancel) { session.savedCount = nil }
                } message: {
                    if session.documents.count > 1, let count = session.savedCount {
                        if count == 1 { Text("card.saved.one") }
                        else { Text("card.saved \(count)") }
                    }
                }
        }
    }

    private var editorContent: some View {
        Group {
            if session.current != nil {
                CardCanvas(session: session,
                    replaceConfirmation: $showingReplace,
                    closeConfirmation: $showingClose,
                    open: choosePhotos,
                    save: presentSaveOptions,
                    close: closeSessionIfSafe,
                    confirmReplace: replacePhotos,
                    confirmClose: { session.clear() })
            }
            else {
                ContentUnavailableView {
                    Label("card.empty.title", systemImage: "photo.badge.plus")
                } description: { Text("card.empty.description") }
                actions: {
                    Button("card.open", action: choosePhotos)
                        .buttonStyle(.borderedProminent)
                        .keyboardShortcut("o")
                }
            }
        }
#if !os(macOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackgroundVisibility(.hidden, for: .navigationBar)
        .scrollEdgeEffectHidden(true, for: .top)
        .toolbarVisibility(.hidden, for: .navigationBar)
        .toolbarColorScheme(forcedDarkroom ? .dark : nil, for: .navigationBar)
#endif
    }

    @ToolbarContentBuilder private var editorToolbar: some ToolbarContent {
        if let document = session.current {
            ToolbarItem(placement: .primaryAction) {
                Button("card.open", systemImage: "photo.badge.plus", action: choosePhotosFromToolbar)
                    .labelStyle(.iconOnly).buttonBorderShape(.circle)
                    .keyboardShortcut("o")
                    .confirmationDialog(replaceTitle, isPresented: $showingToolbarReplace, titleVisibility: .visible) {
                        Button("card.replace", role: .destructive, action: replacePhotos)
                        Button("card.cancel", role: .cancel) { replacementIDs = nil }
                    }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(saveActionTitle, systemImage: "checkmark", action: presentSaveOptions)
                    .labelStyle(.iconOnly).buttonBorderShape(.circle)
                    .keyboardShortcut("s")
            }
            ToolbarItem(placement: .secondaryAction) {
                Menu("card.more", systemImage: "ellipsis") {
                    Button("card.style.reset", systemImage: "arrow.counterclockwise") {
                        document.card.style = PhotoCardStyle()
                    }
                    if let url = document.exportURL, !document.isLive {
                        ShareLink(item: url) { Label("card.share", systemImage: "square.and.arrow.up") }
                    }
                    Button(closeActionTitle, systemImage: "xmark") {
                        if session.hasChanges { showingToolbarClose = true } else { session.clear() }
                    }
                }
                .labelStyle(.iconOnly).buttonBorderShape(.circle)
                .confirmationDialog(closeTitle, isPresented: $showingToolbarClose, titleVisibility: .visible) {
                    Button(closeActionTitle, role: .destructive) { session.clear() }
                }
            }
        }
    }

    @ViewBuilder private var busyOverlay: some View {
        if session.busy {
            ProgressView(value: Double(session.progress), total: Double(max(1, session.total))) {
                Text("card.processing \(session.progress) \(session.total)")
            }
            .padding().frame(maxWidth: 300)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        }
    }

    private func closeSessionIfSafe() {
        if session.hasChanges { showingClose = true } else { session.clear() }
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
        CardSaveSheet(photoCount: session.documents.count,
            canUpdate: session.canUpdateOriginals,
            hasHDR: session.documents.contains { $0.metadata.hdr || $0.metadata.hasPortraitData },
            hasLive: session.documents.contains { $0.isLive }) { options in
                Task { await session.save(options: options) }
            }
#if !os(macOS)
            .presentationDetents([.medium, .large], selection: $saveDetent)
            .presentationContentInteraction(.scrolls)
#endif
    }

    private func choosePhotos() {
        replacementIDs = nil
        if session.hasChanges { showingReplace = true } else { showingPicker = true }
    }
    private func choosePhotosFromToolbar() {
        replacementIDs = nil
        if session.hasChanges { showingToolbarReplace = true } else { showingPicker = true }
    }
    private func presentSaveOptions() {
        saveDetent = .medium
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
        if session.hasChanges { replacementIDs = ids; showingExternalReplace = true }
        else { Task { await session.openAssets(ids) } }
    }
}
