import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct ColorsScreen: View {
    @Environment(PhotoWorkspace.self) private var workspace
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sampling: ColorSamplingState
    @State private var space: ColorResultSpace?
    @State private var showingPicker = false
    @State private var showingFiles = false
    @State private var showingInfo = false
    @State private var wideWorkspace = false
    @State private var showingCamera = false
    @State private var pendingInput: Input?
    @State private var confirmReplace = false
    @State private var confirmationUsesMenu = false
    private enum Input: Equatable { case photos, files, camera }
    private var session: CardSession { workspace.session(for: .colors) }
    private var inlineInformation: Bool {
#if os(macOS)
        true
#else
        wideWorkspace
#endif
    }
    @MainActor init(sampling: ColorSamplingState? = nil) { _sampling = State(initialValue: sampling ?? ColorSamplingState()) }
    private var replacementTitle: LocalizedStringKey {
        session.documents.count == 1 ? "card.replace.confirm.one" : "card.replace.confirm.many"
    }

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                let metrics = PhotoPreviewMetrics(available: geometry.size)
                if let document = session.current {
                    PhotoPreviewPage(sourceURL: document.sourceURL, metrics: metrics,
                        imageAspectRatio: CGFloat(document.metadata.width) / CGFloat(max(1, document.metadata.height))) {
                    photo
                        .photoDevelopEffect()
                } accessories: {
                        colorsActions(metrics: metrics)
                    } content: {
                        if showingInfo && inlineInformation {
                            VStack(spacing: 0) {
                                HStack {
                                    Text("colors.source").font(.headline)
                                    Spacer()
                                    Button("done") { showingInfo = false }
                                }.padding(20)
                                Form { ColorInformationSections(information: sampling.information) }.photoPageForm()
                            }
                        } else {
                            Form { results }.photoPageForm().scrollContentBackground(.hidden)
                                .animation(reduceMotion ? nil : .smooth(duration: 0.2), value: sampling.sample != nil)
                        }
                    }
                } else {
                    PhotoImportPage(title: "tab.colors", description: "colors.description", symbol: "eyedropper.halffull", busy: session.busy) {
                        PhotoImportAction(title: "card.open", symbol: "photo.badge.plus", prominence: .primary) { request(.photos) }
                        PhotoImportAction(title: "colors.files", symbol: "folder") { request(.files) }
                        if cameraAvailable {
                            PhotoImportAction(title: "colors.camera", symbol: "camera", prominence: .tertiary) { request(.camera) }
                        }
                    }
                }
            }
            .modifier(PhotoDepartureOverlay(session: session, viewportCapture: { sampling.departureCapture?() }))
            .onGeometryChange(for: Bool.self) { $0.size.width >= PhotoPreviewMetrics.wideThreshold } action: { wideWorkspace = $0 }
#if !os(macOS)
            .toolbarVisibility(.hidden, for: .navigationBar)
#else
            .navigationTitle("tab.colors")
            .navigationSubtitle(session.current?.originalName ?? "")
            .toolbar {
                if session.current != nil {
                    ToolbarItem(placement: .primaryAction) { inputMenu }
                    ToolbarItem(placement: .primaryAction) { PhotoCloseButton(session: session) }
                }
            }
#endif
        }
        .task(id: session.current?.sourceURL) { await sampling.load(session.current) }
        .onAppear {
            if workspace.openPickerRequest == .colors { workspace.openPickerRequest = nil; openFromCommand() }
        }
        .onChange(of: workspace.openPickerRequest) { _, requested in
            if requested == .colors { workspace.openPickerRequest = nil; openFromCommand() }
        }
        .onChange(of: workspace.saveSheetRequest) { _, requested in
            if requested == .colors { workspace.saveSheetRequest = nil }
        }
        .sheet(isPresented: $showingPicker) {
            NativePhotoPicker { results in
                showingPicker = false
                let target = session
                Task { await target.open(results) }
            }
            .photoPickerPresentation()
        }
        .fileImporter(isPresented: $showingFiles, allowedContentTypes: [.image], allowsMultipleSelection: true) { result in
            switch result {
            case .success(let urls): Task { await session.openFiles(urls) }
            case .failure: session.errorMessage = CardError.invalidImage.localizedDescription
            }
        }
#if os(iOS)
        .sheet(isPresented: $showingCamera) {
            ColorCameraCapture { result in
                showingCamera = false
                if let url = try? result.get() {
                    Task { await session.openFiles([url]); try? FileManager.default.removeItem(at: url) }
                } else if case .failure = result { session.errorMessage = CardError.invalidImage.localizedDescription }
            }
        }
#endif
        .sheet(isPresented: Binding(get: { showingInfo && !inlineInformation }, set: { if !inlineInformation { showingInfo = $0 } })) {
            ColorInformationSheet(information: sampling.information).presentationSizing(.form)
        }
        .alert("card.error.title", isPresented: Binding(get: { session.errorMessage != nil }, set: { if !$0 { session.errorMessage = nil } })) {
            Button("done", role: .cancel) { }
        } message: { Text(session.errorMessage ?? "") }
    }

    @ViewBuilder private var inputButtons: some View {
        Button("card.open", systemImage: "photo.badge.plus") { request(.photos, fromMenu: true) }
        Button("colors.files", systemImage: "folder") { request(.files, fromMenu: true) }
#if os(iOS)
        if UIImagePickerController.isSourceTypeAvailable(.camera) {
            Button("colors.camera", systemImage: "camera") { request(.camera, fromMenu: true) }
        }
#endif
    }
    private func colorsActions(metrics: PhotoPreviewMetrics) -> some View {
#if os(macOS)
        PhotoPreviewActionRow(fillsWidth: false) { colorTools }.disabled(session.busy)
#else
        let actionCount = (cameraAvailable ? 3 : 2) + (hasHDR ? 2 : 1) + 1
        return Group {
            if metrics.width - PhotoPageLayout.margin * 2 >= CGFloat(actionCount) * PhotoPreviewMetrics.inlineToolWidth {
                PhotoPreviewActionRow(fillsWidth: !metrics.isWide) {
                    if session.documents.count > 1 { inputMenu }
                    else { inputAction(.photos, title: "card.open", symbol: "photo.badge.plus") }
                    inputAction(.files, title: "colors.files", symbol: "folder")
#if os(iOS)
                    if cameraAvailable { inputAction(.camera, title: "colors.camera", symbol: "camera") }
#endif
                    colorTools
                    PhotoCloseButton(session: session)
                }
            } else {
                PhotoPreviewActionRow(fillsWidth: !metrics.isWide) { inputMenu; colorTools; PhotoCloseButton(session: session) }
            }
        }
        .disabled(session.busy)
#endif
    }

    private var cameraAvailable: Bool {
#if os(iOS)
        UIImagePickerController.isSourceTypeAvailable(.camera)
#else
        false
#endif
    }

    private var hasHDR: Bool { session.current?.metadata.hdr == true || (sampling.information?.headroom ?? 1) > 1 }

    @ViewBuilder private var colorTools: some View {
        if hasHDR {
            CircularIconToggle("HDR", imageAsset: "HDR", isOn: $sampling.hdr)
        }
        CircularIconButton("colors.source", systemImage: "info.circle") { showingInfo = true }
            .disabled(sampling.information == nil)
    }

    private func inputAction(_ input: Input, title: LocalizedStringKey, symbol: String) -> some View {
        CircularIconButton(title, systemImage: symbol) { request(input) }
            .confirmationDialog(replacementTitle, isPresented: Binding(
                get: { confirmReplace && !confirmationUsesMenu && pendingInput == input },
                set: { value in if !confirmationUsesMenu && pendingInput == input { confirmReplace = value } }), titleVisibility: .visible) {
                    Button("card.replace", role: .destructive) { present(input) }
                    Button("card.cancel", role: .cancel) { pendingInput = nil }
                }
    }

    private var inputMenu: some View {
        Group {
#if os(macOS)
            Menu("card.open", systemImage: "photo.badge.plus") { inputChoices }
                .labelStyle(.iconOnly).menuIndicator(.hidden).buttonBorderShape(.circle)
#else
        PhotoPreviewMenu(title: "card.open", systemImage: "photo.badge.plus") {
            inputChoices
        }
#endif
        }
        .confirmationDialog(replacementTitle, isPresented: Binding(
            get: { confirmReplace && confirmationUsesMenu },
            set: { if confirmationUsesMenu { confirmReplace = $0 } }), titleVisibility: .visible) {
            Button("card.replace", role: .destructive) { if let pendingInput { present(pendingInput) } }
            Button("card.cancel", role: .cancel) { pendingInput = nil }
        }
    }

    @ViewBuilder private var inputChoices: some View {
        inputButtons
        if session.documents.count > 1 {
            Picker("metadata.photo.name", selection: Binding(get: { session.selectedID }, set: { session.selectedID = $0 })) {
                ForEach(session.documents) { Text($0.originalName).tag(Optional($0.id)) }
            }
        }
    }

    private func openFromCommand() {
#if os(macOS)
        request(.photos, fromMenu: true)
#else
        request(.photos)
#endif
    }
    private var photo: some View {
        ZStack {
            if let document = session.current {
                if let image = sampling.image {
                    ColorPhotoViewport(image: image, hdr: sampling.hdr, point: sampling.point, hex: sampling.sample?.color.srgb.hex,
                        onCaptureReady: { sampling.departureCapture = $0 }) {
                        sampling.sample(at: $0, document: document)
                    }
                }
            }
            if sampling.busy || session.busy { ProgressView() }
            if let error = sampling.error { Text(error).foregroundStyle(.secondary).padding() }
        }
    }
    @ViewBuilder private var results: some View {
        if let sample = sampling.sample, let document = session.current {
            ColorResultsPanel(sample: sample, information: sampling.information, space: $space,
                movePixel: { x, y in
                    sampling.sample(at: CGPoint(x: (Double(x) + 0.5) / Double(document.metadata.width),
                        y: (Double(y) + 0.5) / Double(document.metadata.height)), document: document)
                })
        } else {
            Section {
                HStack(spacing: 16) {
                    ZStack {
                        Rectangle().fill(.fill.quaternary)
                        if sampling.busy { ProgressView() }
                        else { Image(systemName: "eyedropper").foregroundStyle(.secondary) }
                    }
                    .frame(width: 72, height: 72)
                    .accessibilityLabel(Text("colors.magnifier"))
                    Spacer(minLength: 0)
                }
            }
        }
    }
    private func request(_ input: Input, fromMenu: Bool = false) {
        confirmationUsesMenu = fromMenu
        if session.current != nil {
            pendingInput = input; confirmReplace = true
        } else { present(input) }
    }
    private func present(_ input: Input) {
        pendingInput = nil
        switch input {
        case .photos: showingPicker = true
        case .files: showingFiles = true
        case .camera: showingCamera = true
        }
    }
}

private struct ColorInformationSheet: View {
    let information: PhotoColorDescription?
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            Form { ColorInformationSections(information: information) }
            .photoPageForm()
            .navigationTitle("colors.source")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("close", systemImage: "xmark", action: dismiss.callAsFunction)
                        .labelStyle(.iconOnly)
                }
            }
        }
    }
}

private struct ColorInformationSections: View {
    let information: PhotoColorDescription?
    @ViewBuilder var body: some View {
                if let information {
                    Section("colors.source") {
                        PhotoInformationRow(title: "colors.dimensions", value: "\(information.width) × \(information.height)", monospacedDigits: true)
                        PhotoInformationRow(title: "colors.profile", value: information.colorSpace)
                        PhotoInformationRow(title: "colors.bitDepth", value: "\(information.bits)", monospacedDigits: true)
                        PhotoInformationRow(title: "colors.gamut", value: information.wideGamut ? String.localized("colors.wide") : "sRGB")
                    }
                    Section("HDR") {
                        PhotoInformationRow(title: "colors.headroom", value: "\(information.headroom.formatted())×")
                        PhotoInformationRow(title: "colors.gainMaps", value: information.gainMaps.isEmpty ? String.localized("colors.none") : information.gainMaps.joined(separator: ", "))
                    }
                }
                Section { Text("colors.sample.definition").foregroundStyle(.secondary) }
    }
}
