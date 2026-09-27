import SwiftUI
import Photos

struct PhotoInformationSheet: View {
    let document: CardDocument
    @Environment(\.dismiss) private var dismiss
    @State private var cannotOpenPhotos = false

    var body: some View {
        let asset = CardPhotoLibrary.asset(document.assetIdentifier)
        NavigationStack {
            List {
                Section {
                    OriginalPhotoSummary(document: document)
                        .listRowInsets(EdgeInsets(top: 12, leading: 20, bottom: 12, trailing: 20))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
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
            .scrollEdgeEffectStyle(.soft, for: .top)
            .scrollContentBackground(.hidden)
            .background(alignment: .top) {
                PhotoAmbientBackdrop(sourceURL: document.sourceURL)
                    .frame(height: 440)
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
        .alert("photo.open.failed", isPresented: $cannotOpenPhotos) { Button("done", role: .cancel) {} }
#if os(macOS)
        .frame(minWidth: 460, idealWidth: 620, minHeight: 520, idealHeight: 740)
#else
        .presentationDetents([.large])
        .presentationContentInteraction(.scrolls)
#endif
    }
}
