import SwiftUI
import AVKit
import Photos

struct LibraryVideoPreview: View {
    let asset: PHAsset
    @State private var player: AVPlayer?
    @State private var failed = false
    @State private var request: PHImageRequestID?
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if let player { VideoPlayer(player: player) }
            else if failed { ContentUnavailableView("photo.preview.unavailable", systemImage: "video.slash") }
            else { ProgressView("loading.photos") }
        }
        .task {
            let options = PHVideoRequestOptions()
            options.isNetworkAccessAllowed = true
            request = PHImageManager.default().requestPlayerItem(forVideo: asset, options: options) { item, _ in
                Task { @MainActor in
                    if let item { player = AVPlayer(playerItem: item) } else { failed = true }
                }
            }
        }
        .onDisappear {
            player?.pause()
            if let request { PHImageManager.default().cancelImageRequest(request) }
        }
        .onChange(of: scenePhase) { _, phase in if phase != .active { player?.pause() } }
    }
}
