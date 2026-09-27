import SwiftUI
import Photos
import CoreLocation

struct PhotoDetailInformation: View {
    let asset: PHAsset?
    let document: CardDocument?
    let coordinate: CLLocationCoordinate2D?
    @State private var details: PhotoTechnicalDetails?
    @State private var report: MediaMetadataReport?
    @State private var loadedURL: URL?

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
                Text(LocalizedStringKey("photo.info.group." + section.group.rawValue))
                    .textCase(nil)
            }
        }
        MediaMetadataReportSection(report: report, showsDescriptions: true)
        .task(id: document?.sourceURL) {
            guard let url = document?.sourceURL else { return }
            // List can recreate its offscreen sections. Keep the completed read so
            // scrolling does not remove rows and reset the list's content offset.
            guard loadedURL != url else { return }
            let loaded = await PhotoTechnicalDetailsReader.shared.read(url)
            guard !Task.isCancelled else { return }
            let isLive = asset?.mediaSubtypes.contains(.photoLive) ?? document?.isLive ?? false
            let loadedReport = await Task.detached(priority: .userInitiated) {
                MediaMetadataReportReader.read(url: url, isLivePhoto: isLive)
            }.value
            guard !Task.isCancelled else { return }
            loadedURL = url
            details = loaded
            report = loadedReport
        }
    }
}
