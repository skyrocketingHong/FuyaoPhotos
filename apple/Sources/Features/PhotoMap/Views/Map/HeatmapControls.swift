import SwiftUI
import MapKit

struct HeatmapControls: View {
    let map: MKMapView
    let options: MapOptions

    var body: some View {
#if os(iOS)
        if options.scale { HeatmapScale(map: map).modifier(MapScaleContentAlignment()) }
#endif
        if options.compass { HeatmapCompass(map: map) }
    }
}

#if os(iOS)
private struct HeatmapScale: UIViewRepresentable {
    let map: MKMapView

    func makeUIView(context: Context) -> MKScaleView {
        let scale = MKScaleView(mapView: map)
        scale.scaleVisibility = .visible
        scale.legendAlignment = .trailing
        return scale
    }

    func updateUIView(_ scale: MKScaleView, context: Context) { scale.mapView = map }
}

private struct HeatmapCompass: UIViewRepresentable {
    let map: MKMapView

    func makeUIView(context: Context) -> MKCompassButton {
        let compass = MKCompassButton(mapView: map)
        compass.compassVisibility = .visible
        return compass
    }

    func updateUIView(_ compass: MKCompassButton, context: Context) { compass.mapView = map }
}
#else
private struct HeatmapCompass: NSViewRepresentable {
    let map: MKMapView

    func makeNSView(context: Context) -> MKCompassButton {
        let compass = MKCompassButton(mapView: map)
        compass.compassVisibility = .visible
        return compass
    }

    func updateNSView(_ compass: MKCompassButton, context: Context) { compass.mapView = map }
}
#endif
