//
//  PhotoLocation.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-30.
//

import Foundation
import CoreLocation
import Photos

struct PhotoLocation: Identifiable, Equatable, Hashable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let asset: PHAsset
    let creationDate: Date?

    static func == (lhs: PhotoLocation, rhs: PhotoLocation) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}
