import SwiftUI
import UniformTypeIdentifiers

struct LensProfileFileDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    let data: Data
    init(_ file: LensProfileFile) throws { data = try file.encoded() }
    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else { throw LensProfileFileError.invalid }
        _ = try LensProfileFile.decode(data); self.data = data
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}
