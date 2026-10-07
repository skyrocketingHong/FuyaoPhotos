import Foundation
import SwiftUI
import MapKit
import PhotoMapCore
import Photos
#if os(macOS)
import AppKit
#else
import UIKit
#endif

@MainActor @Observable final class MapExportState {
    var settings: MapExportSettings
    private(set) var busy = false
    private(set) var stage = MapExportRenderStage.collecting
    private(set) var artifact: MapExportArtifact?
    var errorMessage: String?
    let selectedYear: Int?
    private let viewport: MapViewport
    private let coordinateSystem: MapCoordinateSystem
    private let library: PhotoLibraryService
    @ObservationIgnored private var operation: Task<MapExportArtifact, any Error>?
    @ObservationIgnored private var generation = UUID()

    init(session: MapSession) {
        library = session.library
        selectedYear = session.selectedYear
        coordinateSystem = session.coordinateSystem
        let region = session.currentRegion
        viewport = MapViewport(latitude: region.center.latitude, longitude: region.center.longitude,
            latitudeDelta: region.span.latitudeDelta, longitudeDelta: region.span.longitudeDelta)
        var mapOptions = session.options
        mapOptions.compass = true
        mapOptions.scale = true
        settings = MapExportSettings(mapOptions: mapOptions, displayMode: session.displayMode)
    }

    func generate(colorScheme: ColorScheme) async {
        cancel()
        let token = UUID()
        generation = token
        let settings = settings
        let scope: MapExportScope = settings.scope == .allFiltered ? .allFiltered : .viewport(viewport)
        let scheme = settings.mapOptions.appearance.colorScheme ?? colorScheme
        errorMessage = nil
        artifact = nil
        stage = .collecting
        busy = true
        let work = Task { @MainActor [weak self, library, selectedYear, coordinateSystem] in
            let snapshot = try await library.exportSnapshot(year: selectedYear, scope: scope,
                coordinateSystem: coordinateSystem)
            try Task.checkCancellation()
            guard snapshot.totalCount > 0 else { throw MapExportError.empty }
            let rendered = try await MapExportRenderer.render(snapshot: snapshot, settings: settings,
                colorScheme: scheme, thumbnail: { identifier, pixels in
                    let image = try await library.thumbnails.image(for: identifier, pixelSize: pixels)
                    try Task.checkCancellation()
                    return image.flatMap { Self.thumbnailImage($0, pixels: pixels) }
                }, progress: { [weak self] stage in
                    guard let self, self.generation == token else { return }
                    self.stage = stage
                })
            try await library.validateExportSnapshot(snapshot)
            try Task.checkCancellation()
            guard rendered.image.width == settings.resolution.rawValue,
                  rendered.image.height == settings.resolution.rawValue,
                  rendered.pngData.starts(with: [137, 80, 78, 71, 13, 10, 26, 10]) else {
                throw MapExportError.imageFailed
            }
            let url = try MapExportFileStore.write(rendered.pngData, pixels: settings.resolution.rawValue)
            return MapExportArtifact(url: url, data: rendered.pngData, image: rendered.image,
                pixelSize: settings.resolution.rawValue, totalCount: snapshot.totalCount,
                yearSpan: Self.yearSpan(minimum: snapshot.minYear, maximum: snapshot.maxYear))
        }
        operation = work
        defer {
            if generation == token { operation = nil; busy = false }
        }
        do {
            let result = try await withTaskCancellationHandler {
                try await work.value
            } onCancel: { work.cancel() }
            guard generation == token, !Task.isCancelled else { return }
            artifact = result
        } catch is CancellationError {
        } catch {
            guard generation == token, !Task.isCancelled else { return }
            errorMessage = (error as? MapExportError)?.localizedDescription
                ?? String.localized("map.export.error.imageFailed")
        }
    }

    func cancel() {
        generation = UUID()
        operation?.cancel()
        operation = nil
        busy = false
    }

    func invalidate() {
        cancel()
        artifact = nil
        errorMessage = nil
    }

    private static func yearSpan(minimum: Int?, maximum: Int?) -> String {
        guard let minimum, let maximum else { return String.localized("map.export.header.years.unknown") }
        return minimum == maximum ? String(minimum) : "\(minimum)-\(maximum)"
    }

    private static func thumbnailImage(_ image: NativeImage, pixels: Int) -> CGImage? {
#if os(macOS)
        return image.cgImage(forProposedRect: nil, context: nil, hints: nil)
#else
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.preferredRange = .standard
        let side = CGFloat(max(32, min(2048, pixels)))
        let target = CGSize(width: side, height: side)
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            let factor = max(side / max(1, image.size.width), side / max(1, image.size.height))
            let size = CGSize(width: image.size.width * factor, height: image.size.height * factor)
            image.draw(in: CGRect(x: (side - size.width) / 2, y: (side - size.height) / 2,
                                  width: size.width, height: size.height))
        }.cgImage
#endif
    }
}

private enum MapExportFileStore {
    private static var directory: URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("FuyaoMapExports", isDirectory: true)
    }

    static func write(_ data: Data, pixels: Int) throws -> URL {
        let manager = FileManager.default
        try manager.createDirectory(at: directory, withIntermediateDirectories: true,
            attributes: [.posixPermissions: 0o700])
        removeExpiredFiles()
        let folder = directory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try manager.createDirectory(at: folder, withIntermediateDirectories: false,
            attributes: [.posixPermissions: 0o700])
        let url = folder.appendingPathComponent("FuyaoPhotos-Map-\(pixels).png")
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            try? manager.removeItem(at: folder)
            throw error
        }
    }

    // Share extensions may still read a prepared file after the options sheet closes.
    private static func removeExpiredFiles() {
        let keys: Set<URLResourceKey> = [.isDirectoryKey, .isSymbolicLinkKey, .contentModificationDateKey]
        let cutoff = Date().addingTimeInterval(-24 * 60 * 60)
        guard let entries = try? FileManager.default.contentsOfDirectory(at: directory,
            includingPropertiesForKeys: Array(keys), options: [.skipsHiddenFiles]) else { return }
        for entry in entries where UUID(uuidString: entry.lastPathComponent) != nil {
            guard let values = try? entry.resourceValues(forKeys: keys), values.isDirectory == true,
                  values.isSymbolicLink != true, let modified = values.contentModificationDate,
                  modified < cutoff else { continue }
            try? FileManager.default.removeItem(at: entry)
        }
    }
}
