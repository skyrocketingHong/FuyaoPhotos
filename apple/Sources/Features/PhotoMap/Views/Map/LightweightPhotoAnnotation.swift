import SwiftUI

/// Each annotation owns only its current image; the shared store bounds work and memory.
struct LightweightPhotoAnnotation: View {
    let assetID: String
    let count: Int
    let indexVersion: UInt64
    let thumbnails: PhotoThumbnailStore

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            AssetThumbnail(
                assetID: assetID, pointSize: 64, indexVersion: indexVersion,
                thumbnails: thumbnails
            )
            if count > 1 {
                Text(count, format: .number)
                    .font(.caption.bold())
                    .foregroundStyle(.white)
                    .monospacedDigit()
                    .padding(5)
                    .background(.black.opacity(0.7), in: RoundedRectangle(cornerRadius: 5))
                    .padding(3)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(.white, lineWidth: 2))
        .frame(minWidth: 44, minHeight: 44)
        .contentShape(Rectangle())
    }
}

struct ThumbnailRequest: Hashable {
    let assetID: String
    let indexVersion: UInt64
    let pixelSize: Int
    var attempt = 0
}

struct ThumbnailTaskIdentity: Hashable {
    let request: ThumbnailRequest
    let isActive: Bool
}

struct AssetThumbnail: View {
    let assetID: String
    let pointSize: CGFloat
    let indexVersion: UInt64
    let thumbnails: PhotoThumbnailStore
    @Environment(\.displayScale) private var displayScale
    @Environment(\.scenePhase) private var scenePhase
    @State private var image: NativeImage?
    @State private var loadedRequest: ThumbnailRequest?

    var body: some View {
        let request = ThumbnailRequest(
            assetID: assetID, indexVersion: indexVersion,
            pixelSize: max(1, Int((pointSize * displayScale).rounded(.up)))
        )
        ZStack {
            Rectangle().fill(.quaternary)
            if loadedRequest == request, let image {
                NativePhotoImage(image: image)
                    .scaledToFill()
            } else {
                Image(systemName: "photo")
                    .foregroundStyle(.secondary)
            }
        }
        .frame(width: pointSize, height: pointSize)
        .clipped()
        .accessibilityHidden(true)
        .task(id: ThumbnailTaskIdentity(request: request, isActive: scenePhase == .active)) {
            guard scenePhase == .active else { return }
            guard loadedRequest != request || image == nil else { return }
            image = nil
            loadedRequest = nil
            do {
                let result = try await thumbnails.image(for: request.assetID, pixelSize: request.pixelSize)
                try Task.checkCancellation()
                image = result
                loadedRequest = request
            } catch {
                // Missing, offline, or cancelled assets keep the lightweight placeholder.
            }
        }
    }
}

struct NativePhotoImage: View {
    let image: NativeImage

    var body: some View {
#if os(macOS)
        Image(nsImage: image).resizable()
#else
        Image(uiImage: image).resizable()
#endif
    }
}
