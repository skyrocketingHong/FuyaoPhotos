import SwiftUI
import PhotosUI

struct PhotoCardScreen: View {
    @Bindable var session: CardSession
    @Environment(PhotoWorkspace.self) private var workspace
    @State private var showingPicker = false
    @State private var showingSave = false
    @State private var showingReplace = false
    @State private var showingClose = false
    @State private var replacementIDs: [String]?

    var body: some View {
        NavigationStack {
            Group {
                if session.current != nil {
                    CardCanvas(session:session,open:choosePhotos,save:{ showingSave=true },close:{
                        if session.hasChanges { showingClose=true } else { session.clear() }
                    })
                }
                else {
                    ContentUnavailableView {
                        Label("card.empty.title", systemImage: "photo.badge.plus")
                    } description: { Text("card.empty.description") }
                    actions: { Button("card.open", action: choosePhotos).buttonStyle(.borderedProminent) }
                }
            }
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackgroundVisibility(.hidden, for: .navigationBar)
            .scrollEdgeEffectHidden(true, for: .top)
            .toolbarVisibility(session.current == nil ? .visible : .hidden, for: .navigationBar)
            .toolbarColorScheme(.dark,for:.navigationBar)
#endif
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("card.open", systemImage: "photo.badge.plus", action: choosePhotos)
                        .labelStyle(.iconOnly).buttonBorderShape(.circle)
                        .keyboardShortcut("o")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("card.save", systemImage: "checkmark") { showingSave = true }
                        .labelStyle(.iconOnly).buttonBorderShape(.circle)
                        .keyboardShortcut("s").disabled(session.documents.isEmpty)
                }
                ToolbarItem(placement: .secondaryAction) {
                    Menu("card.more", systemImage: "ellipsis") {
                        Button("card.style.reset", systemImage: "arrow.counterclockwise") { session.current?.card.style = PhotoCardStyle() }
                        if let url = session.current?.exportURL, session.current?.isLive == false {
                            ShareLink(item: url) { Label("card.share", systemImage: "square.and.arrow.up") }
                        }
                        Button("card.close", systemImage: "xmark") {
                            if session.hasChanges { showingClose = true } else { session.clear() }
                        }
                    }
                    .labelStyle(.iconOnly).buttonBorderShape(.circle)
                    .disabled(session.documents.isEmpty)
                }
            }
            .disabled(session.busy)
            .overlay {
                if session.busy {
                    ProgressView(value: Double(session.progress), total: Double(max(1, session.total))) {
                        Text("card.processing \(session.progress) \(session.total)")
                    }
                    .padding().frame(maxWidth: 300)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                }
            }
        }
        .background(Color.black.ignoresSafeArea())
        .environment(\.colorScheme,.dark)
        .tint(.yellow)
        .sheet(isPresented: $showingPicker) {
            NativePhotoPicker { results in
                showingPicker = false
                Task { await session.open(results) }
            }
#if os(macOS)
            .frame(minWidth: 680, idealWidth: 820, minHeight: 520, idealHeight: 620)
            .presentationSizing(.fitted)
#endif
        }
        .sheet(isPresented: $showingSave) {
            CardSaveSheet(canUpdate: session.canUpdateOriginals,
                hasHDR: session.documents.contains { $0.metadata.hdr || $0.metadata.hasPortraitData },
                hasLive: session.documents.contains { $0.isLive }) { options in Task { await session.save(options: options) } }
        }
        .confirmationDialog("card.replace.confirm", isPresented: $showingReplace, titleVisibility: .visible) {
            Button("card.replace", role: .destructive, action: replacePhotos)
            Button("card.cancel", role: .cancel) { replacementIDs = nil }
        }
        .confirmationDialog("card.close.confirm", isPresented: $showingClose, titleVisibility: .visible) {
            Button("card.close", role: .destructive) { session.clear() }
        }
        .alert("card.error.title", isPresented: Binding(get: { session.errorMessage != nil }, set: { if !$0 { session.dismissError() } })) {
            Button("done") { session.dismissError() }
        } message: { Text(session.errorMessage ?? "") }
        .alert("card.save.complete", isPresented: Binding(get: { session.savedCount != nil && session.errorMessage == nil }, set: { if !$0 { session.savedCount = nil } })) {
            Button("photo.open.library") {
                Task {
                    if !(await PhotosApplication.open()) { session.errorMessage = String.localized("photo.open.failed") }
                }
            }
            Button("done", role: .cancel) { session.savedCount = nil }
        } message: { Text("card.saved \(session.savedCount ?? 0)") }
        .sensoryFeedback(.success, trigger: session.savedCount) { _, count in count != nil }
        .onChange(of: workspace.pendingAssetIDs) { _, _ in handlePendingImport() }
        .onChange(of: session.busy) { _, busy in if !busy { handlePendingImport() } }
        .onChange(of: CardPreferences.shared.resolveLocation) { _, enabled in
            if !enabled { session.cancelLocationLookup() }
        }
        .onAppear(perform: handlePendingImport)
    }

    private func choosePhotos() {
        replacementIDs = nil
        if session.hasChanges { showingReplace = true } else { showingPicker = true }
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
        if session.hasChanges { replacementIDs = ids; showingReplace = true }
        else { Task { await session.openAssets(ids) } }
    }
}
