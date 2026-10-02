import SwiftUI
import MapKit
import Photos
import PhotoMapCore

struct MapContainerView: View {
    let session: MapSession
    let scope: Namespace.ID
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            if session.phase == .ready {
                MapCanvas(session: session, scope: scope)
                    .transition(.opacity)
                MapStatusOverlay(session: session)
            } else {
                MapLoadingState(session: session)
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.24), value: session.phase)
        .onGeometryChange(for: CGSize.self) { proxy in proxy.size } action: { size in
            session.viewportDidChange(size)
        }
    }
}

private struct MapCanvas: View {
    @Bindable var session: MapSession
    let scope: Namespace.ID
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var heatmap = HeatmapSurface()

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if session.displayMode == .heatmap {
                    HeatmapView(
                        map: heatmap.map, region: $session.heatmapRegion, clusters: session.clusters,
                        options: session.options, showsUserLocation: session.location.authorized
                    )
                    .transition(.opacity)
                } else {
                    PhotoClusterMap(session: session, scope: scope)
                        .transition(.opacity)
                }
            }
            // Expand the real map, then restore the safe area used by its attribution.
            .safeAreaPadding(EdgeInsets(top: geometry.safeAreaInsets.top,
                leading: geometry.safeAreaInsets.leading, bottom: geometry.safeAreaInsets.bottom + 8,
                trailing: geometry.safeAreaInsets.trailing))
            .ignoresSafeArea(.container)
        }
        .overlay(alignment: .top) {
            MapHeader(count: session.hasQueryResult ? session.visiblePhotoCount : nil,
                      reservesNativeScale: reservesNativeScale) {
                if session.displayMode == .heatmap {
                    HeatmapControls(map: heatmap.map, options: session.options)
                } else {
                    if session.options.scale {
                        MapScaleView(alignment: .trailing, scope: scope).mapControlVisibility(.visible)
                            .modifier(MapScaleContentAlignment())
                    }
                    if session.options.compass {
                        MapCompass(scope: scope).mapControlVisibility(.visible)
                    }
                }
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.24), value: session.displayMode == .heatmap)
        .environment(\.colorScheme, session.options.appearance.colorScheme ?? colorScheme)
    }

    private var reservesNativeScale: Bool {
#if os(macOS)
        // macOS has no separate MKScaleView. Its heatmap scale stays in MapKit's top-leading slot.
        session.displayMode == .heatmap && session.options.scale
#else
        false
#endif
    }
}

private struct PhotoClusterMap: View {
    @Bindable var session: MapSession
    let scope: Namespace.ID
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var presented: [PresentedMapCluster] = []

    var body: some View {
        Map(position: $session.cameraPosition, scope: scope) {
            if session.location.authorized { UserAnnotation() }
            ForEach(presented) { entry in
                Annotation("", coordinate: CLLocationCoordinate2D(latitude: entry.cluster.latitude,
                    longitude: entry.cluster.longitude), anchor: entry.mode == .photo ? .center : .bottom) {
                    Button { session.select(entry.cluster) } label: {
                        AnimatedMapAnnotation(entry: entry, indexVersion: session.library.indexVersion,
                            thumbnails: session.library.thumbnails)
                    }
                    .buttonStyle(.plain)
                    .disabled(entry.removalTime != nil)
                    .accessibilityHidden(entry.removalTime != nil)
                    .accessibilityLabel(Text("map.cluster.open \(entry.cluster.count)"))
                }
            }
        }
        .mapStyle(session.options.swiftUIStyle)
        .mapControls { }
        .onMapCameraChange(frequency: .onEnd) { context in
            guard session.displayMode != .heatmap else { return }
            session.cameraDidSettle(context.region, camera: context.camera)
        }
        .task(id: MapClusterDisplaySnapshot(clusters: session.clusters, mode: session.displayMode, reduceMotion: reduceMotion)) {
            guard session.displayMode != .heatmap else { return }
            let next = session.clusters.map { PresentedMapCluster(cluster: $0, mode: session.displayMode) }
            let keys = Set(next.map(\.id))
            let now = ContinuousClock.now
            let retired = reduceMotion ? [] : presented.compactMap { previous -> PresentedMapCluster? in
                guard !keys.contains(previous.id) else { return nil }
                var entry = previous
                entry.removalTime = entry.removalTime ?? now.advanced(by: .milliseconds(200))
                return entry.removalTime! > now ? entry : nil
            }
            presented = retired + next
            while let deadline = presented.compactMap(\.removalTime).min() {
                do { try await ContinuousClock().sleep(until: deadline) }
                catch { return }
                guard !Task.isCancelled else { return }
                let currentTime = ContinuousClock.now
                presented.removeAll { $0.removalTime.map { $0 <= currentTime } == true }
            }
        }
    }
}

private struct MapLoadingState: View {
    let session: MapSession
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 16) {
            switch session.phase {
            case .permissionRequired:
                ContentUnavailableView {
                    Label("permission.photo.library", systemImage: "photo.badge.exclamationmark")
                } description: {
                    Text("permission.photo.library.description")
                } actions: {
                    Button("action.retry") { Task { await session.retry() } }
                }
            case .failed:
                ContentUnavailableView {
                    Label("error.loading.failed", systemImage: "exclamationmark.triangle")
                } description: {
                    Text("error.library.retry")
                } actions: {
                    Button("action.retry") { Task { await session.retry() } }
                }
            default:
                VStack(spacing: 10) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.title)
                        .symbolEffect(.pulse, isActive: !reduceMotion)
                    Text("loading.photos")
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct MapStatusOverlay: View {
    let session: MapSession

    var body: some View {
        VStack {
            Spacer()
            VStack(spacing: 6) {
            if session.queryFailed || session.library.errorMessage != nil {
                HStack {
                    Label("error.map.query", systemImage: "exclamationmark.triangle")
                        .font(.subheadline)
                    Button("action.retry") { Task { await session.retry() } }
                }
            }
            if session.library.authorizationStatus == .limited {
                Text("permission.photo.library.limited")
                    .foregroundStyle(.secondary)
            }
            }
            .font(.caption)
            .padding(session.queryFailed || session.library.errorMessage != nil || session.library.authorizationStatus == .limited ? 10 : 0)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        }
        .padding()
    }
}
