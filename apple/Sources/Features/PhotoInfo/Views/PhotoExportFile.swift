import Foundation
import CoreTransferable
import UniformTypeIdentifiers

nonisolated struct PhotoExportFile: Transferable, Sendable {
    let url: URL
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .jpeg) { item in SentTransferredFile(item.url) }
            .suggestedFileName { $0.url.lastPathComponent }
    }
}

extension UTType {
    static let fuyaoPhotosPackage = UTType(exportedAs: "ing.fuyaoskyrocket.photos.package", conformingTo: .data)
}
