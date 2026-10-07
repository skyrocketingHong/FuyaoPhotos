import Foundation

public enum MapExportScope: Equatable, Sendable {
    case allFiltered
    case viewport(MapViewport)
}

public struct MapExportSnapshot: Equatable, Sendable {
    /// These values are already in coordinateSystem, not raw WGS84. Never project them a second time.
    public let displayCoordinates: [PhotoCoordinate]
    public let coordinateSystem: MapCoordinateSystem
    public let revision: UInt64
    public let minYear: Int?
    public let maxYear: Int?
    public var totalCount: Int { displayCoordinates.count }

    public init(displayCoordinates: [PhotoCoordinate], coordinateSystem: MapCoordinateSystem, revision: UInt64) {
        self.displayCoordinates = displayCoordinates
        self.coordinateSystem = coordinateSystem
        self.revision = revision
        var minimum: Int?
        var maximum: Int?
        for coordinate in displayCoordinates {
            if let year = coordinate.year {
                minimum = min(minimum ?? year, year)
                maximum = max(maximum ?? year, year)
            }
        }
        minYear = minimum
        maxYear = maximum
    }
}

public enum MapExportGeometryError: Error, Equatable, Sendable {
    case emptySelection
    case invalidGeometry
}
