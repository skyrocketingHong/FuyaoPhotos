import SwiftUI
import MapKit
import Photos
import PhotoMapCore

struct MapContainerView: View {
    let session: MapSession
    let scope: Namespace.ID
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ZStack {
            if session.phase == .ready {
                MapCanvas(session: session, scope: scope)
                MapStatusOverlay(session: session)
            } else {
                MapLoadingState(session: session)
            }
        }
        .onGeometryChange(for: CGSize.self) { proxy in proxy.size } action: { size in
            session.viewportDidChange(size)
        }
    }
}

private struct MapCanvas: View {
    @Bindable var session: MapSession
    let scope: Namespace.ID
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Group {
            if session.displayMode == .heatmap {
                HeatmapView(
                    region: $session.heatmapRegion, clusters: session.clusters,
                    options: session.options
                )
            } else {
                PhotoClusterMap(session: session, scope: scope)
            }
        }
        .environment(\.colorScheme, session.options.appearance.colorScheme ?? colorScheme)
        .ignoresSafeArea()
    }
}

private struct PhotoClusterMap: View {
    @Bindable var session: MapSession
    let scope: Namespace.ID
    @State private var selectedClusterID: String?

    var body: some View {
        Map(position: $session.cameraPosition, selection: $selectedClusterID, scope: scope) {
            ForEach(session.clusters) { cluster in
                if session.displayMode == .photo {
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: cluster.latitude, longitude: cluster.longitude)) {
                        Button {
                            session.select(cluster)
                        } label: {
                            LightweightPhotoAnnotation(
                                assetID: cluster.representativeID, count: cluster.count,
                                indexVersion: session.library.indexVersion,
                                thumbnails: session.library.thumbnails
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text("map.cluster.open \(cluster.count)"))
                    }
                } else {
                    Marker(
                        "", monogram: Text(cluster.count, format: .number),
                        coordinate: CLLocationCoordinate2D(latitude: cluster.latitude, longitude: cluster.longitude)
                    )
                    .tint(.red)
                    .tag(cluster.id)
                }
            }
        }
        .mapStyle(session.options.swiftUIStyle)
        .mapControls {
            if session.options.scale { MapScaleView() }
        }
        .onMapCameraChange(frequency: .onEnd) { context in
            session.cameraDidSettle(context.region, camera: context.camera)
        }
        .onChange(of: selectedClusterID) { _, id in
            if let id, let cluster = session.clusters.first(where: { $0.id == id }) {
                session.select(cluster)
            }
            selectedClusterID = nil
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
            } else if session.hasQueryResult && session.visiblePhotoCount == 0 {
                Label("map.empty.region", systemImage: "mappin.slash")
            }
            if session.library.authorizationStatus == .limited {
                Text("permission.photo.library.limited")
                    .foregroundStyle(.secondary)
            }
            }
            .font(.caption)
            .padding(session.queryFailed || session.visiblePhotoCount == 0 || session.library.authorizationStatus == .limited ? 10 : 0)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        }
        .padding()
    }
}
