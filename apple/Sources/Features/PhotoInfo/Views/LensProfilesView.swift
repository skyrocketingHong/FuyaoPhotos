import SwiftUI
import UniformTypeIdentifiers

struct LensProfilesView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draft: [LensProfile]
    @State private var editor: LensProfileDraft?
    @State private var confirmDiscard = false
    @State private var saveFailed = false
    @State private var importing = false
    @State private var transferring = false
    @State private var exporting = false
    @State private var exportDocument: LensProfileFileDocument?
    @State private var exportName = "Camera.json"
    @State private var incoming: LensProfileFile?
    @State private var transferMessage: String?
    private let initial: [LensProfile]
    private let store: LensProfileStore

    init(store: LensProfileStore) {
        self.store = store
        initial = store.profiles
        _draft = State(initialValue: store.profiles)
    }

    private var hasChanges: Bool { draft != initial }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("lens.profiles.description")
                        .foregroundStyle(.secondary)
                    Button("lens.add", systemImage: "plus") { editor = LensProfileDraft() }
                    Button("lens.file.import", systemImage: "square.and.arrow.down") { importing = true }
                        .disabled(transferring)
                    Menu("lens.file.export", systemImage: "square.and.arrow.up") {
                        ForEach(Array(Set(draft.map { LensProfile.normalize($0.exifModel) })).sorted(), id: \.self) { model in
                            Button(draft.first(where: { $0.acceptsExif(model) })?.device ?? model) { exportModel(model) }
                        }
                    }
                    .disabled(draft.isEmpty || transferring)
                    Text("lens.file.description").font(.footnote).foregroundStyle(.secondary)
                }
                if draft.isEmpty {
                    ContentUnavailableView("lens.empty.title", systemImage: "camera.aperture",
                                           description: Text("lens.empty.description"))
                } else {
                    Section {
                        ForEach(draft) { profile in
                            Button { editor = LensProfileDraft(profile: profile) } label: {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 5) {
                                        Text(profile.name).font(.headline).foregroundStyle(.primary)
                                        Text(profile.device).foregroundStyle(.secondary)
                                        Text(profile.exifModel).font(.subheadline).foregroundStyle(.secondary)
                                        Text(equivalentRange(profile)).font(.subheadline).monospacedDigit()
                                            .foregroundStyle(.secondary)
                                    }
                                    .fixedSize(horizontal: false, vertical: true)
                                    Spacer(minLength: 8)
                                    Image(systemName: "chevron.forward").font(.caption).foregroundStyle(.tertiary)
                                        .accessibilityHidden(true)
                                }
                                .contentShape(.rect)
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button("lens.edit", systemImage: "pencil") { editor = LensProfileDraft(profile: profile) }
                                Button("lens.delete", systemImage: "trash", role: .destructive) { remove(profile) }
                            }
                            .swipeActions {
                                Button("lens.delete", systemImage: "trash", role: .destructive) { remove(profile) }
                            }
                        }
                        .onDelete { draft.remove(atOffsets: $0) }
                    } header: {
                        Text("lens.profiles.saved")
                    } footer: {
                        Text("lens.profiles.footer")
                    }
                }
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .navigationTitle("lens.profiles.title")
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(hasChanges ? "cancel" : "done") { if hasChanges { confirmDiscard = true } else { dismiss() } }
                        .keyboardShortcut(.cancelAction)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("lens.save") { save() }
                        .disabled(!hasChanges)
                        .keyboardShortcut("s", modifiers: .command)
                }
            }
            .sheet(item: $editor) { value in
                LensProfileEditor(draft: value) { profile in
                    if let index = draft.firstIndex(where: { $0.id == profile.id }) { draft[index] = profile }
                    else { draft.append(profile) }
                }
            }
            .confirmationDialog("lens.discard.title", isPresented: $confirmDiscard, titleVisibility: .visible) {
                Button("lens.discard", role: .destructive) { dismiss() }
                Button("lens.keepEditing", role: .cancel) { }
            } message: {
                Text("lens.discard.message")
            }
            .alert("lens.save.failed", isPresented: $saveFailed) { Button("done", role: .cancel) { } }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json], allowsMultipleSelection: false) { result in
                if case .success(let urls) = result, let url = urls.first { importModel(url) }
                else if case .failure(let error) = result, (error as NSError).code != NSUserCancelledError {
                    transferMessage = String.localized("lens.file.invalid")
                }
            }
            .fileExporter(isPresented: $exporting, document: exportDocument, contentType: .json, defaultFilename: exportName) { result in
                if case .failure(let error) = result, (error as NSError).code != NSUserCancelledError {
                    transferMessage = String.localized("lens.file.export.failed")
                }
            }
            .confirmationDialog("lens.file.replace.title", isPresented: Binding(get: { incoming != nil }, set: { if !$0 { incoming = nil } }), titleVisibility: .visible) {
                Button("lens.file.replace", role: .destructive) { if let incoming { apply(incoming) }; incoming = nil }
                Button("cancel", role: .cancel) { incoming = nil }
            } message: {
                if let incoming { Text(String(format: String.localized("lens.file.replace.message"), incoming.device)) }
            }
            .alert("lens.profiles.title", isPresented: Binding(get: { transferMessage != nil }, set: { if !$0 { transferMessage = nil } })) {
                Button("done", role: .cancel) { transferMessage = nil }
            } message: { Text(transferMessage ?? "") }
        }
        .interactiveDismissDisabled(hasChanges)
#if os(macOS)
        .frame(minWidth: 520, idealWidth: 640, minHeight: 480, idealHeight: 640)
#endif
    }

    private func equivalentRange(_ profile: LensProfile) -> String {
        let low = profile.equivalentMin.formatted(.number.precision(.fractionLength(0...2)))
        let high = profile.equivalentMax.formatted(.number.precision(.fractionLength(0...2)))
        return profile.equivalentMin == profile.equivalentMax ? "\(low) mm" : "\(low)–\(high) mm"
    }

    private func remove(_ profile: LensProfile) { draft.removeAll { $0.id == profile.id } }

    private func save() {
        do { try store.save(draft); dismiss() }
        catch { saveFailed = true }
    }

    private func exportModel(_ model: String) {
        do {
            let file = try LensProfileFile(profiles: draft.filter { $0.acceptsExif(model) })
            exportDocument = try LensProfileFileDocument(file); exportName = file.filename; exporting = true
        } catch { transferMessage = String.localized("lens.file.invalid") }
    }

    private func importModel(_ url: URL) {
        transferring = true
        Task {
            defer { transferring = false }
            do {
                let file = try await Task.detached(priority: .userInitiated) {
                    let scope = url.startAccessingSecurityScopedResource()
                    defer { if scope { url.stopAccessingSecurityScopedResource() } }
                    let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                    guard size > 0 && size <= LensProfileFile.maximumBytes else { throw LensProfileFileError.invalid }
                    return try LensProfileFile.decode(Data(contentsOf: url))
                }.value
                if draft.contains(where: { $0.acceptsExif(file.exifModel) }) { incoming = file }
                else { apply(file) }
            } catch { transferMessage = String.localized("lens.file.invalid") }
        }
    }

    private func apply(_ file: LensProfileFile) {
        do { draft = try file.merging(into: draft); transferMessage = String.localized("lens.file.imported") }
        catch { transferMessage = String.localized("lens.file.invalid") }
    }
}
