import MapKit
import SwiftUI

enum MapAppearance: String, Codable, CaseIterable, Identifiable {
    case system, light, dark
    var id: Self { self }
    var title: LocalizedStringKey { LocalizedStringKey("map.appearance." + rawValue) }
    var colorScheme: ColorScheme? {
        switch self { case .system: nil; case .light: .light; case .dark: .dark }
    }
}

struct MapOptions: Codable, Equatable {
    var style: MapStyleMode = .explore
    var appearance: MapAppearance = .system
    var traffic = false
    var pointsOfInterest = true
    var realisticElevation = true
    var compass = true
    var scale = true
    var heatRadius = 64.0
    var heatOpacity = 0.8

    var swiftUIStyle: MapStyle {
        let elevation: MapStyle.Elevation = realisticElevation ? .realistic : .flat
        let points: PointOfInterestCategories = pointsOfInterest ? .all : .excludingAll
        switch style {
        case .explore:
            return .standard(elevation: elevation, pointsOfInterest: points, showsTraffic: traffic)
        case .muted:
            return .standard(elevation: elevation, emphasis: .muted, pointsOfInterest: points, showsTraffic: traffic)
        case .satellite where !traffic && !pointsOfInterest:
            return .imagery(elevation: elevation)
        case .satellite, .hybrid:
            return .hybrid(elevation: elevation, pointsOfInterest: points, showsTraffic: traffic)
        }
    }

    func configuration() -> MKMapConfiguration {
        let elevation: MKMapConfiguration.ElevationStyle = realisticElevation ? .realistic : .flat
        switch style {
        case .explore, .muted:
            let configuration = MKStandardMapConfiguration(elevationStyle: elevation,
                emphasisStyle: style == .muted ? .muted : .default)
            configuration.showsTraffic = traffic
            configuration.pointOfInterestFilter = pointsOfInterest ? .includingAll : .excludingAll
            return configuration
        case .satellite where !traffic && !pointsOfInterest:
            return MKImageryMapConfiguration(elevationStyle: elevation)
        case .satellite, .hybrid:
            let configuration = MKHybridMapConfiguration(elevationStyle: elevation)
            configuration.showsTraffic = traffic
            configuration.pointOfInterestFilter = pointsOfInterest ? .includingAll : .excludingAll
            return configuration
        }
    }
}
