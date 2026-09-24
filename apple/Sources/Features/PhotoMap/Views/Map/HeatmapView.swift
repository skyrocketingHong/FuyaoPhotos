import SwiftUI
import MapKit
import PhotoMapCore

struct HeatmapView: View {
    @Binding var region: MKCoordinateRegion
    let clusters: [MapCluster]
    let options: MapOptions

    var body: some View {
        HeatmapPlatformView(region: $region, clusters: clusters, options: options)
    }
}

#if os(macOS)
private struct HeatmapPlatformView: NSViewRepresentable {
    @Binding var region: MKCoordinateRegion
    let clusters: [MapCluster]
    let options: MapOptions
    @Environment(\.colorScheme) private var colorScheme
    func makeCoordinator() -> HeatmapCoordinator { HeatmapCoordinator(region: $region) }
    func makeNSView(context: Context) -> MKMapView { context.coordinator.makeMap() }
    func updateNSView(_ map: MKMapView, context: Context) {
        context.coordinator.update(map, region: $region, clusters: clusters, options: options)
        map.appearance = NSAppearance(named: colorScheme == .dark ? .darkAqua : .aqua)
    }
    static func dismantleNSView(_ map: MKMapView, coordinator: HeatmapCoordinator) { map.delegate = nil }
}
#else
private struct HeatmapPlatformView: UIViewRepresentable {
    @Binding var region: MKCoordinateRegion
    let clusters: [MapCluster]
    let options: MapOptions
    @Environment(\.colorScheme) private var colorScheme
    func makeCoordinator() -> HeatmapCoordinator { HeatmapCoordinator(region: $region) }
    func makeUIView(context: Context) -> MKMapView { context.coordinator.makeMap() }
    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.update(map, region: $region, clusters: clusters, options: options)
        map.overrideUserInterfaceStyle = colorScheme == .dark ? .dark : .light
    }
    static func dismantleUIView(_ map: MKMapView, coordinator: HeatmapCoordinator) { map.delegate = nil }
}
#endif

private final class HeatmapCoordinator: NSObject, MKMapViewDelegate {
    var region: Binding<MKCoordinateRegion>
    private var rendered: [MapCluster] = []
    private var overlay: HeatmapOverlay?
    private var lastInput: MKCoordinateRegion?
    private var applyingRegion = false
    private var interacting = false
    private var lastOptions: MapOptions?
    private var compass: MKCompassButton?

    init(region: Binding<MKCoordinateRegion>) { self.region = region }

    func makeMap() -> MKMapView {
        let map = MKMapView()
        map.delegate = self
        map.showsUserLocation = false
        map.showsCompass = false
        let compass = MKCompassButton(mapView: map)
        compass.translatesAutoresizingMaskIntoConstraints = false
        map.addSubview(compass)
        NSLayoutConstraint.activate([
            compass.trailingAnchor.constraint(equalTo: map.safeAreaLayoutGuide.trailingAnchor,constant:-12),
            compass.bottomAnchor.constraint(equalTo: map.safeAreaLayoutGuide.bottomAnchor,constant:-12)
        ])
        self.compass = compass
        return map
    }

    func update(_ map: MKMapView, region: Binding<MKCoordinateRegion>, clusters: [MapCluster], options: MapOptions) {
        self.region = region // Do not retain an obsolete representable value.
        if lastOptions != options {
            map.preferredConfiguration = options.configuration()
            compass?.isHidden = !options.compass
            map.showsScale = options.scale
        }
        if rendered != clusters || lastOptions?.heatRadius != options.heatRadius || lastOptions?.heatOpacity != options.heatOpacity {
            if let overlay { map.removeOverlay(overlay) }
            rendered = clusters
            overlay = clusters.isEmpty ? nil : HeatmapOverlay(clusters: clusters, radius: options.heatRadius, opacity: options.heatOpacity)
            if let overlay { map.addOverlay(overlay, level: .aboveRoads) }
        }
        lastOptions = options
        if !interacting && (lastInput == nil || !Self.same(lastInput!, region.wrappedValue)) {
            lastInput = region.wrappedValue
            applyingRegion = true
            map.setRegion(region.wrappedValue, animated: false)
            applyingRegion = false
        }
    }

    func mapView(_ mapView: MKMapView, rendererFor overlay: any MKOverlay) -> MKOverlayRenderer {
        guard let density = overlay as? HeatmapOverlay else { return MKOverlayRenderer(overlay: overlay) }
        return HeatmapRenderer(overlay: density)
    }

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
