import SwiftUI
import Photos

struct PhotoInformationSheet: View {
    let document: CardDocument
    @Environment(\.dismiss) private var dismiss
    @State private var cannotOpenPhotos = false
    @State private var summaryState = OriginalSummaryState()

    var body: some View {
        let asset = CardPhotoLibrary.asset(document.assetIdentifier)
        NavigationStack {
            GeometryReader { geometry in
                let metrics = PhotoPreviewMetrics(available: geometry.size)
                // The preview scrolls with the page here: it is one element of the sheet, not pinned chrome.
                InlinePhotoPreviewPage(sourceURL: document.sourceURL, metrics: metrics,
                    imageAspectRatio: CGFloat(document.metadata.width) / CGFloat(max(1, document.metadata.height))) {
                    OriginalSummaryPhoto(document: document, state: summaryState)
                } accessories: {
                    OriginalSummaryActions(document: document, metrics: metrics, state: summaryState,
                        actions: { libraryButton(assetID: asset?.localIdentifier) })
                } details: {
                    PhotoDetailInformation(asset: asset, document: document, coordinate: nil)
                }
            }
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

    @ViewBuilder private func libraryButton(assetID: String?) -> some View {
        if let assetID {
            CircularIconButton("photo.open.library", systemImage: "photo.on.rectangle") {
                Task { cannotOpenPhotos = !(await PhotosApplication.open(assetIdentifier: assetID)) }
            }
        }
    }
}
