import SwiftUI
import MapKit
import PhotoMapCore

struct HeatmapView: View {
    @Binding var region: MKCoordinateRegion
    let clusters: [MapCluster]
    let options: MapOptions
    let showsUserLocation: Bool

    var body: some View {
        HeatmapPlatformView(region: $region, clusters: clusters, options: options, showsUserLocation: showsUserLocation)
    }
}

#if os(macOS)
private struct HeatmapPlatformView: NSViewRepresentable {
    @Binding var region: MKCoordinateRegion
    let clusters: [MapCluster]
    let options: MapOptions
    let showsUserLocation: Bool
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeCoordinator() -> HeatmapCoordinator { HeatmapCoordinator(region: $region) }
    func makeNSView(context: Context) -> MKMapView { context.coordinator.makeMap() }
    func updateNSView(_ map: MKMapView, context: Context) {
        context.coordinator.update(map, region: $region, clusters: clusters, options: options,
            showsUserLocation: showsUserLocation, animated: context.transaction.animation != nil,
            reduceMotion: reduceMotion)
        map.appearance = NSAppearance(named: colorScheme == .dark ? .darkAqua : .aqua)
    }
    static func dismantleNSView(_ map: MKMapView, coordinator: HeatmapCoordinator) { coordinator.stop(); map.delegate = nil }
}
#else
private struct HeatmapPlatformView: UIViewRepresentable {
    @Binding var region: MKCoordinateRegion
    let clusters: [MapCluster]
    let options: MapOptions
    let showsUserLocation: Bool
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeCoordinator() -> HeatmapCoordinator { HeatmapCoordinator(region: $region) }
    func makeUIView(context: Context) -> MKMapView { context.coordinator.makeMap() }
    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.update(map, region: $region, clusters: clusters, options: options,
            showsUserLocation: showsUserLocation, animated: context.transaction.animation != nil,
            reduceMotion: reduceMotion)
        map.overrideUserInterfaceStyle = colorScheme == .dark ? .dark : .light
    }
    static func dismantleUIView(_ map: MKMapView, coordinator: HeatmapCoordinator) { coordinator.stop(); map.delegate = nil }
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
    private var compass: MKCompassButton?
#if os(iOS)
    private var scale: MKScaleView?
    private var compassTop: NSLayoutConstraint?
#endif

    init(region: Binding<MKCoordinateRegion>) { self.region = region }

    func makeMap() -> MKMapView {
        let map = MKMapView()
        map.delegate = self
        map.showsUserLocation = false
        map.showsCompass = false
        let compass = MKCompassButton(mapView: map)
        compass.compassVisibility = .visible
        compass.translatesAutoresizingMaskIntoConstraints = false
        map.addSubview(compass)
        NSLayoutConstraint.activate([
            compass.trailingAnchor.constraint(equalTo: map.safeAreaLayoutGuide.trailingAnchor, constant: -20)
        ])
#if os(iOS)
        let scale = MKScaleView(mapView: map)
        scale.scaleVisibility = .visible
        scale.legendAlignment = .trailing
        scale.translatesAutoresizingMaskIntoConstraints = false
        map.addSubview(scale)
        NSLayoutConstraint.activate([
            scale.trailingAnchor.constraint(equalTo: map.safeAreaLayoutGuide.trailingAnchor, constant: -20),
            scale.topAnchor.constraint(equalTo: map.safeAreaLayoutGuide.topAnchor, constant: 12)
        ])
        let top = compass.topAnchor.constraint(equalTo: map.safeAreaLayoutGuide.topAnchor, constant: 52)
        top.isActive = true
        compassTop = top
        self.scale = scale
#else
        compass.topAnchor.constraint(equalTo: map.safeAreaLayoutGuide.topAnchor, constant: 12).isActive = true
#endif
        self.compass = compass
        return map
    }

    func update(_ map: MKMapView, region: Binding<MKCoordinateRegion>, clusters: [MapCluster], options: MapOptions,
                showsUserLocation: Bool, animated: Bool, reduceMotion: Bool) {
        map.showsUserLocation = showsUserLocation
        if reduceMotion { densityTransition.finish() }
        self.region = region // Do not retain an obsolete representable value.
        if lastOptions != options {
            map.preferredConfiguration = options.configuration()
            compass?.isHidden = !options.compass
#if os(iOS)
            map.showsScale = false
            scale?.isHidden = !options.scale
            compassTop?.constant = options.scale ? 52 : 12
#else
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

    func stop() { densityTransition.stop() }

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
