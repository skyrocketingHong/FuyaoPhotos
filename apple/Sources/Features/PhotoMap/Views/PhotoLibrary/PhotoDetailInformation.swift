import SwiftUI
import Photos
import CoreLocation

struct PhotoDetailInformation: View {
    let asset: PHAsset?
    let document: CardDocument?
    let coordinate: CLLocationCoordinate2D?
    @State private var details: PhotoTechnicalDetails?

    var body: some View {
        let groups = PhotoInformationFacts.grouped(asset: asset, metadata: document?.metadata,
                                                   details: details, coordinate: coordinate)
        ForEach(groups, id: \.group) { section in
            Section {
                ForEach(section.rows) { row in
                    PhotoInformationRow(title: LocalizedStringKey(row.id), value: row.value,
                                        monospacedDigits: row.numeric)
                }
            } header: {
                Text("photo.info.group.\(section.group.rawValue)")
                    .font(.title3)
                    .bold()
                    .textCase(nil)
            }
        }
        .task(id: document?.sourceURL) {
            details = nil
            guard let url = document?.sourceURL else { return }
            let loaded = await PhotoTechnicalDetailsReader.shared.read(url)
            guard !Task.isCancelled else { return }
            details = loaded
        }
    }
}
