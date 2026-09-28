import SwiftUI
import MapKit
import PhotoMapCore

struct PresentedMapCluster: Identifiable {
    let cluster: MapCluster
    let mode: MapDisplayMode
    var removalTime: ContinuousClock.Instant?
    var id: String { mode.rawValue + ":" + cluster.id }
}

struct MapClusterDisplaySnapshot: Equatable {
    let clusters: [MapCluster]
    let mode: MapDisplayMode
    let reduceMotion: Bool
}

struct AnimatedMapAnnotation: View {
    let entry: PresentedMapCluster
    let indexVersion: UInt64
    let thumbnails: PhotoThumbnailStore
    @State private var appeared = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let visible = appeared && entry.removalTime == nil
        Group {
            if entry.mode == .photo {
                LightweightPhotoAnnotation(assetID: entry.cluster.representativeID, count: entry.cluster.count,
                    indexVersion: indexVersion, thumbnails: thumbnails)
            } else {
                NativeClusterMarker(count: entry.cluster.count).frame(width: 48, height: 56)
            }
        }
        .opacity(visible || (reduceMotion && entry.removalTime == nil) ? 1 : 0)
        .scaleEffect(reduceMotion || visible ? 1 : 0.94, anchor: .bottom)
        .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: visible)
        .onAppear { appeared = true }
    }
}

#if os(iOS)
private struct NativeClusterMarker: UIViewRepresentable {
    let count: Int
    func makeUIView(context: Context) -> MKMarkerAnnotationView {
        let view = MKMarkerAnnotationView(annotation: MKPointAnnotation(), reuseIdentifier: nil)
        view.isUserInteractionEnabled = false
        return view
    }
    func updateUIView(_ view: MKMarkerAnnotationView, context: Context) { configure(view) }
    private func configure(_ view: MKMarkerAnnotationView) {
        view.markerTintColor = .systemRed
        view.glyphText = count.formatted(.number.notation(.compactName))
        view.titleVisibility = .hidden
        view.subtitleVisibility = .hidden
        view.prepareForDisplay()
    }
}
#else
private struct NativeClusterMarker: NSViewRepresentable {
    let count: Int
    func makeNSView(context: Context) -> MKMarkerAnnotationView {
        MKMarkerAnnotationView(annotation: MKPointAnnotation(), reuseIdentifier: nil)
    }
    func updateNSView(_ view: MKMarkerAnnotationView, context: Context) {
        view.markerTintColor = .systemRed
        view.glyphText = count.formatted(.number.notation(.compactName))
        view.titleVisibility = .hidden
        view.subtitleVisibility = .hidden
        view.prepareForDisplay()
    }
}
#endif
