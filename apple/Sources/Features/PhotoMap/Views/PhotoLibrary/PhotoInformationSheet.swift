import SwiftUI
import Photos

struct PhotoInformationSheet: View {
    let document: CardDocument
    @Environment(\.dismiss) private var dismiss
    @State private var preview: CardPreviewState = {
        let state = CardPreviewState()
        state.original = true
        return state
    }()
    @State private var cannotOpenPhotos = false

    var body: some View {
        let asset = CardPhotoLibrary.asset(document.assetIdentifier)
        NavigationStack {
            List {
                Section {
                    CardPreviewSurface(document: document, controls: preview)
                        .aspectRatio(CGFloat(document.metadata.width) / CGFloat(document.metadata.height), contentMode: .fit)
                        .frame(maxWidth: .infinity, maxHeight: 320)
                        .listRowInsets(EdgeInsets())
                        .listRowSeparator(.hidden)
                    PhotoInformationHeading(name: document.originalName,
                                            fileExtension: document.sourceURL.pathExtension,
                                            fileSize: document.metadata.fileSize)
                    .listRowSeparator(.hidden)
                    if asset != nil {
                        Button("photo.open.library", systemImage: "photo.on.rectangle") {
                            Task { cannotOpenPhotos = !(await PhotosApplication.open()) }
                        }
                        .buttonStyle(.bordered)
                        .frame(minHeight: 44, alignment: .leading)
                        .listRowSeparator(.hidden)
                    }
                }
                PhotoDetailInformation(asset: asset, document: document, coordinate: nil)
            }
            .listStyle(.plain)
            .navigationTitle("photo.detail.title")
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("card.close", systemImage: "xmark", action: dismiss.callAsFunction)
                        .buttonBorderShape(.circle)
                }
            }
        }
        .alert("photo.open.failed", isPresented: $cannotOpenPhotos) { Button("done", role: .cancel) {} }
#if os(macOS)
        .frame(minWidth: 460, idealWidth: 620, minHeight: 520, idealHeight: 740)
#else
        .presentationDetents([.large])
#endif
    }
}
