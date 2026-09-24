import Foundation

/// Raw WGS84 metadata. Display projection never changes the stored coordinates.
public struct PhotoCoordinate: Identifiable, Codable, Equatable, Sendable {
    public let id: String
    public let latitude: Double
    public let longitude: Double
    public let creationDate: Date?
    public let year: Int?

    public init(id: String, latitude: Double, longitude: Double, creationDate: Date?, year: Int?) {
        self.id = id
        self.latitude = latitude
        self.longitude = longitude
        self.creationDate = creationDate
        self.year = year
    }

    public var isValid: Bool {
        !id.isEmpty && latitude.isFinite && longitude.isFinite &&
        (-90...90).contains(latitude) && (-180...180).contains(longitude)
    }

    var point: ProjectedPoint { ProjectedPoint(latitude: latitude, longitude: longitude) }
}

struct ProjectedPoint: Sendable {
    let x: Double
    let y: Double

    init(latitude: Double, longitude: Double) {
        x = min(1.nextDown, max(0, (longitude + 180) / 360))
        let radians = min(85.05112878, max(-85.05112878, latitude)) * .pi / 180
        y = min(1.nextDown, max(0, (1 - log(tan(.pi / 4 + radians / 2)) / .pi) / 2))
    }

    static func latitude(y: Double) -> Double { atan(sinh(.pi * (1 - 2 * y))) * 180 / .pi }
}

public struct MapViewport: Equatable, Sendable {
    public let latitude: Double
    public let longitude: Double
    public let latitudeDelta: Double
    public let longitudeDelta: Double

    public init(latitude: Double, longitude: Double, latitudeDelta: Double, longitudeDelta: Double) {
        self.latitude = latitude
        self.longitude = longitude
        self.latitudeDelta = latitudeDelta
        self.longitudeDelta = longitudeDelta
    }

    var rectangles: [MapRect] {
        guard latitude.isFinite, longitude.isFinite, latitudeDelta.isFinite,
              longitudeDelta.isFinite, latitudeDelta > 0, longitudeDelta > 0 else { return [] }
        let north = min(90, latitude + latitudeDelta / 2)
        let south = max(-90, latitude - latitudeDelta / 2)
        guard north >= south else { return [] }
        let top = ProjectedPoint(latitude: north, longitude: 0).y
        let bottom = min(1, ProjectedPoint(latitude: south, longitude: 0).y.nextUp)
        if longitudeDelta >= 360 { return [MapRect(x: 0, y: top, width: 1, height: bottom - top)] }
        let center = ((longitude + 180).truncatingRemainder(dividingBy: 360) + 360)
            .truncatingRemainder(dividingBy: 360) / 360
        let span = longitudeDelta / 360
        let left = center - span / 2
        let right = center + span / 2
        if left < 0 {
            return [MapRect(x: 0, y: top, width: right, height: bottom - top),
                    MapRect(x: left + 1, y: top, width: -left, height: bottom - top)]
        }
        if right > 1 {
            return [MapRect(x: left, y: top, width: 1 - left, height: bottom - top),
                    MapRect(x: 0, y: top, width: right - 1, height: bottom - top)]
        }
        return [MapRect(x: left, y: top, width: span, height: bottom - top)]
    }
}

struct MapRect: Sendable {
    let x: Double
    let y: Double
    let width: Double
    let height: Double
    var maxX: Double { x + width }
    var maxY: Double { y + height }

    func contains(_ point: ProjectedPoint) -> Bool {
        point.x >= x && point.x < maxX && point.y >= y && point.y < maxY
    }
    func contains(_ rect: MapRect) -> Bool {
        rect.x >= x && rect.y >= y && rect.maxX <= maxX && rect.maxY <= maxY
    }
    func intersects(_ rect: MapRect) -> Bool {
        x < rect.maxX && maxX > rect.x && y < rect.maxY && maxY > rect.y
    }
}

public struct MapCell: Hashable, Sendable {
    public let level: Int
    public let x: Int
    public let y: Int
    public var id: String { "\(level):\(x):\(y)" }

    init(level: Int, point: ProjectedPoint) {
        self.level = level
        let size = Double(1 << level)
        x = min((1 << level) - 1, Int(point.x * size))
        y = min((1 << level) - 1, Int(point.y * size))
    }
    var rect: MapRect {
        let side = 1 / Double(1 << level)
        return MapRect(x: Double(x) * side, y: Double(y) * side, width: side, height: side)
    }
}

public struct MapCluster: Identifiable, Equatable, Sendable {
    public let id: String
    public let latitude: Double
    public let longitude: Double
    public let count: Int
    public let representativeID: String
    public let cell: MapCell

    init(cell: MapCell, aggregate: Aggregate) {
        self.cell = cell
        id = cell.id
        count = aggregate.count
        latitude = ProjectedPoint.latitude(y: aggregate.sumY / Double(count))
        longitude = aggregate.sumX / Double(count) * 360 - 180
        representativeID = aggregate.representative
    }
}

public struct MapQueryResult: Equatable, Sendable {
    public let clusters: [MapCluster]
    public let totalCount: Int
    public let revision: UInt64
    public let visitedNodes: Int

    public init(clusters: [MapCluster] = [], totalCount: Int = 0, revision: UInt64 = 0, visitedNodes: Int = 0) {
        self.clusters = clusters
        self.totalCount = totalCount
        self.revision = revision
        self.visitedNodes = visitedNodes
    }
}

struct Aggregate {
    var count = 0
    var sumX = 0.0
    var sumY = 0.0
    var representative = ""

    mutating func add(_ photo: PhotoCoordinate) {
        let point = photo.point
        count += 1
        sumX += point.x
        sumY += point.y
        if representative.isEmpty || photo.id < representative { representative = photo.id }
    }
    mutating func add(_ other: Aggregate) {
        guard other.count > 0 else { return }
        count += other.count
        sumX += other.sumX
        sumY += other.sumY
        if representative.isEmpty || other.representative < representative { representative = other.representative }
    }
}
