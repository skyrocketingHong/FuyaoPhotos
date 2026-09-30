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
    @State private var expandedDevices = Set<String>()
    @State private var inventory: LensCameraInventory?
    @State private var scanning = false
    @State private var bindingProfile: LensProfile?
    private let hardwareDevice = LensCameraInventory.localHardwareDevice
    private let initial: [LensProfile]
    private let store: LensProfileStore

    init(store: LensProfileStore) {
        self.store = store
        initial = store.profiles
        _draft = State(initialValue: store.profiles)
    }

    private var hasChanges: Bool { draft != initial }
    private var deviceGroups: [LensBindings.DeviceGroup] {
        LensBindings.groups(draft, hardwareDevice: hardwareDevice, aliases: inventory?.aliases ?? [])
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
                    Button("lens.hardware.scan", systemImage: "camera") { Task { await scanCameras() } }
                        .disabled(scanning)
                    if scanning { ProgressView() }
                    Text("lens.hardware.description").font(.footnote).foregroundStyle(.secondary)
                }
                if draft.isEmpty {
                    ContentUnavailableView("lens.empty.title", systemImage: "camera.aperture",
                                           description: Text("lens.empty.description"))
                } else {
                    ForEach(deviceGroups) { group in
                        Section {
                            DisclosureGroup(isExpanded: Binding(get: { expandedDevices.contains(group.id) }, set: { expanded in
                                if expanded { expandedDevices.insert(group.id) } else { expandedDevices.remove(group.id) }
                            })) {
                                if LensProfile.normalize(group.device) != group.id {
                                    Text(group.exifModel).font(.subheadline).foregroundStyle(.secondary)
                                }
                                ForEach(group.profiles) { profile in lensRow(profile) }
                                    .onDelete { offsets in
                                        let ids = Set(offsets.compactMap { group.profiles.indices.contains($0) ? group.profiles[$0].id : nil })
                                        draft.removeAll { ids.contains($0.id) }
                                    }
                                if !group.isCurrent {
                                    Button("lens.hardware.thisDevice") {
                                        draft = draft.map { value in
                                            var value = value
                                            if value.acceptsExif(group.exifModel) { value.hardwareModel = hardwareDevice; value.hardwareDevice = nil }
                                            return value
                                        }
                                        reconcile()
                                    }.disabled(hardwareDevice.isEmpty)
                                }
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(group.device).font(.headline).foregroundStyle(.primary)
                                    if group.isCurrent { Text("lens.group.current").font(.subheadline).foregroundStyle(.secondary) }
                                    Text(group.profiles.count == 1 ? String.localized("lens.group.one")
                                        : String(format: String.localized("lens.group.count"), group.profiles.count))
                                        .font(.footnote).foregroundStyle(.secondary)
                                }.fixedSize(horizontal: false, vertical: true)
                            }
                        } footer: {
                            if group.id == deviceGroups.last?.id { Text("lens.profiles.footer") }
                        }
                    }
                }
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .onChange(of: draft) { _, _ in copiedDevice = nil }
            .task { await scanCameras() }
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
                        .disabled(!hasChanges || LensBindings.hasDuplicates(draft))
                        .keyboardShortcut("s", modifiers: .command)
                }
            }
            .sheet(item: $editor) { value in
                LensProfileEditor(draft: value) { profile in
                    if let index = draft.firstIndex(where: { $0.id == profile.id }) {
                        var profile = profile
                        if !draft[index].acceptsExif(profile.exifModel) { profile.hardwareDevice = nil; profile.hardwareModel = nil }
                        draft[index] = profile
                    }
                    else { draft.append(profile) }
                    reconcile()
                }
            }
            .sheet(item: $bindingProfile) { profile in
                LensHardwareBindingView(profile: profile, profiles: draft, hardware: inventory?.lenses ?? [], hardwareDevice: hardwareDevice) { cameraID in
                    do {
                        if let cameraID {
                            draft = try LensBindings.bind(draft, profileID: profile.id, cameraID: cameraID,
                                hardware: inventory?.lenses ?? [], hardwareDevice: hardwareDevice)
                        } else {
                            draft = draft.map { value in
                                var value = value
                                if value.id == profile.id { value.cameraID = nil; value.hardwareDevice = nil }
                                return value
                            }
                        }
                    } catch { transferMessage = String.localized("lens.hardware.invalid") }
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
        VStack(alignment: .leading, spacing: 8) {
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
            }.buttonStyle(.plain)
            Button("lens.hardware.bind", systemImage: "link") { bindingProfile = profile }
                .buttonStyle(.borderless).disabled(scanning)
            if let id = profile.cameraID, !id.isEmpty {
                Text(String(format: String.localized(profile.hardwareDevice == hardwareDevice ? "lens.hardware.bound" : "lens.hardware.hint"), id))
                    .font(.footnote).foregroundStyle(.secondary).textSelection(.enabled)
            }
        }
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
        do { draft = try file.merging(into: draft); reconcile(); transferMessage = String.localized("lens.file.imported") }
        catch { transferMessage = String.localized("lens.file.invalid") }
    }

    private func reconcile() {
        guard let inventory, !hardwareDevice.isEmpty else { return }
        draft = LensBindings.reconcile(draft, hardware: inventory.lenses, hardwareDevice: hardwareDevice, aliases: inventory.aliases)
    }

    private func scanCameras() async {
        scanning = true
        let result = await Task.detached(priority: .utility) { LensCameraInventory.scan() }.value
        guard !Task.isCancelled else { scanning = false; return }
        inventory = result; scanning = false
        reconcile()
    }
}

private struct LensPasteLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        Label { Text("lens.clipboard.import") } icon: { configuration.icon }
            .labelStyle(.titleAndIcon)
    }
}
