import SwiftUI
import PhotosUI
import Photos
import AVFoundation

struct CardLivePhotoPreview: View {
    let resources: [URL]
    let movieURL: URL
    let durationLoaded: (TimeInterval?) -> Void
    let playbackStarted: (Date) -> Void
    let finished: () -> Void
    @State private var photo: PHLivePhoto?
    @State private var failed = false
    @State private var requestID: PHLivePhotoRequestID?
    @State private var active = false
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            if let photo { LivePhotoSurface(photo: photo, started: playbackStarted, finished: finished) }
            else if failed {
                ContentUnavailableView {
                    Label("photo.preview.unavailable", systemImage: "livephoto.slash")
                } actions: { Button("done", action: finished) }
            } else {
                VStack(spacing: 8) {
                    Image(systemName: "livephoto")
                        .font(.title2)
                        .symbolEffect(.pulse, isActive: !reduceMotion)
                    Text("loading.photos").font(.footnote)
                }
                .foregroundStyle(.white)
            }
        }
        .task {
            active = true
            requestID = PHLivePhoto.request(withResourceFileURLs: resources, placeholderImage: nil,
                targetSize: CGSize(width: 1600, height: 1600), contentMode: .aspectFit) { result, info in
                guard (info[PHLivePhotoInfoIsDegradedKey] as? Bool) != true else { return }
                Task { @MainActor in
                    guard active else { return }
                    photo = result; failed = result == nil
                }
            }
        }
        .task(id: movieURL) {
            do {
                let duration = try await AVURLAsset(url: movieURL).load(.duration)
                guard !Task.isCancelled else { return }
                let seconds = duration.seconds
                durationLoaded(duration.isValid && seconds.isFinite && seconds > 0 ? seconds : nil)
            } catch {
                guard !Task.isCancelled else { return }
                durationLoaded(nil)
            }
        }
        .onDisappear {
            active = false
            if let requestID { PHLivePhoto.cancelRequest(withRequestID: requestID) }
        }
        .onChange(of: scenePhase) { _, phase in if phase != .active { finished() } }
    }
}

@MainActor private final class LivePlaybackDelegate: NSObject, PHLivePhotoViewDelegate {
    var started: (Date) -> Void
    var finished: () -> Void
    init(started: @escaping (Date) -> Void, finished: @escaping () -> Void) {
        self.started = started
        self.finished = finished
    }
    func livePhotoView(_ livePhotoView: PHLivePhotoView, willBeginPlaybackWith playbackStyle: PHLivePhotoViewPlaybackStyle) {
        started(.now)
    }
    func livePhotoView(_ livePhotoView: PHLivePhotoView, didEndPlaybackWith playbackStyle: PHLivePhotoViewPlaybackStyle) {
        finished()
    }
}

#if os(macOS)
private struct LivePhotoSurface: NSViewRepresentable {
    let photo: PHLivePhoto
    let started: (Date) -> Void
    let finished: () -> Void
    func makeCoordinator() -> LivePlaybackDelegate { LivePlaybackDelegate(started: started, finished: finished) }
    func makeNSView(context: Context) -> PHLivePhotoView {
        let view = PHLivePhotoView(); view.delegate = context.coordinator; return view
    }
    func updateNSView(_ view: PHLivePhotoView, context: Context) {
        context.coordinator.started = started
        context.coordinator.finished = finished
        guard view.livePhoto !== photo else { return }
        view.livePhoto = photo; view.startPlayback(with: .full)
    }
    static func dismantleNSView(_ view: PHLivePhotoView, coordinator: LivePlaybackDelegate) {
        view.delegate = nil; view.stopPlayback()
    }
}
#else
private struct LivePhotoSurface: UIViewRepresentable {
    let photo: PHLivePhoto
    let started: (Date) -> Void
    let finished: () -> Void
    func makeCoordinator() -> LivePlaybackDelegate { LivePlaybackDelegate(started: started, finished: finished) }
    func makeUIView(context: Context) -> PHLivePhotoView {
        let view = PHLivePhotoView(); view.contentMode = .scaleAspectFit; view.delegate = context.coordinator; return view
    }
    func updateUIView(_ view: PHLivePhotoView, context: Context) {
        context.coordinator.started = started
        context.coordinator.finished = finished
        guard view.livePhoto !== photo else { return }
        view.livePhoto = photo; view.startPlayback(with: .full)
    }
    static func dismantleUIView(_ view: PHLivePhotoView, coordinator: LivePlaybackDelegate) {
        view.delegate = nil; view.stopPlayback()
    }
}
#endif
