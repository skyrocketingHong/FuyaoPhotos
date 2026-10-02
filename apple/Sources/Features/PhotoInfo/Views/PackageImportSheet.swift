import SwiftUI
import UniformTypeIdentifiers
import ImageIO
@preconcurrency import Photos

struct PackageImportRequest: Identifiable {
    let id = UUID()
    let url: URL
}

struct PackageImportPresentation: ViewModifier {
    @Bindable var workspace: PhotoWorkspace
    func body(content: Content) -> some View {
        content
            .fileImporter(isPresented: $workspace.showingPackagePicker, allowedContentTypes: [.fuyaoPhotosPackage]) { result in
                if case .success(let url) = result { workspace.importPackage(url) }
            }
            .sheet(item: $workspace.incomingPackage, onDismiss: workspace.nextPackage) { request in
                PackageImportSheet(request: request)
            }
            .onOpenURL { url in if url.isFileURL && url.pathExtension.lowercased() == FuyaoPhotosPackage.fileExtension { workspace.importPackage(url) } }
    }
}

struct PackageImportSheet: View {
    let request: PackageImportRequest
    @Environment(\.dismiss) private var dismiss
    @State private var contents: FuyaoPhotosPackage.Contents?
    @State private var thumbnail: CGImage?
    @State private var folder: URL?
    @State private var loading = true
    @State private var saving = false
    @State private var saved = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                if let thumbnail {
                    Section { HDRImageView(image: thumbnail, enabled: true).aspectRatio(4 / 3, contentMode: .fit) }
                }
                Section {
                    LabeledContent("package.import.file", value: request.url.lastPathComponent)
                        .textSelection(.enabled).fixedSize(horizontal: false, vertical: true)
                    if contents != nil { Label("Live Photo", systemImage: "livephoto") }
                    Text("package.import.description").foregroundStyle(.secondary)
                    if loading { ProgressView("package.import.checking") }
                    if let error { Text(error).foregroundStyle(.red) }
                    if saved {
                        Label("package.import.saved", systemImage: "checkmark")
                        Button("photo.open.application") { Task { _ = await PhotosApplication.open() } }
                    }
                }
            }
            .photoPageForm()
            .navigationTitle("package.import.action")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(saved ? "done" : "cancel") { dismiss() }.disabled(saving) }
                ToolbarItem(placement: .confirmationAction) {
                    if !saved {
                        Button { Task { await save() } } label: {
                            SaveProgressLabel(title: "package.import.save", active: saving)
                        }.disabled(loading || saving || contents == nil)
                    }
                }
            }
        }
        .interactiveDismissDisabled(saving)
        .task(id: request.id) { await prepare() }
        .onDisappear { if let folder { try? FileManager.default.removeItem(at: folder) } }
#if os(macOS)
        .frame(minWidth: 720, idealWidth: 960, minHeight: 560, idealHeight: 760)
        .presentationSizing(.fitted)
#else
        .presentationSizing(.page)
#endif
    }

    private func prepare() async {
        let url = request.url
        let work = FileManager.default.temporaryDirectory.appendingPathComponent("FuyaoPackage-\(UUID().uuidString)")
        let operation = Task.detached(priority: .userInitiated) {
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            try FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)
            do {
                let contents = try FuyaoPhotosPackage.read(url, into: work)
                try await FuyaoPhotosPackage.validateLivePhoto(contents)
                try Task.checkCancellation()
                return contents
            } catch { try? FileManager.default.removeItem(at: work); throw error }
        }
        do {
            let value = try await withTaskCancellationHandler { try await operation.value } onCancel: { operation.cancel() }
            guard !Task.isCancelled else { try? FileManager.default.removeItem(at: work); return }
            folder = work; contents = value
            thumbnail = await Task.detached(priority: .userInitiated) {
                guard let source = CGImageSourceCreateWithURL(value.photo as CFURL, nil) else { return nil as CGImage? }
                return CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceCreateThumbnailWithTransform: true, kCGImageSourceThumbnailMaxPixelSize: 1200,
                    kCGImageSourceDecodeRequest: kCGImageSourceDecodeToHDR,
                    kCGImageSourceGenerateImageSpecificLumaScaling: true] as CFDictionary)
            }.value
        } catch { if !Task.isCancelled { self.error = CardError.invalidPackage.localizedDescription } }
        loading = false
    }

    private func save() async {
        guard let contents, !saving else { return }
        saving = true; error = nil
        defer { saving = false }
        do {
            let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
            guard status == .authorized || status == .limited else { throw CardError.permission }
            let type: UTType = contents.manifest.photo.fileExtension == "heic" ? .heic : .jpeg
            try await PHPhotoLibrary.shared().performChanges {
                let asset = PHAssetCreationRequest.forAsset()
                let photo = PHAssetResourceCreationOptions(); photo.contentType = type
                photo.originalFilename = "FuyaoPhoto." + contents.manifest.photo.fileExtension
                asset.addResource(with: .photo, fileURL: contents.photo, options: photo)
                let movie = PHAssetResourceCreationOptions(); movie.contentType = .quickTimeMovie; movie.originalFilename = "FuyaoPhoto.mov"
                asset.addResource(with: .pairedVideo, fileURL: contents.movie, options: movie)
            }
            saved = true
        } catch { self.error = CardSession.saveError(error, stage: .librarySave).localizedDescription }
    }
}
