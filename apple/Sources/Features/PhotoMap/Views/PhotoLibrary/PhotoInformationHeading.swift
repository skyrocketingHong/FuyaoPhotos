import SwiftUI
import UniformTypeIdentifiers

struct PhotoInformationHeading: View {
    let name: String
    let fileExtension: String
    let fileSize: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(name)
                .font(.title3)
                .bold()
                .textSelection(.enabled)
            HStack(spacing: 5) {
                Text(UTType(filenameExtension: fileExtension)?.localizedDescription ?? fileExtension.uppercased())
                Text("·")
                Text(Int64(fileSize), format: .byteCount(style: .file))
            }
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
