import SwiftUI
import MapKit
import QuartzCore
import PhotoMapCore

@MainActor final class HeatmapLayerTransition: NSObject {
    private struct Layer {
        let overlay: HeatmapOverlay
        let renderer: HeatmapRenderer
        let inserted: CFTimeInterval
        var retirement: (time: CFTimeInterval, alpha: CGFloat)?
    }
    private weak var map: MKMapView?
    private var layers: [Layer] = []
    private var displayLink: CADisplayLink?
    private let duration: CFTimeInterval = 0.2

    func replace(on map: MKMapView, clusters: [MapCluster], options: MapOptions, animated: Bool) {
        self.map = map
        let now = CACurrentMediaTime()
        advance(to: now)
        for index in layers.indices where layers[index].retirement == nil {
            layers[index].retirement = (now, layers[index].renderer.alpha)
        }
        if !clusters.isEmpty {
            let overlay = HeatmapOverlay(clusters: clusters, radius: options.heatRadius, opacity: options.heatOpacity)
            let renderer = HeatmapRenderer(overlay: overlay)
            renderer.alpha = animated ? 0 : 1
            layers.append(Layer(overlay: overlay, renderer: renderer, inserted: now))
            map.addOverlay(overlay, level: .aboveRoads)
        }
        if animated, !layers.isEmpty {
            startDisplayLink(on: map)
        } else {
            finish()
        }
    }

    func renderer(for overlay: any MKOverlay) -> MKOverlayRenderer? {
        layers.first { $0.overlay === (overlay as AnyObject) }?.renderer
    }

    func stop() { displayLink?.invalidate(); displayLink = nil }

    func removeAll() {
        stop()
        for layer in layers { map?.removeOverlay(layer.overlay) }
        layers.removeAll()
        map = nil
    }

    func finish() {
        stop()
        for layer in layers where layer.retirement != nil { map?.removeOverlay(layer.overlay) }
        layers.removeAll { $0.retirement != nil }
        for layer in layers { layer.renderer.alpha = 1 }
    }

    private func startDisplayLink(on map: MKMapView) {
        guard displayLink == nil else { return }
#if os(macOS)
        let link = map.displayLink(target: self, selector: #selector(tick(_:)))
#else
        let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
#endif
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    @objc private func tick(_ link: CADisplayLink) {
        let now = CACurrentMediaTime()
        advance(to: now)
        if layers.allSatisfy({ $0.retirement == nil && now - $0.inserted >= duration }) { stop() }
    }

    private func advance(to now: CFTimeInterval) {
        var removed: [HeatmapOverlay] = []
        for layer in layers {
            let start = layer.retirement?.time ?? layer.inserted
            let progress = min(1, max(0, (now - start) / duration))
            let amount = CGFloat(UnitCurve.easeOut.value(at: progress))
            layer.renderer.alpha = layer.retirement.map { $0.alpha * (1 - amount) } ?? amount
            if layer.retirement != nil && progress >= 1 { removed.append(layer.overlay) }
        }
        for overlay in removed { map?.removeOverlay(overlay) }
        layers.removeAll { layer in removed.contains { $0 === layer.overlay } }
    }
}
