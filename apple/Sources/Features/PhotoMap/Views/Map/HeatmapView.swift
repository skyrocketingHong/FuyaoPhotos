import SwiftUI
import MapKit
import PhotoMapCore

final class HeatmapSurface {
    lazy var map = MKMapView()
}

struct HeatmapView: View {
    let map: MKMapView
    @Binding var region: MKCoordinateRegion
    let clusters: [MapCluster]
    let options: MapOptions
    let showsUserLocation: Bool

    var body: some View {
        HeatmapPlatformView(map: map, region: $region, clusters: clusters, options: options, showsUserLocation: showsUserLocation)
    }
}

#if os(macOS)
private struct HeatmapPlatformView: NSViewRepresentable {
    let map: MKMapView
    @Binding var region: MKCoordinateRegion
    let clusters: [MapCluster]
    let options: MapOptions
    let showsUserLocation: Bool
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeCoordinator() -> HeatmapCoordinator { HeatmapCoordinator(region: $region) }
    func makeNSView(context: Context) -> MKMapView { context.coordinator.configure(map); return map }
    func updateNSView(_ map: MKMapView, context: Context) {
        context.coordinator.update(map, region: $region, clusters: clusters, options: options,
            showsUserLocation: showsUserLocation, animated: context.transaction.animation != nil,
            reduceMotion: reduceMotion)
        map.appearance = NSAppearance(named: colorScheme == .dark ? .darkAqua : .aqua)
    }
    static func dismantleNSView(_ map: MKMapView, coordinator: HeatmapCoordinator) {
        coordinator.stop()
        map.delegate = nil
        map.showsUserLocation = false
    }
}
#else
private struct HeatmapPlatformView: UIViewRepresentable {
    let map: MKMapView
    @Binding var region: MKCoordinateRegion
    let clusters: [MapCluster]
    let options: MapOptions
    let showsUserLocation: Bool
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeCoordinator() -> HeatmapCoordinator { HeatmapCoordinator(region: $region) }
    func makeUIView(context: Context) -> MKMapView { context.coordinator.configure(map); return map }
    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.update(map, region: $region, clusters: clusters, options: options,
            showsUserLocation: showsUserLocation, animated: context.transaction.animation != nil,
            reduceMotion: reduceMotion)
        map.overrideUserInterfaceStyle = colorScheme == .dark ? .dark : .light
    }
    static func dismantleUIView(_ map: MKMapView, coordinator: HeatmapCoordinator) {
        coordinator.stop()
        map.delegate = nil
        map.showsUserLocation = false
    }
}
#endif

private final class HeatmapCoordinator: NSObject, MKMapViewDelegate {
    var region: Binding<MKCoordinateRegion>
    private var rendered: [MapCluster] = []
    private let densityTransition = HeatmapLayerTransition()
    private var lastInput: MKCoordinateRegion?
    private var applyingRegion = false
    private var interacting = false
    private var lastOptions: MapOptions?

    init(region: Binding<MKCoordinateRegion>) { self.region = region }

    func configure(_ map: MKMapView) {
        map.delegate = self
        map.showsUserLocation = false
        map.showsCompass = false
#if os(iOS)
        map.showsScale = false
#endif
    }

    func update(_ map: MKMapView, region: Binding<MKCoordinateRegion>, clusters: [MapCluster], options: MapOptions,
                showsUserLocation: Bool, animated: Bool, reduceMotion: Bool) {
        map.showsUserLocation = showsUserLocation
        if reduceMotion { densityTransition.finish() }
        self.region = region // Do not retain an obsolete representable value.
        if lastOptions != options {
            map.preferredConfiguration = options.configuration()
#if os(macOS)
            map.showsScale = options.scale
#endif
        }
        if rendered != clusters || lastOptions?.heatRadius != options.heatRadius || lastOptions?.heatOpacity != options.heatOpacity {
            rendered = clusters
            densityTransition.replace(on: map, clusters: clusters, options: options, animated: !reduceMotion)
        }
        lastOptions = options
        if !interacting && (lastInput == nil || !Self.same(lastInput!, region.wrappedValue)) {
            let shouldAnimate = lastInput != nil && animated
            lastInput = region.wrappedValue
            applyingRegion = true
            map.setRegion(region.wrappedValue, animated: shouldAnimate)
            applyingRegion = false
        }
    }

    func mapView(_ mapView: MKMapView, rendererFor overlay: any MKOverlay) -> MKOverlayRenderer {
        densityTransition.renderer(for: overlay) ?? MKOverlayRenderer(overlay: overlay)
    }

    func stop() { densityTransition.removeAll() }

    func mapView(_ mapView: MKMapView, regionWillChangeAnimated animated: Bool) {
        interacting = !applyingRegion
    }

    func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
        interacting = false
        let settled = mapView.region
        lastInput = settled
        guard !Self.same(region.wrappedValue, settled) else { return }
        Task { @MainActor [weak self] in self?.region.wrappedValue = settled }
    }

    private static func same(_ a: MKCoordinateRegion, _ b: MKCoordinateRegion) -> Bool {
        abs(a.center.latitude - b.center.latitude) < 1e-7 &&
        abs(a.center.longitude - b.center.longitude) < 1e-7 &&
        abs(a.span.latitudeDelta - b.span.latitudeDelta) < 1e-7 &&
        abs(a.span.longitudeDelta - b.span.longitudeDelta) < 1e-7
    }
}
