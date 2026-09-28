import Foundation

/// Display spaces, independent of a source photo's EXIF datum.
public enum MapCoordinateSystem: String, Codable, CaseIterable, Sendable {
    case wgs84, gcj02
}

public struct GeographicCoordinate: Equatable, Sendable {
    public let latitude: Double
    public let longitude: Double

    public init?(latitude: Double, longitude: Double) {
        guard latitude.isFinite, longitude.isFinite,
              (-90...90).contains(latitude), (-180...180).contains(longitude) else { return nil }
        self.latitude = latitude
        self.longitude = longitude
    }

    public func converted(from source: MapCoordinateSystem, to destination: MapCoordinateSystem) -> Self {
        guard source != destination, latitude != 0 || longitude != 0,
              abs(latitude) < 90 else { return self }
        if destination == .gcj02 { return addingOffset() }
        var estimate = self
        for _ in 0..<10 {
            let forward = estimate.addingOffset()
            let latitudeError = forward.latitude - latitude
            let longitudeError = forward.longitude - longitude
            guard let next = Self(latitude: estimate.latitude - latitudeError,
                                  longitude: estimate.longitude - longitudeError) else { return self }
            estimate = next
            if max(abs(latitudeError), abs(longitudeError)) < 1e-10 { break }
        }
        return estimate
    }

    // Ported from FuyaoHomepage / wandergis/coordtransform (MIT).
    // Its Amap branch applies this display mapping globally; a geographic
    // bounding box would incorrectly change that provider-specific behavior.
    // See LICENSES/CoordTransform-MIT.txt for the upstream notice.
    private func addingOffset() -> Self {
        let x = longitude - 105, y = latitude - 35
        var dLat = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        dLat += (20 * sin(6 * x * .pi) + 20 * sin(2 * x * .pi)) * 2 / 3
        dLat += (20 * sin(y * .pi) + 40 * sin(y / 3 * .pi)) * 2 / 3
        dLat += (160 * sin(y / 12 * .pi) + 320 * sin(y * .pi / 30)) * 2 / 3
        var dLon = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        dLon += (20 * sin(6 * x * .pi) + 20 * sin(2 * x * .pi)) * 2 / 3
        dLon += (20 * sin(x * .pi) + 40 * sin(x / 3 * .pi)) * 2 / 3
        dLon += (150 * sin(x / 12 * .pi) + 300 * sin(x / 30 * .pi)) * 2 / 3
        let a = 6_378_245.0, eccentricity = 0.00669342162296594
        let radians = latitude / 180 * .pi
        let magic = 1 - eccentricity * pow(sin(radians), 2)
        let root = sqrt(magic)
        dLat = dLat * 180 / ((a * (1 - eccentricity) / (magic * root)) * .pi)
        dLon = dLon * 180 / ((a / root * cos(radians)) * .pi)
        return Self(latitude: latitude + dLat, longitude: longitude + dLon) ?? self
    }
}

extension PhotoCoordinate {
    func projected(to system: MapCoordinateSystem) -> Self? {
        guard isValid, let original = GeographicCoordinate(latitude: latitude, longitude: longitude) else { return nil }
        let displayed = original.converted(from: .wgs84, to: system)
        return Self(id: id, latitude: displayed.latitude, longitude: displayed.longitude,
                    creationDate: creationDate, year: year)
    }
}
