import Foundation

public struct MapExportSquareRect: Equatable, Sendable {
    /// Unit Web Mercator coordinates. X may wrap beyond 0...1; Y stays inside the map world.
    public let x: Double
    public let y: Double
    public let side: Double
    public var midX: Double { x + side / 2 }
    public var midY: Double { y + side / 2 }
}

public struct MapExportPoint: Equatable, Sendable {
    public let x: Double
    public let y: Double
}

public struct MapExportFrame: Equatable, Sendable {
    public let squareRect: MapExportSquareRect
    public let outputSide: Double
    public let minimumInset: Double
    /// Full-world/polar selections cannot have geographic padding without leaving Mercator's Y range.
    public let needsOuterPadding: Bool
    public var outerPadding: Double { needsOuterPadding ? minimumInset : 0 }
    public var mapSide: Double { outputSide - outerPadding * 2 }
    public var viewport: MapViewport { geographicViewport(epsilon: 0) }
    /// The selected snapshot alone should populate the export index; this epsilon only closes round-off gaps.
    public var queryViewport: MapViewport { geographicViewport(epsilon: 1e-12) }

    public static func fit(displayCoordinates: [PhotoCoordinate], outputSide: Double = 512,
                           padding: Double, additionalInset: Double = 0,
                           minimumSpan: Double = 1.0 / 32768) throws -> Self {
        guard outputSide.isFinite, outputSide > 0, padding.isFinite, padding >= 0,
              additionalInset.isFinite, additionalInset >= 0, minimumSpan.isFinite,
              minimumSpan > 0, minimumSpan <= 1 else { throw MapExportGeometryError.invalidGeometry }
        let inset = padding + additionalInset
        guard inset.isFinite, inset * 2 < outputSide else { throw MapExportGeometryError.invalidGeometry }
        guard !displayCoordinates.isEmpty else { throw MapExportGeometryError.emptySelection }
        var xs: [Double] = []
        xs.reserveCapacity(displayCoordinates.count)
        var minimumY = Double.infinity
        var maximumY = -Double.infinity
        for (index, coordinate) in displayCoordinates.enumerated() {
            if index.isMultiple(of: 256) { try Task.checkCancellation() }
            guard coordinate.isValid else { throw MapExportGeometryError.invalidGeometry }
            let point = coordinate.point
            xs.append(point.x)
            minimumY = min(minimumY, point.y)
            maximumY = max(maximumY, point.y)
        }
        xs.sort()
        try Task.checkCancellation()
        var widestGap = -1.0
        var start = xs[0]
        for index in xs.indices {
            let next = index + 1 < xs.count ? xs[index + 1] : xs[0] + 1
            let gap = next - xs[index]
            if gap > widestGap { widestGap = gap; start = next }
        }
        let horizontalSpan = max(0, 1 - widestGap)
        let unwrappedCenterX = start + horizontalSpan / 2
        let centerX = unwrappedCenterX - floor(unwrappedCenterX)
        let centerY = (minimumY + maximumY) / 2
        let baseSide = max(minimumSpan, max(horizontalSpan, maximumY - minimumY))
        let paddedSide = baseSide / (1 - inset * 2 / outputSide)
        let paddedTop = centerY - paddedSide / 2
        let canPadWithinWorld = paddedSide <= 1 && paddedTop >= 0 && paddedTop + paddedSide <= 1
        let side = canPadWithinWorld ? paddedSide : min(1, baseSide)
        let y = canPadWithinWorld ? paddedTop : min(max(0, centerY - side / 2), 1 - side)
        return Self(squareRect: MapExportSquareRect(x: centerX - side / 2, y: y, side: side),
            outputSide: outputSide, minimumInset: inset, needsOuterPadding: !canPadWithinWorld)
    }

    /// A renderer draws the map unrotated and uses the same scale on X and Y.
    public func normalizedPosition(of coordinate: PhotoCoordinate) -> MapExportPoint? {
        guard coordinate.isValid else { return nil }
        let point = coordinate.point
        let x = point.x + (squareRect.midX - point.x).rounded()
        let inset = outerPadding / outputSide
        let span = mapSide / outputSide
        return MapExportPoint(x: inset + (x - squareRect.x) / squareRect.side * span,
            y: inset + (point.y - squareRect.y) / squareRect.side * span)
    }

    private func geographicViewport(epsilon: Double) -> MapViewport {
        let top = squareRect.y - epsilon
        let bottom = squareRect.y + squareRect.side + epsilon
        // Re-querying a polar edge must include the clamped Y=0/Y=1 records after inverse projection.
        let north = epsilon > 0 && top <= 0 ? 90 : ProjectedPoint.latitude(y: max(0, top))
        let south = epsilon > 0 && bottom >= 1 ? -90 : ProjectedPoint.latitude(y: min(1, bottom))
        let longitude = ((squareRect.midX * 360).truncatingRemainder(dividingBy: 360) + 360)
            .truncatingRemainder(dividingBy: 360) - 180
        return MapViewport(latitude: (north + south) / 2, longitude: longitude,
            latitudeDelta: max(Double.ulpOfOne, north - south),
            longitudeDelta: min(360, (squareRect.side + epsilon * 2) * 360))
    }
}
