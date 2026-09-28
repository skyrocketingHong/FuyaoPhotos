import SwiftUI
import Photos

struct PhotoInformationSheet: View {
    let document: CardDocument
    @Environment(\.dismiss) private var dismiss
    @State private var cannotOpenPhotos = false

    var body: some View {
        let asset = CardPhotoLibrary.asset(document.assetIdentifier)
        NavigationStack {
            GeometryReader { geometry in
                let metrics = PhotoPreviewMetrics(available: geometry.size)
                if metrics.isWide {
                    HStack(alignment: .top, spacing: 0) {
                        previewStage(metrics, assetID: asset?.localIdentifier)
                        Form {
                            PhotoDetailInformation(asset: asset, document: document, coordinate: nil)
                        }
                        .photoPageForm().scrollContentBackground(.hidden)
                    }
                } else {
                    Form {
                        PhotoDetailInformation(asset: asset, document: document, coordinate: nil)
                    }
                    .photoPageForm()
                    .scrollContentBackground(.hidden)
                    .safeAreaBar(edge: .top, spacing: 0) {
                        previewStage(metrics, assetID: asset?.localIdentifier).frame(maxWidth: .infinity)
                    }
                }
            }
            .background { PhotoWorkspaceBackdrop(sourceURL: document.sourceURL) }
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
        .background(PhotoPreviewTheme.surface.ignoresSafeArea())
        .alert("photo.open.asset.failed", isPresented: $cannotOpenPhotos) { Button("done", role: .cancel) {} }
#if os(macOS)
        .frame(minWidth: 460, idealWidth: 620, minHeight: 520, idealHeight: 740)
#else
        .presentationDetents([.large])
        .presentationContentInteraction(.scrolls)
#endif
    }
    private func previewStage(_ metrics: PhotoPreviewMetrics, assetID: String?) -> some View {
        OriginalPhotoSummary(document: document, metrics: metrics) {
            if let assetID {
                CircularIconButton("photo.open.library", systemImage: "photo.on.rectangle") {
                    Task { cannotOpenPhotos = !(await PhotosApplication.open(assetIdentifier: assetID)) }
                }
            }
        }
    }

}
