import Foundation

nonisolated struct PhotoSourceResources: Sendable {
    let image: URL
    let movie: URL?
    let originalName: String
    let assetIdentifier: String?
}
