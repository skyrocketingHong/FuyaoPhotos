import SwiftUI
import MapKit
import Photos
import Observation
import PhotoMapCore

/// Window-owned state survives compact/regular navigation changes.
@MainActor
@Observable
final class MapSession {
    enum Phase: Equatable { case idle, loading, ready, permissionRequired, failed }

    let library = PhotoLibraryService.shared
    var displayMode = AppSettings.shared.defaultDisplayMode {
        didSet {
            guard oldValue != displayMode else { return }
            AppSettings.shared.defaultDisplayMode = displayMode
            if oldValue == .heatmap { cameraPosition = .region(currentRegion) }
            invalidateResults()
        }
    }
    var options: MapOptions {
        get { AppSettings.shared.mapOptions }
        set { AppSettings.shared.mapOptions = newValue }
    }
    var selectedYear = AppSettings.shared.defaultSelectedYear {
        didSet {
            guard oldValue != selectedYear else { return }
            invalidateResults()
        }
    }
    var cameraPosition: MapCameraPosition = .automatic
    var currentRegion = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 35.8617, longitude: 104.1954),
        span: MKCoordinateSpan(latitudeDelta: 60, longitudeDelta: 60)
    )
    var presentation: MapPresentation?
    private(set) var phase: Phase = .idle
    private(set) var clusters: [MapCluster] = []
    private(set) var visiblePhotoCount = 0
    private(set) var resultRevision: UInt64 = 0
    private(set) var isQuerying = false
    private(set) var queryFailed = false
    private(set) var hasQueryResult = false
    private(set) var availableYears: [Int] = []

    private struct ResultContext {
        let region: MKCoordinateRegion
        let year: Int?
        let indexVersion: UInt64
    }

    @ObservationIgnored private var resultContext: ResultContext?
    @ObservationIgnored private var viewportSize: CGSize = .zero
    @ObservationIgnored private var queryTask: Task<Void, Never>?
    @ObservationIgnored private var generation: UInt64 = 0
    @ObservationIgnored private var didRequestAuthorization = false
    @ObservationIgnored private var didSetInitialCamera = false
    @ObservationIgnored private var isActivating = false
    @ObservationIgnored private var isActive = false

    /// The representable writes a settled heatmap region through this binding.
    var heatmapRegion: MKCoordinateRegion {
        get { currentRegion }
        set {
            guard !Self.sameRegion(currentRegion, newValue) else { return }
            currentRegion = newValue
            cameraPosition = .region(newValue)
            requestQuery()
        }
    }

    func activate() async {
        isActive = true
        displayMode = AppSettings.shared.defaultDisplayMode
        guard !isActivating else { return }
        isActivating = true
        defer { isActivating = false }

        if !didRequestAuthorization {
            phase = .loading
            didRequestAuthorization = true
            _ = await library.requestAuthorization()
        } else {
            await library.refreshAuthorization()
        }
        guard !Task.isCancelled else { return }
        guard hasAuthorization else {
            phase = .permissionRequired
            clearResults()
            return
        }
        // The shared service owns freshness and deduplication, including access-scope changes.
        if phase != .ready { phase = .loading }
        await library.loadPhotoIndex()
        guard !Task.isCancelled else { return }
        guard hasAuthorization else {
            phase = .permissionRequired
            clearResults()
            return
        }
        guard library.errorMessage == nil else {
            phase = .failed
            return
        }
        if !didSetInitialCamera {
            if let region = await library.initialRegion(year: selectedYear) {
                guard !Task.isCancelled else { return }
                currentRegion = region
                cameraPosition = .region(region)
            }
            didSetInitialCamera = true
        }
        phase = .ready
        refreshAvailableYears()
        requestQuery(immediate: true)
    }

    func pause() {
        isActive = false
        cancelQuery()
    }

    func retry() async {
        await activate()
    }

    func indexDidChange() {
        refreshAvailableYears()
        // Downloading an iCloud resource can update the index while its detail is open.
        invalidateResults()
    }

    func authorizationDidChange() {
        guard !hasAuthorization else { return }
        phase = .permissionRequired
        presentation = nil
        clearResults()
    }

    func applySettings() {
        displayMode = AppSettings.shared.defaultDisplayMode
        refreshAvailableYears()
    }

    func fitPhotos() async {
        guard let region = await library.initialRegion(year: selectedYear) else { return }
        currentRegion = region
        cameraPosition = .region(region)
        requestQuery(immediate: true)
    }

    func viewportDidChange(_ size: CGSize) {
        guard size.width > 0, size.height > 0, viewportSize != size else { return }
        viewportSize = size
        requestQuery()
    }

    func cameraDidSettle(_ region: MKCoordinateRegion, camera: MapCamera) {
        guard !Self.sameRegion(currentRegion, region) else { return }
        currentRegion = region
        // Preserve pitch and heading when the navigation shell is rebuilt.
        cameraPosition = .camera(camera)
        requestQuery()
    }

    func select(_ cluster: MapCluster) {
        guard let context = resultContext, context.indexVersion == library.indexVersion,
              clusters.contains(cluster) else {
            requestQuery(immediate: true)
            return
        }
        if cluster.count > 1 {
            presentation = .cluster(ClusterSelection(
                cluster: cluster, region: context.region, year: context.year,
                indexVersion: context.indexVersion
            ))
        } else if let location = library.location(for: cluster.representativeID) {
            presentation = .photo(location)
        }
    }

    func requestQuery(immediate: Bool = false) {
        cancelQuery()
        guard isActive, phase == .ready, viewportSize.width > 0, viewportSize.height > 0 else { return }
        let requestGeneration = generation
        let region = currentRegion
        let year = selectedYear
        let mode = displayMode
        let size = viewportSize
        let indexVersion = library.indexVersion
        isQuerying = true
        queryFailed = false
        queryTask = Task { [weak self] in
            do {
                if !immediate { try await Task.sleep(for: .milliseconds(150)) }
                guard let self else { return }
                let result = try await library.query(in: region, year: year, viewportSize: size, mode: mode)
                try Task.checkCancellation()
                guard generation == requestGeneration, library.indexVersion == indexVersion else { return }
                // Publish context and markers in the same uninterrupted main-actor turn.
                resultContext = ResultContext(region: region, year: year, indexVersion: indexVersion)
                clusters = result.clusters
                visiblePhotoCount = result.totalCount
                resultRevision = result.revision
                hasQueryResult = true
                isQuerying = false
            } catch is CancellationError {
                // A newer request or a scene transition owns the next result.
            } catch {
                guard let self, generation == requestGeneration, !Task.isCancelled else { return }
                queryFailed = true
                isQuerying = false
            }
        }
    }

    private var hasAuthorization: Bool {
        library.authorizationStatus == .authorized || library.authorizationStatus == .limited
    }

    private func refreshAvailableYears() {
        let current = Calendar.current.component(.year, from: Date())
        guard let start = AppSettings.shared.customStartYear ?? library.photosEarliestYear,
              start <= current else {
            availableYears = []
            return
        }
        availableYears = Array((start...current).reversed())
    }

    private func invalidateResults() {
        clearResults()
        requestQuery()
    }

    private func clearResults() {
        cancelQuery()
        resultContext = nil
        clusters = []
        visiblePhotoCount = 0
        hasQueryResult = false
        queryFailed = false
    }

    private func cancelQuery() {
        generation &+= 1
        queryTask?.cancel()
        queryTask = nil
        isQuerying = false
    }

    private static func sameRegion(_ lhs: MKCoordinateRegion, _ rhs: MKCoordinateRegion) -> Bool {
        abs(lhs.center.latitude - rhs.center.latitude) < 0.0000001 &&
        abs(lhs.center.longitude - rhs.center.longitude) < 0.0000001 &&
        abs(lhs.span.latitudeDelta - rhs.span.latitudeDelta) < 0.0000001 &&
        abs(lhs.span.longitudeDelta - rhs.span.longitudeDelta) < 0.0000001
    }
}

struct ClusterSelection: Identifiable {
    let cluster: MapCluster
    let region: MKCoordinateRegion
    let year: Int?
    let indexVersion: UInt64
    var id: String { cluster.id }
}

enum MapPresentation: Identifiable {
    case photo(PhotoLocation)
    case cluster(ClusterSelection)

    var id: String {
        switch self {
        case .photo(let location): "photo:\(location.id)"
        case .cluster(let selection): "cluster:\(selection.id)"
        }
    }
}
