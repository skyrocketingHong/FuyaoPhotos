import SwiftUI
import Photos
import PhotoMapCore

struct ClusterPhotosView: View {
    let selection: ClusterSelection
    let library: PhotoLibraryService
    let addCard: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var members: [PhotoLocation] = []
    @State private var requestedOffset = 0
    @State private var nextOffset = 0
    @State private var attempt = 0
    @State private var isLoading = true
    @State private var failed = false
    @State private var hasMore = true
    @State private var loadedPageRequest: MemberPageRequest?
    private let pageSize = 60

    var body: some View {
        let request = MemberPageRequest(offset: requestedOffset, attempt: attempt)
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 80, maximum: 180), spacing: 2)], spacing: 2) {
                    ForEach(members) { location in
                        NavigationLink(value: location) {
                            GeometryReader { geometry in
                                AssetThumbnail(assetID: location.id, pointSize: geometry.size.width,
                                    indexVersion: selection.indexVersion, thumbnails: library.thumbnails)
                            }
                            .aspectRatio(1, contentMode: .fit)
                            .overlay(alignment: .bottomTrailing) {
                                if location.asset.mediaType == .video {
                                    Image(systemName: "video.fill").foregroundStyle(.white).padding(4).background(.black.opacity(0.7), in: RoundedRectangle(cornerRadius: 4))
                                } else if location.asset.mediaSubtypes.contains(.photoLive) {
                                    Image(systemName: "livephoto").foregroundStyle(.white).padding(4).background(.black.opacity(0.7), in: RoundedRectangle(cornerRadius: 4))
                                }
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(location.creationDate.map { Text($0, format: .dateTime.year().month().day()) } ?? Text("photo.date.unknown"))
                    }
                }
                if isLoading { ProgressView("loading.photos").padding() }
                else if failed {
                    ContentUnavailableView {
                        Label("error.loading.failed", systemImage: "exclamationmark.triangle")
                    } description: { Text("error.cluster.members") }
                    actions: { Button("action.retry") { attempt += 1 } }
                } else if hasMore {
                    Button("action.load.more") { requestedOffset = nextOffset }.padding()
                } else if members.isEmpty {
                    ContentUnavailableView("map.cluster.empty", systemImage: "photo.on.rectangle")
                }
            }
            .navigationTitle("map.cluster.title")
            .navigationSubtitle(Text("map.cluster.member.count \(selection.cluster.count)"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("card.close", systemImage: "xmark", action: dismiss.callAsFunction)
                        .buttonBorderShape(.circle)
                }
            }
            .navigationDestination(for: PhotoLocation.self) { location in
                PhotoDetailContent(location: location, thumbnails: library.thumbnails,
                    indexVersion: selection.indexVersion, addCard: addCard)
                    .id(location.id)
                    .navigationTitle("photo.detail.title")
            }
        }
#if os(macOS)
        .frame(minWidth: 660, idealWidth: 820, minHeight: 480, idealHeight: 800)
#else
        .presentationDetents([.large])
#endif
        .task(id: MemberPageTaskIdentity(request: request, isActive: scenePhase == .active)) {
            guard scenePhase == .active, loadedPageRequest != request else { return }
            isLoading = true; failed = false
            do {
                let page = try await library.locations(in: selection.cluster, region: selection.region,
                    year: selection.year, offset: request.offset, limit: pageSize)
                try Task.checkCancellation()
                let existing = Set(members.map(\.id))
                members.append(contentsOf: page.filter { !existing.contains($0.id) })
                // Inaccessible assets are filtered after scanning, so advance by index span, not displayed count.
                let scannedCount = min(pageSize, max(0, selection.cluster.count - request.offset))
                nextOffset = request.offset + scannedCount
                hasMore = !page.isEmpty && nextOffset < selection.cluster.count
                loadedPageRequest = request
                isLoading = false
            } catch is CancellationError { }
            catch { if !Task.isCancelled { failed = true; isLoading = false } }
        }
    }
}

private struct MemberPageRequest: Hashable { let offset: Int; let attempt: Int }
private struct MemberPageTaskIdentity: Hashable { let request: MemberPageRequest; let isActive: Bool }
