import SwiftUI
import UniformTypeIdentifiers

struct PhotoInformationHeading: View {
    let name: String
    let fileExtension: String
    let fileSize: Int

    private var fileSummary: String {
        let type = UTType(filenameExtension: fileExtension)?.localizedDescription ?? fileExtension.uppercased()
        let size = Int64(fileSize).formatted(.byteCount(style: .file))
        return type.isEmpty ? size : "\(type) · \(size)"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(name)
                .font(.headline)
                .lineLimit(1)
                .truncationMode(.middle)
                .textSelection(.enabled)
            Text(verbatim: fileSummary)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
