import SwiftUI
import Photos

struct PhotoDetailSheet: View {
    let location: PhotoLocation
    let thumbnails: PhotoThumbnailStore
    let indexVersion: UInt64
    let addCard: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            PhotoDetailContent(location: location, thumbnails: thumbnails, indexVersion: indexVersion, addCard: addCard)
                .navigationTitle("photo.detail.title")
#if !os(macOS)
                .navigationBarTitleDisplayMode(.inline)
#endif
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("card.close", systemImage: "xmark", action: dismiss.callAsFunction)
                            .buttonBorderShape(.circle)
                    }
                }
        }
#if os(macOS)
        .frame(minWidth: 660, idealWidth: 820, minHeight: 520, idealHeight: 800)
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
    @State private var document: CardDocument?
    @State private var failed = false
    @State private var attempt = 0
    @State private var cannotOpenPhotos = false

    var body: some View {
        content
            .task(id: PhotoDetailLoadKey(id: location.id, attempt: attempt)) { await load() }
            .alert("photo.open.failed", isPresented: $cannotOpenPhotos) { Button("done", role: .cancel) {} }
    }

    @ViewBuilder private var content: some View {
#if os(macOS)
        HSplitView {
            VStack(alignment: .leading, spacing: 12) {
                preview
                    .aspectRatio(4 / 3, contentMode: .fit)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(.black)

                HStack(alignment: .center, spacing: 12) {
                    photoName
                    Spacer(minLength: 8)
                    photoActions
                }
            }
            .padding(16)
            .frame(minWidth: 360)

            List {
                PhotoDetailInformation(location: location, metadata: document?.metadata)
            }
            .listStyle(.inset)
            .frame(minWidth: 260, idealWidth: 320)
        }
#else
        List {
            Section {
                preview
                    .aspectRatio(4 / 3, contentMode: .fit)
                    .listRowInsets(EdgeInsets())
                HStack(alignment: .center, spacing: 12) {
                    photoName
                    Spacer(minLength: 8)
                    photoActions
                }
            }
            PhotoDetailInformation(location: location, metadata: document?.metadata)
        }
        .listStyle(.plain)
#endif
    }

    @ViewBuilder private var preview: some View {
        if location.asset.mediaType == .video {
            LibraryVideoPreview(asset: location.asset)
        } else if let document {
            CardPreview(document: document)
        } else if failed {
            ContentUnavailableView {
                Label("photo.preview.unavailable", systemImage: "photo.badge.exclamationmark")
            } description: { Text("photo.preview.retry.description") }
            actions: { Button("action.retry") { attempt += 1 } }
        } else {
            ProgressView("loading.photos").frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    @ViewBuilder private var photoName: some View {
        if let document {
            VStack(alignment: .leading, spacing: 4) {
                Text(document.originalName)
                    .font(.headline)
                    .textSelection(.enabled)
                Text(document.sourceURL.pathExtension.uppercased())
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var photoActions: some View {
        HStack(spacing: 0) {
            CircularIconButton("photo.open.library", systemImage: "photo.on.rectangle") {
                Task { cannotOpenPhotos = !(await PhotosApplication.open()) }
            }
            if location.asset.mediaType == .image {
                CircularIconButton("photo.add.card", systemImage: "photo.badge.plus") {
                    addCard(location.id)
                }
            }
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
