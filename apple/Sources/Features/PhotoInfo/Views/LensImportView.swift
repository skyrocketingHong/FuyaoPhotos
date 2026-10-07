import SwiftUI
import UniformTypeIdentifiers

struct LensImportView: View {
    let profiles: [LensProfile]
    let apply: (LensProfileFile) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var importing = false
    @State private var loading = false
    @State private var file: LensProfileFile?
    @State private var preview: [LensProfile] = []
    @State private var confirmsReplacement = false
    @State private var errorMessage: String?
    @State private var readTask: Task<Void, Never>?

    private var replacingCount: Int {
        guard let file else { return 0 }
        return profiles.filter { $0.acceptsExif(file.exifModel) }.count
    }
    private var hasCapacity: Bool { profiles.count - replacingCount + preview.count <= 64 }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    PhotoPageIntro(title: "lens.import.configuration", description: "lens.import.description",
                        symbol: "square.and.arrow.down")
                }
                Section {
                    Button("lens.file.import", systemImage: "folder") { importing = true }
                        .photoActionStyle(.secondary)
                    PasteButton(payloadType: String.self, onPaste: paste)
                        .labelStyle(LensImportPasteLabelStyle())
                        .photoActionStyle(.secondary)
                    if loading { ProgressView("lens.import.reading") }
                } footer: { Text("lens.file.description") }
                .disabled(loading)
                if let file {
                    Section {
                        LabeledContent("lens.device") { Text(file.device).textSelection(.enabled) }
                        LabeledContent("lens.exifModel") { Text(file.exifModel).textSelection(.enabled) }
                        LabeledContent("lens.import.lensCount", value: preview.count.formatted())
                    } header: { Text("lens.import.preview") }
                    Section {
                        ForEach(preview) { profile in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(profile.name)
                                Text(profile.importSummary).font(.subheadline).foregroundStyle(.secondary)
                                    .monospacedDigit()
                            }
                            .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    Section {
                        Button(replacingCount > 0 ? "lens.import.replaceDraft" : "lens.import.addDraft") {
                            if replacingCount > 0 { confirmsReplacement = true }
                            else { addToDraft() }
                        }
                        .photoActionStyle(.primary)
                        .disabled(loading || !hasCapacity)
                        .confirmationDialog("lens.file.replace.title", isPresented: $confirmsReplacement, titleVisibility: .visible) {
                            Button("lens.file.replace", role: .destructive, action: addToDraft)
                            Button("cancel", role: .cancel) {}
                        } message: {
                            Text(String(format: String.localized("lens.file.replace.message"), file.device))
                        }
                    } footer: {
                        if !hasCapacity { Text("lens.import.capacity") }
                        else if replacingCount > 0 {
                            Text(String(format: String.localized("lens.import.replacingCount"), replacingCount))
                        } else { Text("lens.import.draftOnly") }
                    }
                }
            }
            .photoPageForm()
            .navigationTitle("lens.import.configuration")
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("cancel", role: .cancel) { dismiss() } }
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json], allowsMultipleSelection: false) { result in
            if case .success(let urls) = result, let url = urls.first { read(url) }
            else if case .failure(let error) = result, (error as NSError).code != NSUserCancelledError {
                errorMessage = String.localized("lens.file.invalid")
            }
        }
        .alert("lens.import.configuration", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("done", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
        .onDisappear { readTask?.cancel() }
        .presentationSizing(.form)
#if os(macOS)
        .frame(minWidth: 520, idealWidth: 580, minHeight: 460, idealHeight: 620)
#endif
    }

    private func paste(_ values: [String]) {
        file = nil
        preview = []
        do {
            guard values.count == 1, let text = values.first, !text.isEmpty,
                  text.utf8.count <= LensProfileFile.maximumBytes else { throw LensProfileFileError.invalid }
            try receive(LensProfileFile.decode(Data(text.utf8)))
        } catch { errorMessage = String.localized("lens.clipboard.invalid") }
    }

    private func read(_ url: URL) {
        readTask?.cancel()
        file = nil
        preview = []
        loading = true
        readTask = Task {
            defer { loading = false }
            do {
                let value = try await Task.detached(priority: .userInitiated) {
                    let scope = url.startAccessingSecurityScopedResource()
                    defer { if scope { url.stopAccessingSecurityScopedResource() } }
                    let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                    guard size > 0, size <= LensProfileFile.maximumBytes else { throw LensProfileFileError.invalid }
                    return try LensProfileFile.decode(Data(contentsOf: url))
                }.value
                try Task.checkCancellation()
                try receive(value)
            } catch is CancellationError { }
            catch { if !Task.isCancelled { errorMessage = String.localized("lens.file.invalid") } }
        }
    }

    private func receive(_ value: LensProfileFile) throws {
        let lenses = try value.profiles()
        file = value
        preview = lenses
    }

    private func addToDraft() {
        guard let file else { return }
        do {
            _ = try file.merging(into: profiles)
            apply(file)
            dismiss()
        } catch { errorMessage = String.localized("lens.file.invalid") }
    }
}

private struct LensImportPasteLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        Label { Text("lens.clipboard.import") } icon: { configuration.icon }
            .labelStyle(.titleAndIcon)
    }
}

private extension LensProfile {
    var importSummary: String {
        let lower = equivalentMin.formatted(.number.precision(.fractionLength(0...2)))
        let upper = equivalentMax.formatted(.number.precision(.fractionLength(0...2)))
        let focal = equivalentMin == equivalentMax ? "\(lower) mm" : "\(lower)-\(upper) mm"
        return focal + " · " + String.localized(facing.titleKey)
    }
}
