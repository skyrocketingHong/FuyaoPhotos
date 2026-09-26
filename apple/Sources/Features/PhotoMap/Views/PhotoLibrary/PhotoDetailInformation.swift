import SwiftUI
import Photos
import CoreLocation

struct PhotoDetailInformation: View {
    let asset: PHAsset?
    let document: CardDocument?
    let coordinate: CLLocationCoordinate2D?
    @State private var details: PhotoTechnicalDetails?
    @State private var report: MediaMetadataReport?

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
        MediaMetadataReportSection(report: report, prominentHeaders: true)
        .task(id: document?.sourceURL) {
            details = nil
            report = nil
            guard let url = document?.sourceURL else { return }
            let loaded = await PhotoTechnicalDetailsReader.shared.read(url)
            guard !Task.isCancelled else { return }
            details = loaded
            let isLive = asset?.mediaSubtypes.contains(.photoLive) ?? document?.isLive ?? false
            let loadedReport = await Task.detached(priority: .userInitiated) {
                MediaMetadataReportReader.read(url: url, isLivePhoto: isLive)
            }.value
            guard !Task.isCancelled else { return }
            report = loadedReport
        }
    }
}
