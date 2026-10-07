import Foundation
import Testing
@testable import PhotoMapCore

struct MapExportTests {
    private func photo(_ id: String, _ latitude: Double, _ longitude: Double, year: Int? = 2025) -> PhotoCoordinate {
        PhotoCoordinate(id: id, latitude: latitude, longitude: longitude, creationDate: nil, year: year)
    }

    @Test func allFilteredExportPreservesEveryRecordAndOutlierBeyondDisplayBudgets() async throws {
        var photos = (0..<4096).map { photo("p\($0)", 10 + Double($0 % 80) / 100,
            Double($0 % 120) / 100, year: $0.isMultiple(of: 2) ? 2020 : 2021) }
        photos.append(photo("outlier", 62, 170, year: 2025))
        photos.append(photo("undated", 20, 10, year: nil))
        let index = PhotoSpatialIndex()
        try await index.replace(with: photos)
        let result = try await index.exportSnapshot(year: nil)
        #expect(result.totalCount == photos.count)
        #expect(result.minYear == 2020 && result.maxYear == 2025)
        #expect(result.displayCoordinates.contains(photos[4096]))
        let selectedYear = try await index.exportSnapshot(year: 2021)
        #expect(selectedYear.totalCount == 2048 && selectedYear.minYear == 2021 && selectedYear.maxYear == 2021)
        let frame = try MapExportFrame.fit(displayCoordinates: result.displayCoordinates, padding: 32)
        try assertInset(frame, coordinates: result.displayCoordinates)
        let clustered = try await index.query(viewport: frame.queryViewport, width: 512, height: 512, year: nil, limit: 1)
        #expect(clustered.clusters.count == 1 && clustered.totalCount == result.totalCount)
    }

    @Test func viewportSelectionUsesDisplayedCoordinatesOnceAndRetainsRawIndexRecords() async throws {
        let raw = photo("shanghai", 31.2304, 121.4737, year: 2024)
        let other = photo("other", 10, 10, year: 2025)
        let index = PhotoSpatialIndex(coordinateSystem: .gcj02)
        try await index.replace(with: [raw, other])
        let expected = try #require(raw.projected(to: .gcj02))
        let scope = MapViewport(latitude: expected.latitude, longitude: expected.longitude,
            latitudeDelta: 0.00001, longitudeDelta: 0.00001)
        let result = try await index.exportSnapshot(year: nil, scope: .viewport(scope))
        #expect(result.coordinateSystem == .gcj02)
        #expect(result.displayCoordinates == [expected])
        #expect(result.minYear == 2024 && result.maxYear == 2024)
        #expect(await index.coordinate(for: raw.id) == raw)
        let oldRevision = result.revision
        await index.apply(upserting: [], removing: [raw.id])
        let changed = try await index.exportSnapshot(year: nil)
        #expect(changed.totalCount == 1 && changed.revision > oldRevision)
    }

    @Test func currentViewportCanCrossTheDateLineWithoutSelectingTheOppositeHemisphere() async throws {
        let values = [photo("east", 5, 179.5, year: 2023), photo("west", 5, -179.5, year: 2024), photo("middle", 5, 0)]
        let index = PhotoSpatialIndex()
        try await index.replace(with: values)
        let scope = MapViewport(latitude: 5, longitude: 180, latitudeDelta: 4, longitudeDelta: 4)
        let selected = try await index.exportSnapshot(year: nil, scope: .viewport(scope))
        #expect(Set(selected.displayCoordinates.map(\.id)) == ["east", "west"])
        #expect(selected.minYear == 2023 && selected.maxYear == 2024)
    }

    @Test func viewportAndYearFiltersIntersectWithoutInventingUnknownYearBounds() async throws {
        let values = [photo("selected", 5, 5, year: 2023), photo("otherYear", 5, 5, year: 2024),
                      photo("outside", 40, 40, year: 2023), photo("undated", -20, -20, year: nil)]
        let index = PhotoSpatialIndex()
        try await index.replace(with: values)
        let viewport = MapViewport(latitude: 5, longitude: 5, latitudeDelta: 2, longitudeDelta: 2)
        let selected = try await index.exportSnapshot(year: 2023, scope: .viewport(viewport))
        #expect(selected.displayCoordinates == [values[0]])
        let empty = try await index.exportSnapshot(year: 2000)
        #expect(empty.totalCount == 0 && empty.minYear == nil && empty.maxYear == nil)
        let undatedViewport = MapViewport(latitude: -20, longitude: -20, latitudeDelta: 2, longitudeDelta: 2)
        let undated = try await index.exportSnapshot(year: nil, scope: .viewport(undatedViewport))
        #expect(undated.displayCoordinates == [values[3]])
        #expect(undated.minYear == nil && undated.maxYear == nil)
    }

    @Test func squareCloudKeepsTheSameBasePaddingAndMarkerExtentOnEveryEdge() throws {
        let values = [
            photo("northwest", ProjectedPoint.latitude(y: 0.4), -36),
            photo("northeast", ProjectedPoint.latitude(y: 0.4), 36),
            photo("southwest", ProjectedPoint.latitude(y: 0.6), -36),
            photo("southeast", ProjectedPoint.latitude(y: 0.6), 36)
        ]
        let frame = try MapExportFrame.fit(displayCoordinates: values, outputSide: 1024,
            padding: 40, additionalInset: 24)
        let positions = try values.map { try #require(frame.normalizedPosition(of: $0)) }
        let inset = 64.0 / 1024
        #expect(!frame.needsOuterPadding)
        #expect(abs(positions[0].x - inset) < 0.000001 && abs(positions[0].y - inset) < 0.000001)
        #expect(abs(positions[3].x - (1 - inset)) < 0.000001 && abs(positions[3].y - (1 - inset)) < 0.000001)
        try assertInset(frame, coordinates: values)
    }

    @Test func squareFrameUsesTheShortestDateLineEnvelopeAndOneUndistortedScale() throws {
        let values = [photo("east", 0, 179), photo("west", 0, -179)]
        let frame = try MapExportFrame.fit(displayCoordinates: values, padding: 32, additionalInset: 12)
        #expect(frame.squareRect.side < 0.01)
        #expect(!frame.needsOuterPadding)
        #expect(abs(abs(frame.viewport.longitude) - 180) < 0.000001)
        let points = try values.map { try #require(frame.normalizedPosition(of: $0)) }
        #expect(abs(points[0].y - 0.5) < 0.000001 && abs(points[1].y - 0.5) < 0.000001)
        #expect(abs(min(points[0].x, points[1].x) - 44.0 / 512) < 0.000001)
        #expect(abs(max(points[0].x, points[1].x) - (1 - 44.0 / 512)) < 0.000001)
        try assertInset(frame, coordinates: values)
    }

    @Test func onePointAndCoincidentPhotosHaveAFiniteSquareFrame() throws {
        let values = [photo("a", 40, -75), photo("b", 40, -75)]
        let frame = try MapExportFrame.fit(displayCoordinates: values, padding: 40)
        #expect(frame.squareRect.side > 0 && frame.squareRect.side.isFinite)
        #expect(frame.viewport.longitude == -75)
        let point = try #require(frame.normalizedPosition(of: values[0]))
        #expect(abs(point.x - 0.5) < 0.000001 && abs(point.y - 0.5) < 0.000001)
        #expect(frame.normalizedPosition(of: values[1]) == point)
    }

    @Test func polarAndWorldSelectionsUseUniformOuterPaddingWithoutDroppingPoints() async throws {
        for values in [[photo("north", 90, 0)], [photo("south", -90, 179)],
                       [photo("north", 90, 179), photo("south", -90, -179), photo("equator", 0, 0)]] {
            let frame = try MapExportFrame.fit(displayCoordinates: values, padding: 32, additionalInset: 8)
            #expect(frame.needsOuterPadding && frame.outerPadding == 40)
            #expect(frame.squareRect.y >= 0 && frame.squareRect.y + frame.squareRect.side <= 1)
            #expect(frame.mapSide == 432)
            try assertInset(frame, coordinates: values)
            let index = PhotoSpatialIndex()
            try await index.replace(with: values)
            let visible = try await index.query(viewport: frame.queryViewport, width: 512, height: 512, year: nil)
            #expect(visible.totalCount == values.count)
        }
    }

    @Test func everyFittedExtremumRemainsIncludedWhenExportDataIsReclustered() async throws {
        let selections = [
            [photo("a", 84, -180), photo("b", 84.5, 180), photo("c", 83, -179)],
            [photo("a", -70, -130), photo("b", 70, 130), photo("c", 0, 0)],
            [photo("a", 10, 0), photo("b", 10, 120), photo("c", 10, -120)]
        ]
        for values in selections {
            let frame = try MapExportFrame.fit(displayCoordinates: values, padding: 64)
            try assertInset(frame, coordinates: values)
            let index = PhotoSpatialIndex()
            try await index.replace(with: values)
            let query = try await index.query(viewport: frame.queryViewport, width: 2048, height: 2048,
                year: nil, cellSize: 64, limit: 1200)
            #expect(query.totalCount == values.count)
        }
    }

    @Test func invalidOrEmptyGeometryIsReportedInsteadOfInventingAMapArea() {
        #expect(throws: MapExportGeometryError.emptySelection) {
            try MapExportFrame.fit(displayCoordinates: [], padding: 32)
        }
        #expect(throws: MapExportGeometryError.invalidGeometry) {
            try MapExportFrame.fit(displayCoordinates: [photo("a", 0, 0)], padding: 256)
        }
        #expect(throws: MapExportGeometryError.invalidGeometry) {
            try MapExportFrame.fit(displayCoordinates: [photo("a", .nan, 0)], padding: 32)
        }
    }

    private func assertInset(_ frame: MapExportFrame, coordinates: [PhotoCoordinate]) throws {
        let inset = frame.minimumInset / frame.outputSide
        for coordinate in coordinates {
            let point = try #require(frame.normalizedPosition(of: coordinate))
            #expect(point.x >= inset - 0.000001 && point.x <= 1 - inset + 0.000001)
            #expect(point.y >= inset - 0.000001 && point.y <= 1 - inset + 0.000001)
        }
    }
}
