import SwiftUI

struct MapExportPreview: View {
    let state: MapExportState
    let transition: Namespace.ID

    var body: some View {
        ZStack {
            Color.clear
            if let artifact = state.artifact {
                HDRImageView(image: artifact.image, enabled: false)
#if os(iOS)
                    .matchedTransitionSource(id: artifact.url, in: transition)
#endif
                    .accessibilityLabel(Text("map.export.preview"))
            } else if state.busy {
                MapExportGenerationProgress(stage: state.stage)
                    .padding(PhotoPageLayout.margin)
            } else if let error = state.errorMessage {
                ContentUnavailableView {
                    Label("map.export.failed", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(error)
                }
            } else {
                ContentUnavailableView("map.export.preview.empty", systemImage: "map",
                    description: Text("map.export.preview.description"))
            }
        }
    }
}

struct MapExportPreviewActions: View {
    let artifact: MapExportArtifact?
    let busy: Bool
    let generate: () -> Void
    let cancel: () -> Void
    let fullScreen: () -> Void
    let save: (MapExportArtifact) -> Void

    var body: some View {
        if let artifact {
            GeometryReader { geometry in
                PhotoPreviewActionRow(fillsWidth: geometry.size.width <= 520) {
                    CircularIconButton("map.export.regenerate", systemImage: "arrow.clockwise", action: generate)
                    CircularIconButton("card.preview.full", systemImage: "arrow.up.left.and.arrow.down.right", action: fullScreen)
                    ShareLink(item: artifact, preview: SharePreview(Text("map.export.title"),
                        image: Image(decorative: artifact.image, scale: 1))) {
                            PhotoPreviewActionIcon(image: Image(systemName: "square.and.arrow.up"))
#if os(iOS)
                                .modifier(PhotoActionForeground())
#endif
                        }
                        .photoIconControlStyle()
                        .accessibilityLabel(Text("card.share"))
                        .help(Text("card.share"))
                    CircularIconButton("map.export.save", systemImage: "square.and.arrow.down") { save(artifact) }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .disabled(busy)
        } else {
            PhotoPreviewActionRow {
                if busy {
                    CircularIconButton("map.export.cancel", systemImage: "stop", action: cancel)
                } else {
                    Button("map.export.generate", systemImage: "map", action: generate)
                        .fixedSize()
#if os(iOS)
                        .buttonStyle(.glassProminent)
#else
                        .buttonStyle(.borderedProminent)
#endif
                        .controlSize(.regular)
                }
            }
        }
    }
}

private struct MapExportGenerationProgress: View {
    let stage: MapExportRenderStage

    var body: some View {
        switch stage {
        case .collecting:
            ProgressView("map.export.stage.collecting")
        case .map:
            ProgressView("map.export.stage.map")
        case let .markers(completed, total):
            ProgressView(value: Double(completed), total: Double(max(1, total))) {
                Text(String(format: String.localized("map.export.stage.markers"), Int64(completed), Int64(total)))
            }
        case .compositing:
            ProgressView("map.export.stage.compositing")
        }
    }
}

struct MapExportFullPreview: View {
    let artifact: MapExportArtifact
    var close: (() -> Void)?
    @State private var resetVersion = 0

    var body: some View {
        NativeZoomablePreview(resetVersion: resetVersion) {
            HDRImageView(image: artifact.image, enabled: false)
                .accessibilityLabel(Text("map.export.preview"))
        }
        .background { PhotoImageShadow(aspectRatio: 1) }
        .background { PhotoWorkspaceBackdrop(sourceURL: artifact.url) }
        .navigationTitle("map.export.preview")
#if os(iOS)
        .toolbarVisibility(.visible, for: .navigationBar)
        .toolbarVisibility(.hidden, for: .tabBar)
#endif
        .toolbar {
#if os(macOS)
            if let close {
                ToolbarItem(placement: .cancellationAction) {
                    Button("close", systemImage: "xmark", action: close)
                        .labelStyle(.iconOnly)
                }
            }
#endif
            ToolbarItem(placement: .primaryAction) {
                Button("card.preview.reset", systemImage: "1.magnifyingglass") { resetVersion += 1 }
                    .labelStyle(.iconOnly)
            }
        }
    }
}
