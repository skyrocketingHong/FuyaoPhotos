import SwiftUI

struct CardCanvas: View {
    @Bindable var session: CardSession
    var zoom: Namespace.ID
    @Binding var replaceConfirmation: Bool
    @Binding var closeConfirmation: Bool
    let open: () -> Void
    let save: () -> Void
    let close: () -> Void
    let confirmReplace: () -> Void
    let confirmClose: () -> Void
    @State private var preview = CardPreviewState()
    @State private var textEditingActive = false
    @State private var inspectorSelection = CardInspectorSelection()

    var body: some View {
        GeometryReader { geometry in
            if let document = session.current {
                let metrics = PhotoPreviewMetrics(available: geometry.size)
                PhotoPreviewPage(sourceURL: document.sourceURL, metrics: metrics,
                    imageAspectRatio: CGFloat(document.metadata.width) / CGFloat(max(1, document.metadata.height))) {
                    CardFilmstrip(session: session, preview: preview, zoom: zoom,
                                  processing: textEditingActive || preview.isRendering)
                        .photoDevelopEffect()
                } accessories: {
                    CardActionStrip(document: document, preview: preview, photoCount: session.documents.count,
                                    replaceConfirmation: $replaceConfirmation, closeConfirmation: $closeConfirmation,
                                    open: open, save: save, close: close,
                                    confirmReplace: confirmReplace, confirmClose: confirmClose,
                                    saved: session.savedCount != nil && session.errorMessage == nil)
                } content: {
                    CardAdjustmentPanel(document: document, selection: inspectorSelection, textEditingActive: $textEditingActive)
                        .frame(maxWidth: metrics.isWide ? 460 : .infinity, maxHeight: .infinity)
                        .frame(maxWidth: .infinity, alignment: metrics.isWide ? .leading : .center)
                }
                .onChange(of:session.selectedID) { _,_ in
                    preview.playing=false; preview.original=false; textEditingActive=false
                }
#if os(iOS)
                .navigationDestination(isPresented: $preview.fullScreen) {
                    // Photos-style zoom push from the canvas photo; registered here, outside
                    // the filmstrip's lazy pager.
                    CardFullPreview(document: document, hdr: preview.hdr, original: preview.original)
                        .navigationTransition(.zoom(sourceID: document.id, in: zoom))
                        .toolbarVisibility(.hidden, for: .tabBar)
                }
#endif
            }
        }
    }
}

private struct CardActionStrip: View {
    let document: CardDocument
    @Bindable var preview: CardPreviewState
    let photoCount: Int
    @Binding var replaceConfirmation: Bool
    @Binding var closeConfirmation: Bool
    let open: () -> Void
    let save: () -> Void
    let close: () -> Void
    let confirmReplace: () -> Void
    let confirmClose: () -> Void
    let saved: Bool
    var body: some View {
        GeometryReader { geometry in
            let visible = visibleTools(for: geometry.size.width)
            HStack(spacing: 0) {
                ForEach(visible, id: \.self) { tool in
                    control(for: tool, width: geometry.size.width)
                        .frame(width: geometry.size.width / CGFloat(visible.count))
                        .frame(height: 64)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(maxHeight: .infinity, alignment: .center)
        }
        .frame(height: 64)
    }

    private enum Tool: Hashable {
        case live, hdr, compare, full, open, save, more
    }

    private var optionalTools: [Tool] {
        var tools: [Tool] = [.open]
        if document.isLive { tools.append(.live) }
        if document.metadata.hdr { tools.append(.hdr) }
        tools.append(contentsOf: [.compare, .full])
        return tools
    }

    private var replacePrompt: LocalizedStringKey {
        photoCount == 1 ? "card.replace.confirm.one" : "card.replace.confirm.many"
    }

    private var closePrompt: LocalizedStringKey {
        photoCount == 1 ? "card.close.confirm.one" : "card.close.confirm.many"
    }

    private var closeLabel: LocalizedStringKey {
        photoCount == 1 ? "card.close.one" : "card.close.many"
    }

    private func visibleTools(for width: CGFloat) -> [Tool] {
        let slots = min(7, max(2, Int(max(0, width) / 68)))
        return Array(optionalTools.prefix(slots - 2)) + [.save, .more]
    }

    private func overflowTools(for width: CGFloat) -> [Tool] {
        let visible = Set(visibleTools(for: width))
        return optionalTools.filter { !visible.contains($0) }
    }

    @ViewBuilder private func control(for tool: Tool, width: CGFloat) -> some View {
        switch tool {
        case .live:
            CircularIconToggle("card.live.preview", systemImage: preview.playing ? "stop.circle" : "livephoto",
                               isOn: $preview.playing)
        case .hdr:
            CircularIconToggle("HDR", imageAsset: "HDR", isOn: $preview.hdr)
        case .compare:
            CircularIconToggle("card.compare", systemImage: "square.on.square", isOn: $preview.original)
        case .full:
            CircularIconButton("card.preview.full", systemImage: "arrow.up.left.and.arrow.down.right") {
                preview.playing = false
                preview.fullScreen = true
            }
        case .open:
            CircularIconButton("card.open", systemImage: "photo.badge.plus", action: open)
                .confirmationDialog(replacePrompt, isPresented: $replaceConfirmation, titleVisibility: .visible) {
                    Button("card.replace", role: .destructive, action: confirmReplace)
                    Button("card.cancel", role: .cancel) { }
                }
        case .save:
            CircularIconButton(photoCount == 1 ? "card.save.action.one" : "card.save.action.many",
                               systemImage: saved ? "checkmark.circle.fill" : "square.and.arrow.down", action: save)
        case .more:
            if overflowTools(for: width).contains(.open) {
                moreMenu(for: width)
                    .confirmationDialog(replacePrompt, isPresented: $replaceConfirmation, titleVisibility: .visible) {
                        Button("card.replace", role: .destructive, action: confirmReplace)
                        Button("card.cancel", role: .cancel) { }
                    }
            } else {
                moreMenu(for: width)
            }
        }
    }

    private func moreMenu(for width: CGFloat) -> some View {
        PhotoPreviewMenu(title: "card.more") {
            ForEach(overflowTools(for: width), id: \.self) { menuAction(for: $0) }
            if let url = document.exportURL, !document.isLive || document.exportIsMotionPhoto {
                ShareLink(item: url) { Label("card.share", systemImage: "square.and.arrow.up") }
            }
            Button("card.style.reset", systemImage: "arrow.counterclockwise") {
                document.card.style = PhotoCardStyle()
            }
            Button(closeLabel, systemImage: "xmark", action: close)
        }
        .confirmationDialog(closePrompt, isPresented: $closeConfirmation, titleVisibility: .visible) {
            Button(closeLabel, role: .destructive, action: confirmClose)
            Button("card.cancel", role: .cancel) { }
        }
    }

    @ViewBuilder private func menuAction(for tool: Tool) -> some View {
        switch tool {
        case .live:
            Toggle("card.live.preview", systemImage: preview.playing ? "stop.circle" : "livephoto", isOn: $preview.playing)
        case .hdr:
            Toggle(isOn: $preview.hdr) { Label { Text("HDR") } icon: { Image("HDR") } }
        case .compare:
            Toggle("card.compare", systemImage: "square.on.square", isOn: $preview.original)
        case .full:
            Button("card.preview.full", systemImage: "arrow.up.left.and.arrow.down.right") {
                preview.playing = false
                preview.fullScreen = true
            }
        case .open:
            Button("card.open", systemImage: "photo.badge.plus", action: open)
        case .save, .more:
            EmptyView()
        }
    }
}

private struct CardFilmstrip: View {
    @Bindable var session: CardSession
    let preview: CardPreviewState
    let zoom: Namespace.ID
    let processing: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        ZStack {
#if os(macOS)
            if let document=session.current {
                CardPreviewSurface(document:document,controls:preview,zoom:zoom).id(document.id)
                    .modifier(ProcessingVeil(active: processing, pulse: preview.isRendering))
                    .aspectRatio(CGFloat(document.metadata.width)/CGFloat(document.metadata.height),contentMode:.fit)
                    .frame(maxWidth:.infinity,maxHeight:.infinity)
            }
#else
            ScrollView(.horizontal) {
                LazyHStack(spacing: 0) {
                ForEach(session.documents) { document in
                    Group {
                        if document.id==session.selectedID {
                            CardPreviewSurface(document:document,controls:preview,zoom:zoom)
                        } else if abs((session.documents.firstIndex { $0.id==document.id } ?? 0)-selectedIndex)<=1 {
                            CardNeighborPreview(document:document,hdr:preview.hdr)
                        } else { Color.clear }
                    }
                    .aspectRatio(CGFloat(document.metadata.width)/CGFloat(document.metadata.height),contentMode:.fit)
                    .containerRelativeFrame(.horizontal)
                    .frame(maxHeight: .infinity, alignment: .center)
                    .id(document.id)
                }
                }
                .frame(maxHeight: .infinity)
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: Binding(get: { session.selectedID }, set: { id in
                if let id, session.documents.contains(where: { $0.id == id }) { session.selectedID = id }
            }))
            .scrollIndicators(.hidden)
            .modifier(ProcessingVeil(active: processing, pulse: preview.isRendering))
#endif
            if session.documents.count>1 {
                HStack {
                    CircularIconButton("card.previous",systemImage:"chevron.left") { move(-1) }
                        .disabled(selectedIndex==0)
                    Spacer()
                    CircularIconButton("card.next",systemImage:"chevron.right") { move(1) }
                        .disabled(selectedIndex==session.documents.count-1)
                }
                .padding(.horizontal, 8)
                .frame(maxHeight: .infinity)
                Text("card.photo.position \(selectedIndex+1) \(session.documents.count)")
                    .font(.caption.monospacedDigit())
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(.thinMaterial, in: .capsule)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, 8)
            }
        }
    }
    private var selectedIndex:Int { session.documents.firstIndex { $0.id==session.selectedID } ?? 0 }
    private func move(_ amount:Int) {
        let next=selectedIndex+amount
        if session.documents.indices.contains(next) {
            withAnimation(reduceMotion ? nil : .smooth(duration: 0.24)) {
                session.selectedID = session.documents[next].id
            }
        }
    }
}
