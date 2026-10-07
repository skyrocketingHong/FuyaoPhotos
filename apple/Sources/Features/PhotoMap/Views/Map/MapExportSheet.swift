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

    @MainActor init(session: MapSession) {
        _state = State(initialValue: MapExportState(session: session))
        selectedYear = session.selectedYear
    }

    var body: some View {
        @Bindable var state = state
        NavigationStack {
            GeometryReader { geometry in
                if geometry.size.width >= 900 && !dynamicTypeSize.isAccessibilitySize {
                    HStack(alignment: .top, spacing: 0) {
                        ScrollView { previewContent.padding(20) }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        Form { configurationSections(settings: $state.settings) }
                            .photoPageForm()
                            .frame(width: 380)
                    }
                } else {
                    Form {
                        Section("map.export.preview") { previewContent }
                        configurationSections(settings: $state.settings)
                    }
                    .photoPageForm()
                }
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
                ToolbarItem(placement: .confirmationAction) {
                    Button(state.artifact == nil ? "map.export.generate" : "map.export.regenerate", action: generate)
                        .disabled(state.busy)
                }
            }
        }
        .modifier(NativePresentationDefaults())
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
#if os(macOS)
        .frame(minWidth: 680, idealWidth: 1020, minHeight: 560, idealHeight: 780)
        .presentationSizing(.fitted)
#else
        .presentationSizing(.page)
        .presentationDetents([.large])
#endif
    }

    @ViewBuilder
    private func configurationSections(settings: Binding<MapExportSettings>) -> some View {
        Section {
            LabeledContent("year.filter.title") {
                if let selectedYear { Text(selectedYear, format: .number.grouping(.never)) }
                else { Text("year.filter.all") }
            }
            Picker("map.export.scope", selection: settings.scope) {
                ForEach(MapExportScopeChoice.allCases) { choice in
                    Text(choice.title).tag(choice)
                }
            }
            .photoFormMenuPickerStyle()
            Picker("map.export.size", selection: settings.resolution) {
                ForEach(Array(MapExportResolution.allCases), id: \.rawValue) { resolution in
                    Text(String(format: String.localized("map.export.size.value"),
                        Int64(resolution.rawValue), Int64(resolution.rawValue)))
                        .tag(resolution)
                }
            }
            .photoFormMenuPickerStyle()
            Picker("map.appearance", selection: settings.mapOptions.appearance) {
                ForEach(MapAppearance.allCases) { Text($0.title).tag($0) }
            }
            .photoFormMenuPickerStyle()
        } footer: {
            Text("map.export.options.description")
        }
        .disabled(state.busy)
        Section("sidebar.map.style") {
            MapStyleChoiceGrid(selection: settings.mapOptions.style, appearance: settings.wrappedValue.mapOptions.appearance)
        }
        .disabled(state.busy)
        Section("sidebar.display.mode") {
            MapDisplayModeChoiceGrid(selection: settings.displayMode)
        }
        .disabled(state.busy)
    }

    @ViewBuilder
    private var previewContent: some View {
        if let artifact = state.artifact {
            VStack(alignment: .leading, spacing: 12) {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .firstTextBaseline) {
                        resultCount(artifact)
                        Spacer(minLength: 12)
                        Text(artifact.yearSpan).foregroundStyle(.secondary)
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        resultCount(artifact)
                        Text(artifact.yearSpan).foregroundStyle(.secondary)
                    }
                }
                .font(.subheadline)
                Image(decorative: artifact.image, scale: 1)
                    .resizable().scaledToFit()
                    .accessibilityLabel(Text("map.export.preview"))
                Text(String(format: String.localized("map.export.size.value"),
                    Int64(artifact.pixelSize), Int64(artifact.pixelSize)))
                    .font(.footnote).foregroundStyle(.secondary)
                if state.busy {
                    generationProgress
                    Button("map.export.cancel", action: state.cancel)
                }
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 12) { resultActions(artifact) }
                    VStack(alignment: .leading, spacing: 12) { resultActions(artifact) }
                }
                .buttonStyle(.bordered)
                .disabled(state.busy)
                if saved {
                    Label("map.export.saved", systemImage: "checkmark.circle")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                if let error = state.errorMessage {
                    Text(error).font(.footnote).foregroundStyle(.secondary)
                }
            }
        } else {
            ZStack {
                Rectangle().fill(.fill.quaternary)
                if state.busy {
                    VStack(spacing: 12) {
                        generationProgress
                        Button("map.export.cancel", action: state.cancel)
                    }
                    .padding(20)
                } else if let error = state.errorMessage {
                    ContentUnavailableView {
                        Label("map.export.failed", systemImage: "exclamationmark.triangle")
                    } description: {
                        Text(error)
                    } actions: {
                        Button("action.retry", action: generate)
                    }
                } else {
                    ContentUnavailableView("map.export.preview.empty", systemImage: "map",
                        description: Text("map.export.preview.description"))
                }
            }
            .aspectRatio(1, contentMode: .fit)
        }
    }

    private func resultCount(_ artifact: MapExportArtifact) -> some View {
        Text(String(format: String.localized("map.export.header.count"), Int64(artifact.totalCount)))
            .monospacedDigit()
    }

    @ViewBuilder
    private func resultActions(_ artifact: MapExportArtifact) -> some View {
        ShareLink(item: artifact, preview: SharePreview(Text("map.export.title"),
            image: Image(decorative: artifact.image, scale: 1))) {
                Label("card.share", systemImage: "square.and.arrow.up")
            }
        Button {
            exportDocument = MapExportPNGDocument(data: artifact.data)
            exportName = artifact.url.deletingPathExtension().lastPathComponent
            exporting = true
        } label: {
            Label("map.export.save", systemImage: "square.and.arrow.down")
        }
    }

    @ViewBuilder
    private var generationProgress: some View {
        switch state.stage {
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

    private func generate() {
        saved = false
        Task { await state.generate(colorScheme: colorScheme) }
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
