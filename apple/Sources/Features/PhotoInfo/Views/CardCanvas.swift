import SwiftUI

struct CardCanvas: View {
    @Bindable var session: CardSession
    @Binding var replaceConfirmation: Bool
    @Binding var closeConfirmation: Bool
    let open: () -> Void
    let save: () -> Void
    let close: () -> Void
    let confirmReplace: () -> Void
    let confirmClose: () -> Void
    @State private var preview = CardPreviewState()
    @State private var textEditingActive = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geometry in
            if let document = session.current {
                Group {
#if os(macOS)
                if geometry.size.width >= 760 {
                    HSplitView {
                        macPreview(document: document)
                            .frame(minWidth: 400)
                        CardAdjustmentPanel(document: document, textEditingActive: $textEditingActive)
                            .frame(minWidth: 300, idealWidth: 340, maxWidth: 420)
                    }
                } else {
                    VStack(spacing: 0) {
                        CardFilmstrip(session: session, preview: preview,
                                      processing: textEditingActive || preview.isRendering)
                            .frame(height: max(160, min(geometry.size.height * 0.43, 320)))
                            .padding(.horizontal, 20)
                            .padding(.top, 16)
                        actionStrip(document: document)
                        CardAdjustmentPanel(document: document, textEditingActive: $textEditingActive)
                            .frame(maxHeight: .infinity)
                    }
                    .background(Color(white: 0.06))
                }
#else
                let wide = geometry.size.width >= 800 ||
                    (geometry.size.width >= 700 && geometry.size.width > geometry.size.height * 1.2)
                let sidebarWidth = min(460, max(310, geometry.size.width * 0.44))
                if wide {
                    HStack(spacing: 0) {
                        VStack(spacing: 8) {
                            CardFilmstrip(session: session, preview: preview,
                                          processing: textEditingActive || preview.isRendering)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .padding(.horizontal, 8)
                                .shadow(color: .black.opacity(0.42), radius: 4, y: 2)
                            actionStrip(document: document)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background {
                            PhotoAmbientBackdrop(sourceURL: document.sourceURL)
                                .id(document.id)
                                .ignoresSafeArea(edges: .top)
                        }
                        CardAdjustmentPanel(document: document, textEditingActive: $textEditingActive)
                            .frame(width: sidebarWidth)
                    }
                } else {
                    let photoHeight = min(geometry.size.width * 0.75,
                                          max(72, min(geometry.size.height * 0.43, geometry.size.height - 290)))
                    VStack(spacing: 8) {
                        CardFilmstrip(session: session, preview: preview,
                                      processing: textEditingActive || preview.isRendering)
                            .frame(height: photoHeight)
                            .padding(.horizontal, 8)
                            .shadow(color: .black.opacity(0.42), radius: 4, y: 2)
                        actionStrip(document: document)
                        CardAdjustmentPanel(document: document, textEditingActive: $textEditingActive)
                            .frame(maxHeight: .infinity)
                    }
                    .padding(.top, 4)
                    .background {
                        PhotoAmbientBackdrop(sourceURL: document.sourceURL)
                            .id(document.id)
                            .ignoresSafeArea(edges: .top)
                    }
                    .animation(reduceMotion ? nil : .easeInOut(duration: 0.28), value: photoHeight)
                }
#endif
                }
                .onChange(of:session.selectedID) { _,_ in
                    preview.playing=false; preview.original=false; textEditingActive=false
                }
            }
        }
    }

    private func actionStrip(document: CardDocument) -> some View {
        CardActionStrip(document: document, preview: preview, photoCount: session.documents.count,
                        replaceConfirmation: $replaceConfirmation, closeConfirmation: $closeConfirmation,
                        open: open, save: save, close: close,
                        confirmReplace: confirmReplace, confirmClose: confirmClose,
                        saved: session.savedCount != nil && session.errorMessage == nil)
    }

#if os(macOS)
    private func macPreview(document: CardDocument) -> some View {
        VStack(spacing: 0) {
            CardFilmstrip(session: session, preview: preview,
                          processing: textEditingActive || preview.isRendering)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.horizontal, 24)
                .padding(.top, 20)
            actionStrip(document: document)
                .padding(.bottom, 12)
        }
        .background(Color(white: 0.06))
    }
#endif
}

private struct CardActionStrip: View {
    let document: CardDocument
    @Bindable var preview: CardPreviewState
    let photoCount: Int
    @Binding var replaceConfirmation: Bool
    @Binding var closeConfirmation: Bool
    @State private var showingPhotoInfo = false
    let open: () -> Void
    let save: () -> Void
    let close: () -> Void
    let confirmReplace: () -> Void
    let confirmClose: () -> Void
    let saved: Bool
    var body: some View {
        Group {
#if os(macOS)
        HStack(spacing: 14) {
            CardMediaControls(document: document, controls: preview)
        }
        .labelStyle(.iconOnly)
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .controlSize(.regular)
        .padding(.horizontal, 18)
        .padding(.vertical, 10)
#else
        GeometryReader { geometry in
            let visible = visibleTools(for: geometry.size.width)
            HStack(spacing: 0) {
                ForEach(visible, id: \.self) { tool in
                    control(for: tool, width: geometry.size.width)
                        .frame(width: 64, height: 64)
                }
            }
            .frame(width: CGFloat(visible.count) * 64, height: 64)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
        .frame(height: 64)
#endif
        }
        .sheet(isPresented: $showingPhotoInfo) {
            PhotoInformationSheet(document: document)
        }
    }

#if !os(macOS)
    private enum Tool: Hashable {
        case live, hdr, compare, full, open, info, save, more
    }

    private var optionalTools: [Tool] {
        var tools: [Tool] = [.open]
        if document.isLive { tools.append(.live) }
        if document.metadata.hdr { tools.append(.hdr) }
        tools.append(contentsOf: [.info, .compare, .full])
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
        let slots = min(7, max(2, Int((max(0, width - 32)) / 64)))
        return Array(optionalTools.prefix(slots - 2)) + [.save, .more]
    }

    private func overflowTools(for width: CGFloat) -> [Tool] {
        let visible = Set(visibleTools(for: width))
        return optionalTools.filter { !visible.contains($0) }
    }

    @ViewBuilder private func control(for tool: Tool, width: CGFloat) -> some View {
        switch tool {
        case .live:
            Toggle(isOn: $preview.playing) {
                CardLivePlaybackIndicator(controls: preview)
                    .frame(width: 24, height: 24)
            }
            .toggleStyle(.button)
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .controlSize(.large)
            .accessibilityLabel(Text("card.live.preview"))
            .help(Text("card.live.preview"))
            .padding(6)
        case .hdr:
            Toggle(isOn: $preview.hdr) {
                Label { Text("HDR") } icon: { Image("HDR") }
                    .labelStyle(.iconOnly)
                    .frame(width: 20, height: 20)
            }
            .toggleStyle(.button)
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .controlSize(.large)
            .accessibilityLabel(Text("HDR"))
            .help(Text("HDR"))
            .padding(6)
        case .compare:
            Toggle(isOn: $preview.original) {
                Label("card.compare", systemImage: "square.on.square")
                    .labelStyle(.iconOnly)
                    .frame(width: 20, height: 20)
            }
            .toggleStyle(.button)
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .controlSize(.large)
            .accessibilityLabel(Text("card.compare"))
            .help(Text("card.compare"))
            .padding(6)
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
        case .info:
            CircularIconButton("photo.info.open", systemImage: "info.circle") { showingPhotoInfo = true }
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
        Menu {
            ForEach(overflowTools(for: width), id: \.self) { menuAction(for: $0) }
            if let url = document.exportURL, !document.isLive {
                ShareLink(item: url) { Label("card.share", systemImage: "square.and.arrow.up") }
            }
            Button("card.style.reset", systemImage: "arrow.counterclockwise") {
                document.card.style = PhotoCardStyle()
            }
            Button(closeLabel, systemImage: "xmark", action: close)
        } label: {
            Image(systemName: "ellipsis").frame(width: 20, height: 20)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .controlSize(.large)
        .accessibilityLabel(Text("card.more"))
        .help(Text("card.more"))
        .padding(6)
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
        case .info:
            Button("photo.info.open", systemImage: "info.circle") { showingPhotoInfo = true }
        case .save, .more:
            EmptyView()
        }
    }
#endif
}

private struct CardFilmstrip: View {
    @Bindable var session: CardSession
    let preview: CardPreviewState
    let processing: Bool
    var body: some View {
        ZStack {
#if os(macOS)
            if let document=session.current {
                CardPreviewSurface(document:document,controls:preview).id(document.id)
                    .modifier(ProcessingVeil(active: processing, pulse: preview.isRendering))
                    .aspectRatio(CGFloat(document.metadata.width)/CGFloat(document.metadata.height),contentMode:.fit)
                    .frame(maxWidth:.infinity,maxHeight:.infinity)
                    .shadow(color:.black.opacity(0.5),radius:8,y:3)
            }
#else
            TabView(selection:$session.selectedID) {
                ForEach(session.documents) { document in
                    Group {
                        if document.id==session.selectedID {
                            CardPreviewSurface(document:document,controls:preview)
                        } else if abs((session.documents.firstIndex { $0.id==document.id } ?? 0)-selectedIndex)<=1 {
                            CardNeighborPreview(document:document,hdr:preview.hdr)
                        } else { Color.black }
                    }
                    .aspectRatio(CGFloat(document.metadata.width)/CGFloat(document.metadata.height),contentMode:.fit)
                    .frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.top)
                    .tag(Optional(document.id))
                }
            }
            .tabViewStyle(.page(indexDisplayMode:.never))
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
#if os(macOS)
        .background(Color(white:0.06))
#endif
    }
    private var selectedIndex:Int { session.documents.firstIndex { $0.id==session.selectedID } ?? 0 }
    private func move(_ amount:Int) {
        let next=selectedIndex+amount
        if session.documents.indices.contains(next) { session.selectedID=session.documents[next].id }
    }
}
