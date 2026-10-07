import SwiftUI
import UniformTypeIdentifiers

struct LensProfilesView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model: LensWorkspaceDraft
    @State private var queuedEditor: LensProfileDraft?
    @State private var confirmEditorReplacement = false
    @State private var confirmDiscard = false
    @State private var saveFailed = false
    @State private var showingImport = false
    @State private var exporting = false
    @State private var exportDocument: LensProfileFileDocument?
    @State private var exportName = "Camera.json"
    @State private var transferMessage: String?
    @State private var transferStatus: String?
    @State private var copiedDevice: String?
    @State private var expandedDevices = Set<String>()
    @State private var inventory: LensCameraInventory?
    @State private var scanning = false
    @State private var bindingProfile: LensProfile?
    @State private var departing: [UUID: DepartingLens] = [:]
    private struct DepartingLens {
        let profile: LensProfile
        let index: Int
        let deadline: ContinuousClock.Instant
    }
    private let hardwareDevice = LensCameraInventory.localHardwareDevice
    private let store: LensProfileStore
    private let embedded: Bool

    init(store: LensProfileStore, workspace: LensWorkspaceDraft? = nil, embedded: Bool = false) {
        self.store = store; self.embedded = embedded
        _model = State(initialValue: workspace ?? LensWorkspaceDraft(profiles: store.profiles))
    }

    private var draft: [LensProfile] { get { model.profiles } nonmutating set { model.profiles = newValue } }
    private var initial: [LensProfile] { get { model.initial } nonmutating set { model.initial = newValue } }
    private var editor: LensEditingSession? { get { model.editor } nonmutating set { model.editor = newValue } }

    private var hasChanges: Bool { draft != initial }
    private var hasPendingChanges: Bool { hasChanges || editor?.hasChanges == true }
    private var deviceGroups: [LensBindings.DeviceGroup] {
        var displayed = draft
        for item in departing.values.sorted(by: { $0.index < $1.index }) where !displayed.contains(where: { $0.id == item.profile.id }) {
            displayed.insert(item.profile, at: min(item.index, displayed.count))
        }
        return LensBindings.groups(displayed, hardwareDevice: hardwareDevice, aliases: inventory?.aliases ?? [])
    }

    var body: some View {
        Group {
            if embedded { page }
            else { NavigationStack { page } }
        }
        .interactiveDismissDisabled(hasPendingChanges)
    }

    private var page: some View {
        GeometryReader { geometry in
            let split = geometry.size.width >= 820
            let pageTitle: LocalizedStringKey = if let editor, !split {
                editor.initial.isNew ? "lens.add" : "lens.edit"
            } else { "lens.profiles.title" }
            Group {
                if let editor, !split {
                    editorView(editor, inline: false)
                } else {
                    HStack(spacing: 0) {
                        profileList
                            .frame(width: editor != nil ? min(360, geometry.size.width * 0.4) : nil)
#if os(macOS)
                            .frame(maxWidth: editor == nil && embedded ? 620 : nil)
#endif
                        if let editor {
                            Divider()
                            editorView(editor, inline: true)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
#if os(macOS)
            .navigationTitle(embedded ? LocalizedStringKey("settings.title") : pageTitle)
#else
            .navigationTitle(pageTitle)
#endif
            .toolbar {
                if editor == nil || split {
                    if !embedded {
                        ToolbarItem(placement: .cancellationAction) {
                            Button(hasPendingChanges ? "cancel" : "done") {
                                if hasPendingChanges { confirmDiscard = true } else { dismiss() }
                            }.keyboardShortcut(.cancelAction)
                        }
                    }
                    ToolbarItemGroup(placement: .primaryAction) {
                        Button("lens.add", systemImage: "plus") { requestEditor(LensProfileDraft()) }
                            .disabled(draft.count >= 64)
                        Menu {
                            Button("lens.hardware.scan", systemImage: "camera") { Task { await scanCameras() } }
                                .disabled(scanning || editor != nil)
                            if embedded && hasPendingChanges {
                                Button("lens.discard", systemImage: "arrow.uturn.backward", role: .destructive) { confirmDiscard = true }
                            }
                        } label: {
                            Label("card.more", systemImage: "ellipsis").labelStyle(.iconOnly)
                        }
                        .menuIndicator(.hidden).buttonBorderShape(.circle)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("lens.save", action: save)
                            .disabled(!hasChanges || scanning || editor?.hasChanges == true || LensBindings.hasDuplicates(draft))
                            .keyboardShortcut("s", modifiers: .command)
                    }
                }
            }
        }
        .task { await scanCameras() }
        .task(id: Set(departing.keys)) {
            guard let deadline = departing.values.map(\.deadline).min() else { return }
            do { try await ContinuousClock().sleep(until: deadline) } catch { return }
            departing = departing.filter { $0.value.deadline > .now }
        }
        .onChange(of: draft) { _, _ in copiedDevice = nil }
        .confirmationDialog("lens.discard.title", isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button("lens.discard", role: .destructive) {
                departing = [:]; draft = initial; editor = nil
                if !embedded { dismiss() }
            }
            Button("lens.keepEditing", role: .cancel) { }
        } message: { Text("lens.discard.message") }
        .confirmationDialog("lens.discard.title", isPresented: $confirmEditorReplacement, titleVisibility: .visible) {
            Button("lens.discard", role: .destructive) {
                if let queuedEditor { editor = LensEditingSession(draft: queuedEditor) }
                queuedEditor = nil
            }
            Button("lens.keepEditing", role: .cancel) { queuedEditor = nil }
        } message: { Text("lens.editor.discard.message") }
        .alert("lens.save.failed", isPresented: $saveFailed) { Button("done", role: .cancel) { } }
        .sheet(isPresented: $showingImport) {
            LensImportView(profiles: draft, apply: apply)
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
            .presentationSizing(.form)
        }
        .fileExporter(isPresented: $exporting, document: exportDocument, contentType: .json, defaultFilename: exportName) { result in
            if case .success = result { transferStatus = String.localized("lens.file.exported") }
            else if case .failure(let error) = result, (error as NSError).code != NSUserCancelledError {
                transferMessage = String.localized("lens.file.export.failed")
            }
        }
        .alert("lens.profiles.title", isPresented: Binding(get: { transferMessage != nil }, set: { if !$0 { transferMessage = nil } })) {
            Button("done", role: .cancel) { transferMessage = nil }
        } message: { Text(transferMessage ?? "") }
    }

    private var profileList: some View {
        Form {
            Section {
                PhotoPageIntro(title: "lens.profiles.title", description: "lens.profiles.description", symbol: "camera.aperture")
            }
            Section {
                Button("lens.import.configuration", systemImage: "square.and.arrow.down") { showingImport = true }
                    .photoActionStyle(.secondary)
                    .disabled(editor?.hasChanges == true)
                if hasChanges {
                    Label("lens.list.unsaved", systemImage: "pencil.circle")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                if scanning { ProgressView("lens.hardware.scanning") }
                if let transferStatus {
                    Label(transferStatus, systemImage: "checkmark").font(.footnote).foregroundStyle(.secondary)
                }
            } footer: { Text("lens.profiles.footer") }
            if draft.isEmpty && departing.isEmpty {
                Section {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("lens.empty.title", systemImage: "camera.aperture").font(.headline)
                        Text("lens.empty.description").foregroundStyle(.secondary)
                        Button("lens.add", systemImage: "plus") { requestEditor(LensProfileDraft()) }
                            .photoActionStyle(.primary)
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.vertical, 8)
                }
            }
            ForEach(deviceGroups) { group in
                Section {
                    DisclosureGroup(isExpanded: Binding(get: { expandedDevices.contains(group.id) }, set: { expanded in
                        if expanded { expandedDevices.insert(group.id) } else { expandedDevices.remove(group.id) }
                    })) {
                        if LensProfile.normalize(group.device) != group.id {
                            Text(group.exifModel).font(.subheadline).foregroundStyle(.secondary)
                        }
                        Menu("lens.device.actions", systemImage: "ellipsis") {
                            Button("lens.device.addLens", systemImage: "plus") { addLens(to: group) }
                                .disabled(draft.count >= 64)
                            Divider()
                            Button("lens.clipboard.copy", systemImage: "doc.on.doc") { copyModel(group.id) }
                            Button("lens.file.export", systemImage: "square.and.arrow.up") { exportModel(group.id) }
                            if !group.isCurrent {
                                Divider()
                                Button("lens.hardware.thisDevice", systemImage: "checkmark.circle") {
                                    draft = draft.map { value in
                                        var value = value
                                        if value.acceptsExif(group.exifModel) { value.hardwareModel = hardwareDevice; value.hardwareDevice = nil }
                                        return value
                                    }
                                    reconcile()
                                }.disabled(hardwareDevice.isEmpty)
                            }
                        }
                        .disabled(editor?.hasChanges == true)
                        if copiedDevice == group.device {
                            Label(String(format: String.localized("lens.clipboard.copied"), group.device), systemImage: "checkmark")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                        ForEach(group.profiles) { profile in
                            DissolvingRow(removed: !draft.contains(where: { $0.id == profile.id }),
                                onFinished: {
                                    withAnimation(reduceMotion ? nil : .smooth(duration: 0.18)) {
                                        departing[profile.id] = nil
                                    }
                                }) { lensRow(profile) }
                        }
                            .onDelete { offsets in
                                let ids = Set(offsets.compactMap { group.profiles.indices.contains($0) ? group.profiles[$0].id : nil })
                                if let editor, ids.contains(editor.id) { self.editor = nil }
                                for profile in group.profiles where ids.contains(profile.id) { remove(profile) }
                            }
                            .deleteDisabled(editor != nil)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(group.device).font(.headline).foregroundStyle(.primary)
                            if group.isCurrent {
                                Label("lens.group.current", systemImage: "checkmark.circle.fill")
                                    .font(.subheadline).foregroundStyle(.tint)
                            }
                            Text(group.profiles.count == 1 ? String.localized("lens.group.one")
                                : String(format: String.localized("lens.group.count"), group.profiles.count))
                                .font(.footnote).foregroundStyle(.secondary)
                        }.fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .photoPageForm()
    }

    private func editorView(_ session: LensEditingSession, inline: Bool) -> some View {
        LensProfileEditor(session: session, inline: inline) { profile in
            model.apply(profile)
            expandedDevices.insert(LensProfile.normalize(profile.exifModel))
            reconcile()
        } close: { editor = nil }
    }

    private func requestEditor(_ value: LensProfileDraft) {
        guard editor?.id != value.id else { return }
        if editor?.hasChanges == true { queuedEditor = value; confirmEditorReplacement = true }
        else { editor = LensEditingSession(draft: value) }
    }

    private func addLens(to group: LensBindings.DeviceGroup) {
        var value = LensProfileDraft()
        value.device = group.device
        value.exifModel = group.exifModel
        value.hardwareModel = group.profiles.first?.hardwareDevice ?? group.profiles.first?.hardwareModel
        requestEditor(value)
    }

    private func equivalentRange(_ profile: LensProfile) -> String {
        let low = profile.equivalentMin.formatted(.number.precision(.fractionLength(0...2)))
        let high = profile.equivalentMax.formatted(.number.precision(.fractionLength(0...2)))
        return profile.equivalentMin == profile.equivalentMax ? "\(low) mm" : "\(low)-\(high) mm"
    }

    private func lensRow(_ profile: LensProfile) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Button { requestEditor(LensProfileDraft(profile: profile)) } label: {
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
                Menu {
                    Button("lens.edit", systemImage: "pencil") { requestEditor(LensProfileDraft(profile: profile)) }
                    Button("lens.hardware.bind", systemImage: "link") { bindingProfile = profile }
                        .disabled(scanning || editor != nil)
                    Button("lens.delete", systemImage: "trash", role: .destructive) { remove(profile) }
                        .disabled(editor != nil)
                } label: {
                    Label("lens.row.actions", systemImage: "ellipsis")
                        .labelStyle(.iconOnly)
                        .modifier(PhotoActionForeground())
                }
                .menuIndicator(.hidden).buttonBorderShape(.circle)
            }
            if let id = profile.cameraID, !id.isEmpty {
                Text(String(format: String.localized(profile.hardwareDevice == hardwareDevice ? "lens.hardware.bound" : "lens.hardware.hint"), id))
                    .font(.footnote).foregroundStyle(.secondary).textSelection(.enabled)
            }
        }
        .contextMenu {
            Button("lens.edit", systemImage: "pencil") { requestEditor(LensProfileDraft(profile: profile)) }
            Button("lens.delete", systemImage: "trash", role: .destructive) { remove(profile) }.disabled(editor != nil)
        }
        .swipeActions { Button("lens.delete", systemImage: "trash", role: .destructive) { remove(profile) }.disabled(editor != nil) }
    }

    private func remove(_ profile: LensProfile) {
        guard let index = draft.firstIndex(where: { $0.id == profile.id }), editor == nil else { return }
        if !reduceMotion {
            if departing.count >= 3, let oldest = departing.min(by: { $0.value.deadline < $1.value.deadline })?.key {
                departing[oldest] = nil
            }
            departing[profile.id] = DepartingLens(profile: profile, index: index,
                deadline: .now.advanced(by: TelegramDustView.lifetime + .milliseconds(120)))
        }
        draft.removeAll { $0.id == profile.id }
    }

    private func save() {
        do {
            try store.save(draft); initial = draft
            transferStatus = String.localized("lens.save.complete")
            if !embedded { dismiss() }
        }
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

    private func apply(_ file: LensProfileFile) {
        do {
            try model.importConfiguration(file)
            reconcile(); showingImport = false
            expandedDevices.insert(LensProfile.normalize(file.exifModel))
            transferStatus = String.localized("lens.file.imported")
        }
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
