//
//  MapStyleMode.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-30.
//

import Foundation
import SwiftUI
import MapKit

@available(iOS 17.0, macOS 14.0, *)
enum MapStyleMode: String, Codable, CaseIterable, Identifiable {
    case explore = "map.style.explore"
    case muted = "map.style.muted"
    case satellite = "map.style.satellite"
    case hybrid = "map.style.hybrid"

    var id: String { rawValue }

    var localizedName: LocalizedStringKey {
        LocalizedStringKey(rawValue)
    }

    var icon: String {
        switch self {
        case .explore: return "map"
        case .muted: return "map.circle"
        case .satellite: return "globe.asia.australia.fill"
        case .hybrid: return "map.fill"
        }
    }

    var swiftUIMapStyle: MapStyle {
        switch self {
        case .explore:
            return .standard(elevation: .realistic, pointsOfInterest: .all, showsTraffic: false)
        case .muted:
            return .standard(emphasis: .muted)
        case .satellite:
            return .imagery(elevation: .realistic)
        case .hybrid:
            return .hybrid(elevation: .realistic, pointsOfInterest: .all, showsTraffic: false)
        }
    }
}
