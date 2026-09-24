import Foundation
import Testing
@testable import PhotoMapCore

private let world = MapViewport(latitude: 0, longitude: 0, latitudeDelta: 180, longitudeDelta: 360)
private func photo(_ id: String, _ lat: Double = 0, _ lon: Double = 0, year: Int? = 2026) -> PhotoCoordinate {
    PhotoCoordinate(id: id, latitude: lat, longitude: lon, creationDate: nil, year: year)
}

struct SpatialIndexTests {
    @Test func singlePhotoBoundsRemainCenteredOnThePhoto() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: [photo("single", 30, 120)])
        let bounds = try #require(try await index.bounds(year: nil))
        #expect(abs(bounds.longitude - 120) < 1e-9)
        #expect(abs(bounds.latitude - 30) < 1e-9)
        #expect(bounds.longitudeDelta > 0)
    }

    @Test func extremeFiniteViewportSizeCannotOverflowIntegerConversion() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: [photo("single")])
        let viewport = MapViewport(latitude: 0, longitude: 0, latitudeDelta: 1e-9, longitudeDelta: 1e-9)
        let result = try await index.query(viewport: viewport, width: .greatestFiniteMagnitude,
                                           height: .greatestFiniteMagnitude, year: nil)
        #expect(result.totalCount == 1)
    }

    @Test func bulkChangesRebuildSummariesWithoutLosingMembers() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: (0..<1000).map { photo("p\($0)") })
        await index.apply(upserting: (0..<100).map { photo("new\($0)", 10, 20, year: 2025) },
                          removing: (0..<500).map { "p\($0)" })
        let all = try await index.query(viewport: world, width: 400, height: 800, year: nil)
        let old = try await index.query(viewport: world, width: 400, height: 800, year: 2026)
        #expect(all.totalCount == 600)
        #expect(old.totalCount == 500)
    }

    @Test func emptyAndInvalidInput() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: [photo("nan", .nan), photo("bad", 91), photo("", 0)])
        let result = try await index.query(viewport: world, width: 400, height: 800, year: nil)
        #expect(result.totalCount == 0)
        #expect(try await index.bounds(year: nil) == nil)
    }

    @Test func zeroCoordinatesAndMissingDateRemainVisible() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: [photo("equator", 0, 12), photo("meridian", 20, 0), photo("unknown", 0, 0, year: nil)])
        let result = try await index.query(viewport: world, width: 400, height: 800, year: nil)
        let dated = try await index.query(viewport: world, width: 400, height: 800, year: 2026)
        #expect(result.totalCount == 3)
        #expect(dated.totalCount == 2)
    }

    @Test func displayBudgetPreservesAllPhotos() async throws {
        let index = PhotoSpatialIndex()
        let records = (0..<10_000).map { photo("p\($0)", Double($0 % 160) - 80, Double($0 % 350) - 175) }
        try await index.replace(with: records)
        let result = try await index.query(viewport: world, width: 1800, height: 1800, year: nil, cellSize: 10, limit: 30)
        #expect(result.totalCount == 10_000)
        #expect(result.clusters.count <= 30)
        #expect(Set(result.clusters.map(\.id)).count == result.clusters.count)
    }

    @Test func antimeridianQueryAndBounds() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: [photo("east", 1, 179.8), photo("west", 1, -179.8), photo("away", 1, 0)])
        let viewport = MapViewport(latitude: 1, longitude: 180, latitudeDelta: 4, longitudeDelta: 2)
        let result = try await index.query(viewport: viewport, width: 400, height: 800, year: nil)
        #expect(result.totalCount == 2)
        await index.apply(upserting: [], removing: ["away"])
        let bounds = try #require(try await index.bounds(year: nil))
        #expect(bounds.longitudeDelta < 1)
        #expect(abs(bounds.longitude) > 179)
    }

    @Test func polarCoordinatesAndWorldEdgesAreNotDropped() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: [photo("north", 90, 180), photo("south", -90, -180)])
        #expect(try await index.query(viewport: world, width: 400, height: 800, year: nil).totalCount == 2)
    }

    @Test func stableIDsAndRenderedEquality() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: [photo("a", 20, 20), photo("b", 20.001, 20.001)])
        let first = try await index.query(viewport: world, width: 1, height: 1, year: nil)
        await index.apply(upserting: [photo("c", 20.002, 20.002)], removing: [])
        let second = try await index.query(viewport: world, width: 1, height: 1, year: nil)
        #expect(first.clusters[0].id == second.clusters[0].id)
        #expect(first.clusters[0] != second.clusters[0])
        #expect(second.clusters[0].representativeID == "a")
    }

    @Test func deltaMovesAndRemovalsUpdateAllSummaries() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: (0..<200).map { photo("p\($0)", 0, 0) })
        await index.apply(upserting: [photo("p0", 40, 100, year: 2025)], removing: ["p1"])
        #expect(try await index.query(viewport: world, width: 500, height: 500, year: nil).totalCount == 199)
        #expect(try await index.query(viewport: world, width: 500, height: 500, year: 2026).totalCount == 198)
        #expect(try await index.query(viewport: world, width: 500, height: 500, year: 2025).totalCount == 1)
    }

    @Test func groupMembershipPaginationIsCompleteAndStable() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: (0..<140).map { photo(String(format: "%03d", $0)) })
        let group = try #require(try await index.query(viewport: world, width: 1, height: 1, year: nil).clusters.first)
        var ids: [String] = []
        for offset in stride(from: 0, to: 140, by: 60) {
            ids += try await index.members(of: group.cell, viewport: world, year: nil, offset: offset, limit: 60).map(\.id)
        }
        #expect(ids.count == 140)
        #expect(Set(ids).count == 140)
        #expect(ids == ids.sorted())
    }

    @Test func replacementDeduplicatesAndCancelledWorkDoesNotPublish() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: [photo("a"), photo("a", 10)])
        #expect(await index.snapshot().count == 1)
        let task = Task {
            try Task.checkCancellation()
            try await index.replace(with: (0..<100_000).map { photo("new\($0)") })
        }
        task.cancel()
        do { try await task.value; Issue.record("Cancelled replacement published") } catch is CancellationError {} catch { throw error }
        #expect(await index.snapshot().map(\.id) == ["a"])
    }

    @Test func indexedCountsMatchBruteForceQueries() async throws {
        let records = (0..<5000).map { i in
            let latitude = Double((i * 37) % 16000) / 100.0 - 80.0
            let longitude = Double((i * 137) % 36000) / 100.0 - 180.0
            return photo("p\(i)", latitude, longitude, year: i % 2 == 0 ? 2025 : 2026)
        }
        let index = PhotoSpatialIndex()
        try await index.replace(with: records)
        for longitude in [-179.0, -120, 0, 100, 179] {
            let viewport = MapViewport(latitude: 20, longitude: longitude, latitudeDelta: 60, longitudeDelta: 80)
            let expected = records.filter { item in item.year == 2026 && viewport.rectangles.contains { $0.contains(item.point) } }.count
            let actual = try await index.query(viewport: viewport, width: 500, height: 900, year: 2026, limit: 40)
            #expect(actual.totalCount == expected)
        }
    }

    @Test func worldQueryUsesSummariesInsteadOfScanningEveryPhoto() async throws {
        let index = PhotoSpatialIndex()
        try await index.replace(with: (0..<100_000).map { photo("p\($0)", Double($0 % 160) - 80, Double(($0 * 7) % 350) - 175) })
        let result = try await index.query(viewport: world, width: 1, height: 1, year: nil)
        #expect(result.totalCount == 100_000)
        #expect(result.visitedNodes == 1)
    }

    @Test func cacheEnforcesBothBudgetsAndRefreshesLRU() {
        var cache = CostLimitedCache<String, Int>(countLimit: 2, costLimit: 10)
        cache.insert(1, for: "a", cost: 4)
        cache.insert(2, for: "b", cost: 4)
        #expect(cache.value(for: "a") == 1)
        cache.insert(3, for: "c", cost: 4)
        #expect(cache.value(for: "b") == nil)
        #expect(cache.totalCost == 8)
        cache.insert(4, for: "d", cost: 11)
        #expect(cache.value(for: "d") == nil)
        cache.remove(where: { $0 == "a" })
        #expect(cache.totalCost == 4)
        cache.removeAll()
        #expect(cache.count == 0 && cache.totalCost == 0)
    }
}
