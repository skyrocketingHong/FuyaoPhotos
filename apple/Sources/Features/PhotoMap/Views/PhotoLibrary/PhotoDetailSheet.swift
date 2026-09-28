import SwiftUI
import Photos

struct PhotoDetailSheet: View {
    let location: PhotoLocation
    let thumbnails: PhotoThumbnailStore
    let indexVersion: UInt64
    let addCard: (String) -> Void
    var embedded = false
    var onClose: (() -> Void)?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            PhotoDetailContent(location: location, thumbnails: thumbnails, indexVersion: indexVersion,
                               addCard: addCard, compactLayout: embedded)
                .navigationTitle("photo.detail.title")
#if !os(macOS)
                .navigationBarTitleDisplayMode(.inline)
#endif
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("card.close", systemImage: "xmark") {
                            if let onClose { onClose() } else { dismiss() }
                        }
                            .buttonBorderShape(.circle)
                    }
                }
        }
#if os(macOS)
        .frame(minWidth: embedded ? nil : 660, idealWidth: embedded ? nil : 820,
               minHeight: embedded ? nil : 520, idealHeight: embedded ? nil : 800)
#else
        .presentationDetents([.large])
#endif
    }
}

struct PhotoDetailContent: View {
    let location: PhotoLocation
    let thumbnails: PhotoThumbnailStore
    let indexVersion: UInt64
    let addCard: (String) -> Void
    var compactLayout = false
    @State private var document: CardDocument?
    @State private var failed = false
    @State private var attempt = 0
    @State private var cannotOpenPhotos = false

    var body: some View {
        content
            .task(id: PhotoDetailLoadKey(id: location.id, attempt: attempt)) { await load() }
            .alert("photo.open.asset.failed", isPresented: $cannotOpenPhotos) { Button("done", role: .cancel) {} }
    }

    private var content: some View {
        GeometryReader { geometry in
            let metrics = PhotoPreviewMetrics(available: geometry.size)
            if metrics.isWide && !compactLayout {
                HStack(alignment: .top, spacing: 0) {
                    previewStage(metrics)
                    Form {
                        PhotoDetailInformation(asset: location.asset, document: document, coordinate: location.coordinate)
                    }
                    .photoPageForm().scrollContentBackground(.hidden)
                }
            } else {
                Form {
                    PhotoDetailInformation(asset: location.asset, document: document, coordinate: location.coordinate)
                }
                .photoPageForm()
                .scrollContentBackground(.hidden)
                .safeAreaBar(edge: .top, spacing: 0) { previewStage(metrics).frame(maxWidth: .infinity) }
            }
        }
        .background { PhotoWorkspaceBackdrop(sourceURL: document?.sourceURL) }
    }

    @ViewBuilder private func previewStage(_ metrics: PhotoPreviewMetrics) -> some View {
        if let document {
            OriginalPhotoSummary(document: document, metrics: metrics, showsFileSummary: false) { photoActionButtons }
        } else {
            PhotoPreviewStage(metrics: metrics,
                imageAspectRatio: CGFloat(location.asset.pixelWidth) / CGFloat(max(1, location.asset.pixelHeight))) {
                preview
            } accessories: {
                PhotoPreviewActionRow { photoActionButtons }
            }
        }
    }

    @ViewBuilder private var preview: some View {
        if location.asset.mediaType == .video {
            LibraryVideoPreview(asset: location.asset)
        } else if failed {
            ContentUnavailableView {
                Label("photo.preview.unavailable", systemImage: "photo.badge.exclamationmark")
            } description: { Text("photo.preview.retry.description") }
            actions: { Button("action.retry") { attempt += 1 } }
        } else {
            ProgressView("loading.photos").frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    @ViewBuilder private var photoActionButtons: some View {
        CircularIconButton("photo.open.library", systemImage: "photo.on.rectangle") {
            Task { cannotOpenPhotos = !(await PhotosApplication.open(assetIdentifier: location.id)) }
        }
        if location.asset.mediaType == .image {
            CircularIconButton("metadata.open.cards", systemImage: "photo.badge.plus") { addCard(location.id) }
        }
    }

    private func load() async {
        guard location.asset.mediaType == .image, document == nil else { return }
        failed = false
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("FuyaoDetail-\(UUID().uuidString)")
        do {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let resources = try await PhotoSourceLoader.load(location.asset, into: folder)
            let metadata = try await CardImageProcessor.shared.read(resources.image, author: "")
            try Task.checkCancellation()
            let value = CardDocument(resources: resources, metadata: metadata)
            value.card = PhotoCard()
            document = value
        } catch {
            try? FileManager.default.removeItem(at: folder)
            if !Task.isCancelled { failed = true }
        }
    }
}

private struct PhotoDetailLoadKey: Hashable { let id: String; let attempt: Int }

struct MediaTypeLabel: View {
    let mediaType: PHAssetMediaType
    var body: some View {
        switch mediaType {
        case .image: Text("media.type.image")
        case .video: Text("media.type.video")
        case .audio: Text("media.type.audio")
        default: Text("media.type.unknown")
        }
    }
}
