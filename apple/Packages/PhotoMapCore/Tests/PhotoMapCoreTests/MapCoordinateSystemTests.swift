import Testing
@testable import PhotoMapCore

struct MapCoordinateSystemTests {
    private let original = PhotoCoordinate(id: "fixture", latitude: 39.915, longitude: 116.404,
                                            creationDate: nil, year: 2026)
    // Independently evaluated using FuyaoHomepage's wgs84ToGcj02 implementation.
    private let displayed = GeographicCoordinate(latitude: 39.91640428150164, longitude: 116.41024449916938)!

    @Test func conversionMatchesHomepageReference() throws {
        let source = try #require(GeographicCoordinate(latitude: original.latitude, longitude: original.longitude))
        let result = source.converted(from: .wgs84, to: .gcj02)
        #expect(abs(result.latitude - displayed.latitude) < 1e-10)
        #expect(abs(result.longitude - displayed.longitude) < 1e-10)
        #expect(displayed.converted(from: .gcj02, to: .gcj02) == displayed)
        #expect(source.converted(from: .wgs84, to: .wgs84) == source)
    }

    @Test(arguments: [(39.915, 116.404), (30.25, 120.15), (31.2, 121.4), (22.55, 114.05),
                      (22.3, 114.17), (25.03, 121.56), (35.68, 139.69), (51.5074, -0.1278)])
    func roundTripDoesNotAccumulateOffset(_ point: (Double, Double)) throws {
        let source = try #require(GeographicCoordinate(latitude: point.0, longitude: point.1))
        let restored = source.converted(from: .wgs84, to: .gcj02).converted(from: .gcj02, to: .wgs84)
        #expect(abs(restored.latitude - source.latitude) < 1e-8)
        #expect(abs(restored.longitude - source.longitude) < 1e-8)
    }

    @Test(arguments: [(0.0, 0.0), (90, 180), (-90, -180)])
    func unsetAndPolarCoordinatesRemainFinite(_ point: (Double, Double)) throws {
        let source = try #require(GeographicCoordinate(latitude: point.0, longitude: point.1))
        #expect(source.converted(from: .wgs84, to: .gcj02) == source)
        #expect(source.converted(from: .gcj02, to: .wgs84) == source)
    }

    @Test(arguments: [
        (22.55, 114.05, 22.547268202722883, 114.0551039312208),
        (22.3, 114.17, 22.297254174058057, 114.17496420873648),
        (25.03, 121.56, 25.02707395352421, 121.56378302087622),
        (35.68, 139.69, 35.68028010637725, 139.69416231821026),
        (51.5074, -0.1278, 51.50472595478352, -0.10932835467395463)
    ])
    func amapBranchMatchesHomepageOutsideTheMainlandBoundingBox(_ point: (Double, Double, Double, Double)) throws {
        let source = try #require(GeographicCoordinate(latitude: point.0, longitude: point.1))
        let result = source.converted(from: .wgs84, to: .gcj02)
        #expect(abs(result.latitude - point.2) < 1e-10)
        #expect(abs(result.longitude - point.3) < 1e-10)
        #expect(source.converted(from: .wgs84, to: .wgs84) == source)
    }

    @Test func rejectsInvalidValuesWithoutDroppingValidZeroes() {
        #expect(GeographicCoordinate(latitude: .nan, longitude: 120) == nil)
        #expect(GeographicCoordinate(latitude: 30, longitude: .infinity) == nil)
        #expect(GeographicCoordinate(latitude: 91, longitude: 120) == nil)
        #expect(GeographicCoordinate(latitude: 30, longitude: -181) == nil)
        #expect(GeographicCoordinate(latitude: 0, longitude: 0) != nil)
    }

    @Test func displayedQueryAndMembersUseTheSameCoordinateSystem() async throws {
        let index = PhotoSpatialIndex(coordinateSystem: .gcj02)
        try await index.replace(with: [original])
        let viewport = MapViewport(latitude: displayed.latitude, longitude: displayed.longitude,
                                   latitudeDelta: 0.0002, longitudeDelta: 0.0002)
        for cellSize in [24.0, 64] {
            let result = try await index.query(viewport: viewport, width: 400, height: 800, year: nil, cellSize: cellSize)
            #expect(result.totalCount == 1)
            let cluster = try #require(result.clusters.first)
            #expect(abs(cluster.latitude - displayed.latitude) < 1e-8)
            #expect(abs(cluster.longitude - displayed.longitude) < 1e-8)
            let members = try await index.members(of: cluster.cell, viewport: viewport, year: nil, offset: 0, limit: 60)
            #expect(members == [original])
        }
        let rawViewport = MapViewport(latitude: original.latitude, longitude: original.longitude,
                                      latitudeDelta: 0.0002, longitudeDelta: 0.0002)
        #expect(try await index.query(viewport: rawViewport, width: 400, height: 800, year: nil).totalCount == 0)
        #expect(await index.snapshot() == [original])
        #expect(await index.coordinate(for: original.id) == original)
        let bounds = try #require(try await index.bounds(year: nil))
        #expect(abs(bounds.latitude - displayed.latitude) < 1e-8)
        #expect(abs(bounds.longitude - displayed.longitude) < 1e-8)
    }

    @Test func refreshAndIncrementalUpdatesDoNotApplyTheOffsetTwice() async throws {
        let index = PhotoSpatialIndex(coordinateSystem: .gcj02)
        try await index.replace(with: [original])
        try await index.replace(with: await index.snapshot())
        await index.apply(upserting: [original], removing: [])
        let viewport = MapViewport(latitude: displayed.latitude, longitude: displayed.longitude,
                                   latitudeDelta: 0.0002, longitudeDelta: 0.0002)
        #expect(try await index.query(viewport: viewport, width: 400, height: 800, year: nil).totalCount == 1)
        let batch = (0..<70).map {
            PhotoCoordinate(id: "bulk-\($0)", latitude: original.latitude, longitude: original.longitude,
                            creationDate: nil, year: 2026)
        }
        await index.apply(upserting: batch, removing: [original.id])
        #expect(try await index.query(viewport: viewport, width: 400, height: 800, year: nil).totalCount == 70)
        #expect(await index.snapshot().allSatisfy { $0.latitude == original.latitude && $0.longitude == original.longitude })
        await index.apply(upserting: [], removing: batch.map(\.id))
        #expect(try await index.query(viewport: viewport, width: 400, height: 800, year: nil).totalCount == 0)
    }
}
