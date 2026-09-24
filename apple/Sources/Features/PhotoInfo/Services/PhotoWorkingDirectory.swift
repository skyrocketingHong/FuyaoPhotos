import Foundation

/// The document owns only its private copy, never the Photos library's original resource.
nonisolated final class PhotoWorkingDirectory {
    let url: URL
    init(_ url: URL) { self.url = url }
    deinit { try? FileManager.default.removeItem(at: url) }
}
