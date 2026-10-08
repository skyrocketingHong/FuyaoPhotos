import SwiftUI
import UniformTypeIdentifiers

struct MapExportSheet: View {
    @State private var state: MapExportState
    private let selectedYear: Int?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.dismiss) private var dismiss
    @State private var exporting = false
    @State private var exportDocument: MapExportPNGDocument?
    @State private var exportName = "FuyaoPhotos-Map"
    @State private var saveFailed = false
    @State private var saved = false
    @State private var fullScreen = false
    @Namespace private var previewTransition

    @MainActor init(session: MapSession) {
        _state = State(initialValue: MapExportState(session: session))
        selectedYear = session.selectedYear
    }

    var body: some View {
        NavigationStack {
#if os(macOS)
            if fullScreen, let artifact = state.artifact {
                MapExportFullPreview(artifact: artifact, close: { fullScreen = false })
            } else {
                workspace
            }
#else
            workspace
                .navigationDestination(isPresented: $fullScreen) {
                    if let artifact = state.artifact {
                        MapExportFullPreview(artifact: artifact)
                            .navigationTransition(.zoom(sourceID: artifact.url, in: previewTransition))
                    }
                }
#endif
        }
        .modifier(NativePresentationDefaults())
        .background { MapExportRenderingContextBridge(context: state.renderingContext).frame(width: 0, height: 0) }
        .onChange(of: state.settings) { _, _ in
            state.invalidate()
            saved = false
        }
        .onDisappear { state.cancel() }
        .fileExporter(isPresented: $exporting, document: exportDocument, contentType: .png,
            defaultFilename: exportName) { result in
                switch result {
                case .success: saved = true
                case .failure(let error):
                    if (error as NSError).code != NSUserCancelledError { saveFailed = true }
                }
                exportDocument = nil
            }
        .alert("map.export.save.failed", isPresented: $saveFailed) {
            Button("done", role: .cancel) {}
        }
        .alert("map.export.saved", isPresented: $saved) {
            Button("done", role: .cancel) {}
        }
#if os(macOS)
        .frame(minWidth: 680, idealWidth: 1020, minHeight: 560, idealHeight: 780)
        .presentationSizing(.fitted)
#else
        .presentationSizing(.page)
        .presentationDetents([.large])
#endif
    }

    private var workspace: some View {
        GeometryReader { geometry in
            let available = CGSize(width: dynamicTypeSize.isAccessibilitySize
                ? min(geometry.size.width, PhotoPreviewMetrics.columnWidth) : geometry.size.width,
                height: geometry.size.height)
            let metrics = PhotoPreviewMetrics(available: available, compactImageAspectRatio: 1)
            PhotoPreviewPage(sourceURL: state.artifact?.url, metrics: metrics, imageAspectRatio: 1) {
                MapExportPreview(state: state, transition: previewTransition)
            } accessories: {
                MapExportPreviewActions(artifact: state.artifact, busy: state.busy,
                    generate: generate, cancel: state.cancel,
                    fullScreen: { fullScreen = true }, save: save)
            } content: {
                MapExportConfiguration(state: state, selectedYear: selectedYear)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationTitle("map.export.title")
#if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
#endif
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("close", systemImage: "xmark") { state.cancel(); dismiss() }
                    .labelStyle(.iconOnly)
            }
        }
    }

    private func save(_ artifact: MapExportArtifact) {
        exportDocument = MapExportPNGDocument(data: artifact.data)
        exportName = artifact.url.deletingPathExtension().lastPathComponent
        exporting = true
    }

    private func generate() {
        saved = false
        Task { await state.generate(colorScheme: colorScheme) }
    }
}

private struct MapExportConfiguration: View {
    @Bindable var state: MapExportState
    let selectedYear: Int?

    var body: some View {
        Form {
            Section {
                LabeledContent("year.filter.title") {
                    if let selectedYear { Text(selectedYear, format: .number.grouping(.never)) }
                    else { Text("year.filter.all") }
                }
                Picker("map.export.scope", selection: $state.settings.scope) {
                    ForEach(MapExportScopeChoice.allCases) { choice in
                        Text(choice.title).tag(choice)
                    }
                }
                .photoFormMenuPickerStyle()
                Picker("map.export.size", selection: $state.settings.resolution) {
                    ForEach(Array(MapExportResolution.allCases), id: \.rawValue) { resolution in
                        Text(String(format: String.localized("map.export.size.value"),
                            Int64(resolution.rawValue), Int64(resolution.rawValue)))
                            .tag(resolution)
                    }
                }
                .photoFormMenuPickerStyle()
                Picker("map.appearance", selection: $state.settings.mapOptions.appearance) {
                    ForEach(MapAppearance.allCases) { Text($0.title).tag($0) }
                }
                .photoFormMenuPickerStyle()
            } footer: {
                Text("map.export.options.description")
            }
            Section("sidebar.map.style") {
                MapStyleChoiceGrid(selection: $state.settings.mapOptions.style,
                    appearance: state.settings.mapOptions.appearance)
            }
            Section("sidebar.display.mode") {
                MapDisplayModeChoiceGrid(selection: $state.settings.displayMode)
            }
        }
        .photoPageForm()
        .disabled(state.busy)
    }
}

private struct MapExportPNGDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.png] }
    let data: Data

    init(data: Data) { self.data = data }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else { throw CocoaError(.fileReadCorruptFile) }
        self.data = data
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
