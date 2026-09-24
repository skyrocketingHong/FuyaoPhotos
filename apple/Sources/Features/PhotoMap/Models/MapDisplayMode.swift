//
//  MapDisplayMode.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-30.
//

import Foundation
import SwiftUI

enum MapDisplayMode: String, CaseIterable, Identifiable {
    case photo = "display.mode.photo"
    case cluster = "display.mode.cluster"
    case heatmap = "display.mode.heatmap"

    var id: String { rawValue }

    var localizedName: LocalizedStringKey {
        LocalizedStringKey(rawValue)
    }

    var icon: String {
        switch self {
        case .photo: return "photo.fill"
        case .cluster: return "circle.grid.3x3.fill"
        case .heatmap: return "flame.fill"
        }
    }
}
