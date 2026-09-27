import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct ColorsScreen: View {
    @Environment(PhotoWorkspace.self) private var workspace
    @State private var sampling = ColorSamplingState()
    @State private var space: ColorResultSpace?
    @State private var showingPicker = false
    @State private var showingFiles = false
    @State private var showingInfo = false
    @State private var showingCamera = false
    @State private var pendingInput: Input?
    @State private var confirmReplace = false
    private enum Input { case photos, files, camera }
    private var session: CardSession { workspace.session(for: .colors) }

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                if session.current == nil {
                    Form { intro }.photoPageForm()
                } else if geometry.size.width >= 800 || (geometry.size.width >= 700 && geometry.size.width > geometry.size.height * 1.2) {
                    HStack(spacing: 0) {
                        VStack(spacing: 8) {
                            photo.frame(maxWidth: .infinity, maxHeight: .infinity)
                            photoTools
                        }
                        .padding(.horizontal, 20)
                        .frame(width: geometry.size.width * 0.55)
                        Form { results }.photoPageForm()
                    }
                } else {
                    let photoHeight = min((geometry.size.width - 40) * 0.75,
                        max(72, min(geometry.size.height * 0.43, geometry.size.height - 290)))
                    VStack(spacing: 8) {
                        photo.frame(height: photoHeight).padding(.horizontal, 20)
                        photoTools.padding(.horizontal, 20)
                        Form { results }.photoPageForm().frame(maxHeight: .infinity)
                    }
                }
            }
#if !os(macOS)
            .toolbarVisibility(.hidden, for: .navigationBar)
#endif
        }
        .task(id: session.current?.sourceURL) { await sampling.load(session.current) }
        .sheet(isPresented: $showingPicker) {
            NativePhotoPicker { results in
                showingPicker = false
                let target = session
                Task { await target.open(results) }
            }
#if os(macOS)
            .frame(minWidth: 680, idealWidth: 820, minHeight: 520, idealHeight: 620)
#endif
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
        .sheet(isPresented: $showingInfo) { ColorInformationSheet(information: sampling.information) }
        .confirmationDialog("card.replace.confirm.many", isPresented: $confirmReplace) {
            Button("card.replace", role: .destructive) { if let pendingInput { present(pendingInput) } }
            Button("card.cancel", role: .cancel) { pendingInput = nil }
        }
        .alert("card.error.title", isPresented: Binding(get: { session.errorMessage != nil }, set: { if !$0 { session.errorMessage = nil } })) {
            Button("done", role: .cancel) { }
        } message: { Text(session.errorMessage ?? "") }
    }

    private var intro: some View {
        Section {
            PhotoPageIntro(title: "tab.colors", description: "colors.description", symbol: "eyedropper.halffull")
            ViewThatFits(in: .horizontal) {
                HStack { inputButtons }
                VStack(alignment: .leading) { inputButtons }
            }
            .disabled(session.busy)
            .buttonStyle(.borderless)
        }
    }
    @ViewBuilder private var inputButtons: some View {
        Button("card.open", systemImage: "photo.on.rectangle") { request(.photos) }
        Button("colors.files", systemImage: "folder") { request(.files) }
#if os(iOS)
        if UIImagePickerController.isSourceTypeAvailable(.camera) {
            Button("colors.camera", systemImage: "camera") { request(.camera) }
        }
#endif
    }
    private var photoTools: some View {
        HStack {
            if session.documents.count > 1 {
                let index = session.documents.firstIndex(where: { $0.id == session.selectedID }) ?? 0
                Button("card.previous", systemImage: "chevron.left") { session.selectedID = session.documents[index - 1].id }
                    .labelStyle(.iconOnly).disabled(index == 0)
                Text("card.photo.position \(index + 1) \(session.documents.count)").font(.caption.monospacedDigit())
                Button("card.next", systemImage: "chevron.right") { session.selectedID = session.documents[index + 1].id }
                    .labelStyle(.iconOnly).disabled(index >= session.documents.count - 1)
            }
            Spacer(minLength: 0)
            Menu("card.open", systemImage: "photo.badge.plus") {
                inputButtons
            }
            .labelStyle(.iconOnly)
            .buttonStyle(.bordered)
        }
        .frame(minHeight: 44)
        .disabled(session.busy)
    }
    private var photo: some View {
        ZStack {
            if let document = session.current {
                PhotoAmbientBackdrop(sourceURL: document.sourceURL)
                if let image = sampling.image {
                    ColorPhotoViewport(image: image, hdr: sampling.hdr, point: sampling.point) {
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
                hdr: $sampling.hdr, showInfo: { showingInfo = true }, movePixel: { x, y in
                    sampling.sample(at: CGPoint(x: (Double(x) + 0.5) / Double(document.metadata.width),
                        y: (Double(y) + 0.5) / Double(document.metadata.height)), document: document)
                })
        } else if sampling.busy {
            Section { ProgressView() }
        }
    }
    private func request(_ input: Input) {
        if workspace.hasPendingEdits(in: session) {
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
            Form {
                if let information {
                    Section("colors.source") {
                        LabeledContent("colors.dimensions", value: "\(information.width) × \(information.height)")
                        LabeledContent("colors.profile", value: information.colorSpace)
                        LabeledContent("colors.bitDepth", value: "\(information.bits)")
                        LabeledContent("colors.gamut") { Text(information.wideGamut ? "colors.wide" : "sRGB") }
                    }
                    Section("HDR") {
                        LabeledContent("Headroom", value: "\(information.headroom.formatted())×")
                        LabeledContent("Gain Map", value: information.gainMaps.isEmpty ? String.localized("colors.none") : information.gainMaps.joined(separator: ", "))
                    }
                }
                Section { Text("colors.sample.definition").foregroundStyle(.secondary) }
            }
            .photoPageForm()
            .navigationTitle("colors.source")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("done", action: dismiss.callAsFunction) } }
        }
#if os(macOS)
        .frame(minWidth: 460, minHeight: 440)
#endif
    }
}
