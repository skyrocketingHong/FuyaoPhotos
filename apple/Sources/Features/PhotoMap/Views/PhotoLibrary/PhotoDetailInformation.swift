import SwiftUI
import Photos

struct PhotoDetailInformation: View {
    let location: PhotoLocation
    let metadata: CardPhotoMetadata?
    var body: some View {
        Section("photo.detail.photo.properties") {
            if let date = location.creationDate {
                LabeledContent("photo.detail.creation.time") { Text(date, format: .dateTime.year().month().day().hour().minute()) }
            }
            LabeledContent("photo.dimensions", value: "\(location.asset.pixelWidth) × \(location.asset.pixelHeight)")
            if let metadata {
                LabeledContent("photo.file.size") { Text(Int64(metadata.fileSize), format: .byteCount(style: .file)) }
                ForEach([CardField.device, .camera, .focalLength, .aperture, .exposure, .iso], id: \.self) { field in
                    if !metadata.card[field].isEmpty {
                        LabeledContent(LocalizedStringKey(field.titleKey)) {
                            Text(metadata.card[field]).textSelection(.enabled)
                        }
                    }
                }
                if metadata.hdr { Label { Text("HDR") } icon: { Image("HDR") } }
            }
        }
        Section("photo.detail.location.info") {
            LabeledContent("photo.detail.latitude") {
                Text(location.coordinate.latitude, format: .number.precision(.fractionLength(6))).monospacedDigit().textSelection(.enabled)
            }
            LabeledContent("photo.detail.longitude") {
                Text(location.coordinate.longitude, format: .number.precision(.fractionLength(6))).monospacedDigit().textSelection(.enabled)
            }
        }
    }
}
