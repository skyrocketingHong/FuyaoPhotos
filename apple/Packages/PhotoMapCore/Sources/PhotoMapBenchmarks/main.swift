import Foundation
import PhotoMapCore

@main struct Benchmarks {
    static func main() async throws {
        let clock = ContinuousClock()
        print("count,distribution,index_ms,query_p50_ms,query_p95_ms,max_markers")
        for count in [1_000, 10_000, 50_000, 100_000] {
            for dense in [false, true] {
                var random: UInt64 = 42
                func next() -> Double {
                    random = random &* 6364136223846793005 &+ 1442695040888963407
                    return Double(random >> 11) / Double(UInt64(1) << 53)
                }
                let photos = (0..<count).map { i in
                    PhotoCoordinate(id: "p\(i)", latitude: dense ? 30 + next() * 0.1 : next() * 160 - 80,
                                    longitude: dense ? 120 + next() * 0.1 : next() * 360 - 180,
                                    creationDate: nil, year: i % 2 == 0 ? 2025 : 2026)
                }
                let index = PhotoSpatialIndex()
                let begin = clock.now
                try await index.replace(with: photos)
                let build = milliseconds(begin.duration(to: clock.now))
                var times: [Double] = []
                var maxMarkers = 0
                for pass in 0..<60 {
                    let viewport = MapViewport(latitude: dense ? 30.05 : 0,
                                               longitude: dense ? 120.03 + Double(pass % 10) * 0.004 : Double(pass % 10) - 5,
                                               latitudeDelta: dense ? 0.08 : 120,
                                               longitudeDelta: dense ? 0.08 : 300)
                    let start = clock.now
                    let result = try await index.query(viewport: viewport, width: 430, height: 800, year: nil)
                    times.append(milliseconds(start.duration(to: clock.now)))
                    maxMarkers = max(maxMarkers, result.clusters.count)
                }
                times.sort()
                print(String(format: "%d,%@,%.3f,%.3f,%.3f,%d", count, dense ? "dense" : "dispersed", build,
                             times[times.count / 2], times[Int(Double(times.count - 1) * 0.95)], maxMarkers))
            }
        }
    }

    static func milliseconds(_ duration: Duration) -> Double {
        let parts = duration.components
        return Double(parts.seconds) * 1000 + Double(parts.attoseconds) / 1e15
    }
}
