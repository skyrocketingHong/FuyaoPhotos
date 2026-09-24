import Foundation

/// The tree and its mutable summaries belong exclusively to this background actor.
/// Whole-subtree summaries make a world-scale query independent of the photo count.
public actor PhotoSpatialIndex {
    private var records: [String: PhotoCoordinate] = [:]
    private var root = Node(level: 0, x: 0, y: 0)
    public private(set) var revision: UInt64 = 0

    public init() {}

    public func replace(with photos: [PhotoCoordinate]) throws {
        let newRoot = Node(level: 0, x: 0, y: 0)
        var newRecords: [String: PhotoCoordinate] = [:]
        for (offset, photo) in photos.enumerated() {
            if offset.isMultiple(of: 256) { try Task.checkCancellation() }
            guard photo.isValid else { continue }
            if let old = newRecords[photo.id] { newRoot.remove(old) }
            newRecords[photo.id] = photo
            newRoot.insert(photo)
        }
        try Task.checkCancellation()
        root = newRoot
        records = newRecords
        revision &+= 1
    }

    /// A Photos change is one synchronous actor transaction, never partially visible.
    public func apply(upserting photos: [PhotoCoordinate], removing identifiers: [String]) {
        // Large library edits rebuild once, rather than repeatedly reducing a dense leaf.
        if photos.count + identifiers.count > 64 {
            for id in identifiers { records.removeValue(forKey: id) }
            for photo in photos {
                records.removeValue(forKey: photo.id)
                if photo.isValid { records[photo.id] = photo }
            }
            let replacement = Node(level: 0, x: 0, y: 0)
            for photo in records.values { replacement.insert(photo) }
            root = replacement
            revision &+= 1
            return
        }
        for id in identifiers {
            if let old = records.removeValue(forKey: id) { root.remove(old) }
        }
        for photo in photos {
            if let old = records.removeValue(forKey: photo.id) { root.remove(old) }
            if photo.isValid {
                records[photo.id] = photo
                root.insert(photo)
            }
        }
        revision &+= 1
    }

    public func query(viewport: MapViewport, width: Double, height: Double, year: Int?,
                      cellSize: Double = 64, limit: Int = 300) throws -> MapQueryResult {
        let rectangles = viewport.rectangles
        guard !rectangles.isEmpty, width.isFinite, height.isFinite,
              cellSize.isFinite, width > 0, height > 0, cellSize > 0 else { return MapQueryResult(revision: revision) }
        let spanX = rectangles.reduce(0) { $0 + $1.width }
        let spanY = rectangles[0].height
        let scale = min(width / max(spanX, 1e-12), height / max(spanY, 1e-12)) / cellSize
        // Clamp before converting to Int: finite inputs can still overflow the division above.
        var level = Int(min(22, max(0, floor(log2(max(1, scale))))))
        let budget = max(1, limit)
        while true {
            try Task.checkCancellation()
            var aggregates: [MapCell: Aggregate] = [:]
            var visited = 0
            for rect in rectangles {
                try root.query(rect, year: year, targetLevel: level, output: &aggregates, visited: &visited)
            }
            // Reduce resolution instead of discarding groups or their members.
            if aggregates.count <= budget || level == 0 {
                let clusters = aggregates.map { MapCluster(cell: $0.key, aggregate: $0.value) }
                    .sorted { ($0.cell.y, $0.cell.x) < ($1.cell.y, $1.cell.x) }
                return MapQueryResult(clusters: clusters, totalCount: clusters.reduce(0) { $0 + $1.count },
                                      revision: revision, visitedNodes: visited)
            }
            level -= 1
        }
    }

    public func members(of cell: MapCell, viewport: MapViewport, year: Int?, offset: Int, limit: Int) throws -> [PhotoCoordinate] {
        var matches: [PhotoCoordinate] = []
        try root.collect(cell.rect, year: year, output: &matches)
        let rectangles = viewport.rectangles
        matches.removeAll { photo in !rectangles.contains { $0.contains(photo.point) } }
        matches.sort { $0.id < $1.id }
        return Array(matches.dropFirst(max(0, offset)).prefix(max(0, min(200, limit))))
    }

    public func snapshot() -> [PhotoCoordinate] { Array(records.values) }
    public func coordinate(for id: String) -> PhotoCoordinate? { records[id] }

    public func bounds(year: Int?) throws -> MapViewport? {
        var latitudes: [Double] = []
        var longitudes: [Double] = []
        for (offset, photo) in records.values.enumerated() {
            if offset.isMultiple(of: 256) { try Task.checkCancellation() }
            if year == nil || photo.year == year {
                latitudes.append(photo.latitude)
                longitudes.append(photo.longitude)
            }
        }
        guard let south = latitudes.min(), let north = latitudes.max() else { return nil }
        longitudes.sort()
        var gap = -1.0
        var start = longitudes[0]
        for i in longitudes.indices {
            let next = i + 1 < longitudes.count ? longitudes[i + 1] : longitudes[0] + 360
            if next - longitudes[i] > gap { gap = next - longitudes[i]; start = next }
        }
        let rawSpan = max(0, 360 - gap)
        let span = max(0.01, rawSpan)
        let center = ((start + rawSpan / 2 + 180).truncatingRemainder(dividingBy: 360) + 360)
            .truncatingRemainder(dividingBy: 360) - 180
        return MapViewport(latitude: (south + north) / 2, longitude: center,
                           latitudeDelta: min(180, max(0.01, (north - south) * 1.2)),
                           longitudeDelta: min(360, span * 1.2))
    }
}

/// Reference nodes never leave PhotoSpatialIndex; no unchecked Sendable escape.
private final class Node {
    let level: Int
    let x: Int
    let y: Int
    let rect: MapRect
    var photos: [String: PhotoCoordinate] = [:]
    var children: [Node]?
    var all = Aggregate()
    var years: [Int: Aggregate] = [:]

    init(level: Int, x: Int, y: Int) {
        self.level = level; self.x = x; self.y = y
        let side = 1 / Double(1 << level)
        rect = MapRect(x: Double(x) * side, y: Double(y) * side, width: side, height: side)
    }

    func insert(_ photo: PhotoCoordinate) {
        all.add(photo)
        if let year = photo.year { years[year, default: Aggregate()].add(photo) }
        if let children { children[quadrant(photo.point)].insert(photo); return }
        photos[photo.id] = photo
        if photos.count > 64 && level < 18 {
            let nodes = (0..<4).map { Node(level: level + 1, x: x * 2 + $0 % 2, y: y * 2 + $0 / 2) }
            for item in photos.values { nodes[quadrant(item.point)].insert(item) }
            children = nodes
            photos.removeAll(keepingCapacity: false)
        }
    }

    func remove(_ photo: PhotoCoordinate) {
        if let children { children[quadrant(photo.point)].remove(photo) }
        else { photos.removeValue(forKey: photo.id) }
        all = Aggregate(); years.removeAll(keepingCapacity: true)
        if let children {
            for child in children {
                all.add(child.all)
                for (year, aggregate) in child.years { years[year, default: Aggregate()].add(aggregate) }
            }
            if all.count == 0 { self.children = nil }
        } else {
            for item in photos.values {
                all.add(item)
                if let year = item.year { years[year, default: Aggregate()].add(item) }
            }
        }
    }

    func quadrant(_ point: ProjectedPoint) -> Int {
        (point.x >= rect.x + rect.width / 2 ? 1 : 0) + (point.y >= rect.y + rect.height / 2 ? 2 : 0)
    }

    func query(_ visible: MapRect, year: Int?, targetLevel: Int,
               output: inout [MapCell: Aggregate], visited: inout Int) throws {
        guard rect.intersects(visible) else { return }
        visited += 1
        if visited.isMultiple(of: 128) { try Task.checkCancellation() }
        let summary = year.flatMap { years[$0] } ?? (year == nil ? all : Aggregate())
        guard summary.count > 0 else { return }
        if level >= targetLevel && visible.contains(rect) {
            let point = ProjectedPoint(latitude: ProjectedPoint.latitude(y: rect.y + rect.height / 2),
                                       longitude: (rect.x + rect.width / 2) * 360 - 180)
            output[MapCell(level: targetLevel, point: point), default: Aggregate()].add(summary)
        } else if let children {
            for child in children { try child.query(visible, year: year, targetLevel: targetLevel, output: &output, visited: &visited) }
        } else {
            for (offset, photo) in photos.values.enumerated() {
                if offset.isMultiple(of: 256) { try Task.checkCancellation() }
                if (year == nil || photo.year == year) && visible.contains(photo.point) {
                    output[MapCell(level: targetLevel, point: photo.point), default: Aggregate()].add(photo)
                }
            }
        }
    }

    func collect(_ visible: MapRect, year: Int?, output: inout [PhotoCoordinate]) throws {
        try Task.checkCancellation()
        guard rect.intersects(visible) else { return }
        if let children {
            for child in children { try child.collect(visible, year: year, output: &output) }
        } else {
            for (offset, photo) in photos.values.enumerated() {
                if offset.isMultiple(of: 256) { try Task.checkCancellation() }
                if (year == nil || photo.year == year) && visible.contains(photo.point) { output.append(photo) }
            }
        }
    }
}
