import MapKit
import PhotoMapCore

/// A bounded density grid, not a copy of every photo in the library.
nonisolated final class HeatmapOverlay: NSObject, MKOverlay {
    let coordinate = CLLocationCoordinate2D(latitude: 0, longitude: 0)
    let boundingMapRect = MKMapRect.world
    let points: [(point: MKMapPoint, intensity: CGFloat)]
    let radius: CGFloat
    let opacity: CGFloat

    init(clusters: [MapCluster], radius: Double, opacity: Double) {
        self.radius = min(120, max(32, radius))
        self.opacity = min(1, max(0.3, opacity))
        let maximum = Double(max(1, clusters.map(\.count).max() ?? 1))
        points = clusters.map {
            (MKMapPoint(CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)),
             CGFloat(max(0.4, log1p(Double($0.count)) / log1p(maximum))))
        }
        super.init()
    }
}

final class HeatmapRenderer: MKOverlayRenderer {
    private let density: HeatmapOverlay
    private let gradient: CGGradient?

    init(overlay: HeatmapOverlay) {
        density = overlay
        let space = CGColorSpaceCreateDeviceRGB()
        let colors: [CGColor] = [
            CGColor(red: 1, green: 0.35, blue: 0.1, alpha: 0.85),
            CGColor(red: 1, green: 0.8, blue: 0.1, alpha: 0.55),
            CGColor(red: 0.15, green: 0.7, blue: 0.8, alpha: 0.3),
            CGColor(red: 0.2, green: 0.4, blue: 0.9, alpha: 0)
        ]
        gradient = CGGradient(colorsSpace: space, colors: colors as CFArray, locations: [0, 0.25, 0.65, 1])
        super.init(overlay: overlay)
    }

    override func draw(_ mapRect: MKMapRect, zoomScale: MKZoomScale, in context: CGContext) {
        guard let gradient, zoomScale > 0 else { return }
        let radius = density.radius / zoomScale
        let padded = mapRect.insetBy(dx: -radius, dy: -radius)
        context.saveGState()
        defer { context.restoreGState() }
        context.setBlendMode(.normal)
        for item in density.points where padded.contains(item.point) {
            context.setAlpha(item.intensity * density.opacity)
            // Map coordinates and renderer coordinates are different spaces.
            let center = point(for: item.point)
            context.drawRadialGradient(gradient, startCenter: center, startRadius: 0,
                                       endCenter: center, endRadius: radius, options: [])
        }
    }
}
