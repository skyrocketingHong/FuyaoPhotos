import Foundation
import SwiftUI
import CoreGraphics
import CoreTransferable
import UniformTypeIdentifiers

enum MapExportScopeChoice: String, CaseIterable, Identifiable {
    case allFiltered
    case currentViewport
    var id: Self { self }
    var title: LocalizedStringKey {
        switch self {
        case .allFiltered: "map.export.scope.all"
        case .currentViewport: "map.export.scope.viewport"
        }
    }
}

enum MapExportResolution: Int, CaseIterable, Identifiable {
    case small = 1024
    case standard = 2048
    case large = 4096
    var id: Self { self }
}

struct MapExportSettings: Equatable {
    var scope: MapExportScopeChoice = .allFiltered
    var resolution: MapExportResolution = .standard
    var mapOptions: MapOptions
    var displayMode: MapDisplayMode
}

enum MapExportRenderStage: Equatable {
    case collecting
    case map
    case markers(completed: Int, total: Int)
    case compositing

    var description: String {
        switch self {
        case .collecting: String.localized("map.export.stage.collecting")
        case .map: String.localized("map.export.stage.map")
        case let .markers(completed, total):
            String(format: String.localized("map.export.stage.markers"), Int64(completed), Int64(total))
        case .compositing: String.localized("map.export.stage.compositing")
        }
    }
}

enum MapExportError: String, LocalizedError {
    case empty, changed, permission, mapUnavailable, imageFailed, unsupportedExtent
    var errorDescription: String? { String.localized("map.export.error." + rawValue) }
}

struct MapExportRenderedImage {
    let pngData: Data
    let image: CGImage
}

nonisolated struct MapExportArtifact: Transferable {
    let url: URL
    let data: Data
    let image: CGImage
    let pixelSize: Int
    let totalCount: Int
    let yearSpan: String

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .png) { value in
            SentTransferredFile(value.url)
        }
        .suggestedFileName { $0.url.lastPathComponent }
    }
}
