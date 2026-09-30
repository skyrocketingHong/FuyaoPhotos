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
    @State private var copiedDevice: String?
    private let initial: [LensProfile]
    private let store: LensProfileStore

    init(store: LensProfileStore) {
        self.store = store
        initial = store.profiles
        _draft = State(initialValue: store.profiles)
    }

    private var hasChanges: Bool { draft != initial }
    private struct DeviceGroup: Identifiable {
        let id: String
        let profiles: [LensProfile]
        var device: String { profiles[0].device }
        var exifModel: String { profiles[0].exifModel }
    }
    private var deviceGroups: [DeviceGroup] {
        let grouped = Dictionary(grouping: draft) { LensProfile.normalize($0.exifModel) }
        var seen = Set<String>()
        return draft.compactMap { profile in
            let key = LensProfile.normalize(profile.exifModel)
            guard seen.insert(key).inserted, let profiles = grouped[key] else { return nil }
            return DeviceGroup(id: key, profiles: profiles)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("lens.profiles.description")
                        .foregroundStyle(.secondary)
                    Button("lens.add", systemImage: "plus") { editor = LensProfileDraft() }
                    PasteButton(payloadType: String.self, onPaste: importClipboard)
                        .labelStyle(LensPasteLabelStyle())
                        .disabled(transferring)
                    Button("lens.file.import", systemImage: "square.and.arrow.down") { importing = true }
                        .disabled(transferring)
                    Menu("lens.clipboard.copy", systemImage: "doc.on.doc") {
                        ForEach(deviceGroups) { group in Button(group.device) { copyModel(group.id) } }
                    }
                    .disabled(draft.isEmpty || transferring)
                    Menu("lens.file.export", systemImage: "square.and.arrow.up") {
                        ForEach(deviceGroups) { group in Button(group.device) { exportModel(group.id) } }
                    }
                    .disabled(draft.isEmpty || transferring)
                    if let copiedDevice {
                        Label(String(format: String.localized("lens.clipboard.copied"), copiedDevice), systemImage: "checkmark")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                    Text("lens.file.description").font(.footnote).foregroundStyle(.secondary)
                }
                if draft.isEmpty {
                    ContentUnavailableView("lens.empty.title", systemImage: "camera.aperture",
                                           description: Text("lens.empty.description"))
                } else {
                    ForEach(deviceGroups) { group in
                        Section {
                            ForEach(group.profiles) { profile in lensRow(profile) }
                                .onDelete { offsets in
                                    let ids = Set(offsets.compactMap { group.profiles.indices.contains($0) ? group.profiles[$0].id : nil })
                                    draft.removeAll { ids.contains($0.id) }
                                }
                        } header: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(group.device).font(.headline)
                                if LensProfile.normalize(group.device) != group.id { Text(group.exifModel) }
                                Text(group.profiles.count == 1 ? String.localized("lens.group.one")
                                    : String(format: String.localized("lens.group.count"), group.profiles.count))
                            }
                            .textCase(nil).fixedSize(horizontal: false, vertical: true)
                        } footer: {
                            if group.id == deviceGroups.last?.id { Text("lens.profiles.footer") }
                        }
                    }
                }
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .onChange(of: draft) { _, _ in copiedDevice = nil }
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

    private func lensRow(_ profile: LensProfile) -> some View {
        Button { editor = LensProfileDraft(profile: profile) } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 5) {
                    Text(profile.name).font(.headline).foregroundStyle(.primary)
                    Text(equivalentRange(profile)).font(.subheadline).monospacedDigit().foregroundStyle(.secondary)
                }
                .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                Image(systemName: "chevron.forward").font(.caption).foregroundStyle(.tertiary).accessibilityHidden(true)
            }.contentShape(.rect)
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button("lens.edit", systemImage: "pencil") { editor = LensProfileDraft(profile: profile) }
            Button("lens.delete", systemImage: "trash", role: .destructive) { remove(profile) }
        }
        .swipeActions { Button("lens.delete", systemImage: "trash", role: .destructive) { remove(profile) } }
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

    private func copyModel(_ model: String) {
        do {
            let file = try LensProfileFile(profiles: draft.filter { $0.acceptsExif(model) })
            guard let text = String(data: try file.encoded(), encoding: .utf8) else { throw LensProfileFileError.invalid }
#if os(macOS)
            NSPasteboard.general.clearContents()
            guard NSPasteboard.general.setString(text, forType: .string) else { throw LensProfileFileError.invalid }
#else
            UIPasteboard.general.string = text
#endif
            copiedDevice = file.device
        } catch { transferMessage = String.localized("lens.clipboard.copy.failed") }
    }

    private func importClipboard(_ values: [String]) {
        do {
            guard values.count == 1, let text = values.first, !text.isEmpty,
                  text.utf8.count <= LensProfileFile.maximumBytes else { throw LensProfileFileError.invalid }
            receive(try LensProfileFile.decode(Data(text.utf8)))
        } catch { transferMessage = String.localized("lens.clipboard.invalid") }
    }

    private func receive(_ file: LensProfileFile) {
        if draft.contains(where: { $0.acceptsExif(file.exifModel) }) { incoming = file }
        else { apply(file) }
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
                receive(file)
            } catch { transferMessage = String.localized("lens.file.invalid") }
        }
    }

    private func apply(_ file: LensProfileFile) {
        do { draft = try file.merging(into: draft); transferMessage = String.localized("lens.file.imported") }
        catch { transferMessage = String.localized("lens.file.invalid") }
    }
}

private struct LensPasteLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        Label { Text("lens.clipboard.import") } icon: { configuration.icon }
            .labelStyle(.titleAndIcon)
    }
}
