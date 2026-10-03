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
    let safeAreaInsets: EdgeInsets

    var body: some View {
#if os(macOS)
        HeatmapPlatformView(map: map, region: $region, clusters: clusters, options: options,
            showsUserLocation: showsUserLocation, safeAreaInsets: safeAreaInsets)
            .ignoresSafeArea(.container)
#else
        HeatmapPlatformView(map: map, region: $region, clusters: clusters, options: options, showsUserLocation: showsUserLocation)
            .safeAreaPadding(safeAreaInsets)
            .ignoresSafeArea(.container)
#endif
    }
}

#if os(macOS)
private struct HeatmapPlatformView: NSViewRepresentable {
    let map: MKMapView
    @Binding var region: MKCoordinateRegion
    let clusters: [MapCluster]
    let options: MapOptions
    let showsUserLocation: Bool
    let safeAreaInsets: EdgeInsets
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection
    func makeCoordinator() -> HeatmapCoordinator { HeatmapCoordinator(region: $region) }
    func makeNSView(context: Context) -> HeatmapMapHost {
        context.coordinator.configure(map)
        return HeatmapMapHost(map: map)
    }
    func sizeThatFits(_ proposal: ProposedViewSize, nsView: HeatmapMapHost, context: Context) -> CGSize? {
        guard let width = proposal.width, let height = proposal.height,
              width.isFinite, height.isFinite else { return nil }
        return CGSize(width: width, height: height)
    }
    func updateNSView(_ host: HeatmapMapHost, context: Context) {
        host.requiredInsets = NSEdgeInsets(top: safeAreaInsets.top,
            left: layoutDirection == .leftToRight ? safeAreaInsets.leading : safeAreaInsets.trailing,
            bottom: safeAreaInsets.bottom,
            right: layoutDirection == .leftToRight ? safeAreaInsets.trailing : safeAreaInsets.leading)
        host.updateSafeArea()
        let map = host.map
        context.coordinator.update(map, region: $region, clusters: clusters, options: options,
            showsUserLocation: showsUserLocation, animated: context.transaction.animation != nil,
            reduceMotion: reduceMotion)
        map.appearance = NSAppearance(named: colorScheme == .dark ? .darkAqua : .aqua)
    }
    static func dismantleNSView(_ host: HeatmapMapHost, coordinator: HeatmapCoordinator) {
        coordinator.stop()
        let map = host.map
        map.delegate = nil
        map.showsUserLocation = false
        map.removeFromSuperview()
        map.additionalSafeAreaInsets = NSEdgeInsetsZero
    }
}

private final class HeatmapMapHost: NSView {
    let map: MKMapView
    var requiredInsets = NSEdgeInsetsZero

    init(map: MKMapView) {
        self.map = map
        super.init(frame: .zero)
        map.frame = bounds
        map.autoresizingMask = [.width, .height]
        addSubview(map)
    }

    required init?(coder: NSCoder) { return nil }

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        updateSafeArea()
    }

    override func layout() {
        updateSafeArea()
        super.layout()
    }

    func updateSafeArea() {
        guard map.superview === self else { return }
        let applied = map.additionalSafeAreaInsets
        let resolved = map.safeAreaInsets
        // Insets protect MapKit's controls without shrinking the canvas behind the sidebar.
        // Subtract our prior contribution so inherited AppKit insets are not counted twice.
        let next = NSEdgeInsets(
            top: max(0, requiredInsets.top - max(0, resolved.top - applied.top)),
            left: max(0, requiredInsets.left - max(0, resolved.left - applied.left)),
            bottom: max(0, requiredInsets.bottom - max(0, resolved.bottom - applied.bottom)),
            right: max(0, requiredInsets.right - max(0, resolved.right - applied.right)))
        if next.top != applied.top || next.left != applied.left ||
            next.bottom != applied.bottom || next.right != applied.right {
            map.additionalSafeAreaInsets = next
        }
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
