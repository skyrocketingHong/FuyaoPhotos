import SwiftUI
import MapKit

struct ContentView: View {
    @State private var workspace = PhotoWorkspace()
    @AppStorage(CardAppearance.storageKey) private var cardAppearance = CardAppearance.system.rawValue
    private var darkroomCards: Bool { cardAppearance == CardAppearance.darkroom.rawValue }
    var body: some View {
        TabView(selection: $workspace.selectedTab) {
            Tab("tab.map", systemImage: "map", value: PhotoWorkspace.Tab.map) {
                PhotoMapScreen()
#if !os(macOS)
                    .toolbarBackground(.hidden, for: .tabBar)
#endif
            }
            Tab("tab.cards", systemImage: "photo.badge.plus", value: PhotoWorkspace.Tab.cards) {
                PhotoCardScreen(session: workspace.cards)
#if !os(macOS)
                    .toolbarBackground(.visible, for: .tabBar)
                    .toolbarColorScheme(darkroomCards ? .dark : nil, for: .tabBar)
#endif
            }
            Tab("tab.metadata", systemImage: "info.circle", value: PhotoWorkspace.Tab.metadata) {
                MetadataScreen(state: workspace.metadataEdits)
#if !os(macOS)
                    .toolbarBackground(.visible, for: .tabBar)
#endif
            }
            Tab("tab.colors", systemImage: "eyedropper.halffull", value: PhotoWorkspace.Tab.colors) {
                ColorsScreen()
            }
#if !os(macOS)
            if #available(iOS 27, *) {
                Tab("settings.title", systemImage: "gearshape", value: PhotoWorkspace.Tab.settings, role: .prominent) {
                    NavigationStack { SettingsView() }
                }
            } else {
                Tab("settings.title", systemImage: "gearshape", value: PhotoWorkspace.Tab.settings) {
                    NavigationStack { SettingsView() }
                }
            }
#endif
        }
        .tabViewStyle(.tabBarOnly)
        .tint(.yellow)
#if os(iOS)
        .background {
            NativeTabSelectionStyle(color: .yellow, selection: workspace.selectedTab)
                .frame(width: 0, height: 0).accessibilityHidden(true)
        }
#endif
#if os(macOS)
        .frame(minWidth: 760, minHeight: 560)
#endif
        .environment(workspace)
    }
}

struct PhotoMapScreen: View {
    @Namespace private var mapScope
    @State private var session = MapSession()
    @State private var showingOptions = false
    @State private var showingModes = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var editAfterDismiss: [String]?
    @State private var availableWidth: CGFloat = 0
    @State private var clusterNavigationPath: [PhotoLocation] = []
    @Environment(PhotoWorkspace.self) private var workspace

    private var showsSelectionPane: Bool { availableWidth >= 900 }
    private var selectionPaneWidth: CGFloat { min(420, max(320, availableWidth * 0.34)) }

    var body: some View {
        @Bindable var session = session
        NavigationStack {
            HStack(spacing: 0) {
                MapContainerView(session: session, scope: mapScope)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
#if !os(macOS)
                    .overlay(alignment: .bottomTrailing) {
                        VStack(spacing: 0) {
                            mapStyleMenu.frame(width: 52, height: 52)
                            mapOptionsButton.frame(width: 52, height: 52)
                            fitPhotosButton.frame(width: 52, height: 52)
                            Divider().frame(width: 28)
                            locationButton.frame(width: 52, height: 52)
                        }
                        .frame(width: 52)
                        .glassEffect(.regular.interactive(), in: .capsule)
                        .fixedSize(horizontal: true, vertical: true)
                        .labelStyle(.iconOnly)
                        .buttonStyle(.plain)
                        .foregroundStyle(.primary)
                        .padding(20)
                    }
#endif
                if showsSelectionPane, let presentation = session.presentation {
                    Divider()
                    MapSelectionPane(presentation: presentation, session: session,
                                     navigationPath: $clusterNavigationPath,
                                     close: { session.presentation = nil }, addCard: addCard)
                        .id(presentation.id)
                        .frame(width: selectionPaneWidth)
                        .frame(maxHeight: .infinity)
                        .background(.regularMaterial)
                        .transition(.move(edge: .trailing).combined(with: .opacity))
                }
            }
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: session.presentation?.id)
            .safeAreaInset(edge: .top, spacing: 0) {
                if session.phase == .loading {
                    PhotoPageIntro(title: "tab.map", description: "map.intro.description", symbol: "map")
                        .padding(20)
                        .background(.background, in: .rect(cornerRadius: 20))
                        .padding(.horizontal, 20)
                        .padding(.top, 8)
                }
            }
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { availableWidth = $0 }
#if !os(macOS)
            .toolbarVisibility(.hidden, for: .navigationBar)
#else
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    mapStyleMenu
                        .labelStyle(.iconOnly)
                        .buttonBorderShape(.circle)
                        .help(Text("sidebar.map.style"))
                    YearFilterMenu(selectedYear: $session.selectedYear, availableYears: session.availableYears)
                        .accessibilityLabel(Text("year.filter.title"))
                        .buttonBorderShape(.circle)
                        .help(Text("year.filter.title"))
                }
                ToolbarItemGroup(placement: .primaryAction) {
                    mapOptionsButton
                        .labelStyle(.iconOnly)
                        .buttonBorderShape(.circle)
                        .help(Text("map.options"))
                    fitPhotosButton
                        .labelStyle(.iconOnly)
                        .buttonBorderShape(.circle)
                        .help(Text("map.fit.photos"))
                    locationButton.labelStyle(.iconOnly).help(Text("map.location"))
                }
            }
#endif
        }
        .mapScope(mapScope)
        .modifier(MapLifecycleModifier(session: session))
        .onChange(of: session.presentation?.id) { _, _ in clusterNavigationPath = [] }
        .sheet(item: Binding(
            get: { showsSelectionPane ? nil : session.presentation },
            set: { if !showsSelectionPane { session.presentation = $0 } }
        ), onDismiss: {
            if let ids = editAfterDismiss { editAfterDismiss = nil; workspace.editPhotos(ids) }
        }) { presentation in
            switch presentation {
            case .photo(let location):
                PhotoDetailSheet(location: location, thumbnails: session.library.thumbnails,
                                 indexVersion: session.library.indexVersion, addCard: addCard)
            case .cluster(let selection):
                ClusterPhotosView(selection: selection, library: session.library, addCard: addCard,
                                  navigationPath: $clusterNavigationPath)
            }
        }
    }

    private func addCard(_ id: String) {
        if showsSelectionPane {
            session.presentation = nil
            workspace.editPhotos([id])
        } else {
            editAfterDismiss = [id]
            session.presentation = nil
        }
    }

    private var mapStyleMenu: some View {
        Button { showingModes = true } label: {
            MapActionLabel(title: "map.modes", symbol: session.options.style.icon)
        }
            .popover(isPresented: $showingModes) {
                MapModesView(session: session)
                    .modifier(NativePresentationDefaults())
#if os(iOS)
                    .presentationCompactAdaptation(.sheet)
                    .presentationDetents([.medium, .large])
#endif
            }
    }

    private var locationButton: some View {
        Button {
            session.locateUser(animated: !reduceMotion)
        } label: {
            if session.location.locating { ProgressView().controlSize(.small) }
            else { MapActionLabel(title: "map.location", symbol: "location.fill") }
        }
        .disabled(session.location.locating)
        .accessibilityLabel(Text("map.location"))
        .alert("map.location", isPresented: Binding(get: { session.location.errorMessage != nil },
            set: { if !$0 { session.location.errorMessage = nil } })) {
                Button("done", role: .cancel) { }
            } message: { Text(session.location.errorMessage ?? "") }
    }

    private var mapOptionsButton: some View {
        Button { showingOptions = true } label: {
            MapActionLabel(title: "map.options", symbol: "slider.horizontal.3")
        }
            .popover(isPresented: $showingOptions) {
                MapOptionsView(session: session)
                    .modifier(NativePresentationDefaults())
#if !os(macOS)
                    .presentationCompactAdaptation(.sheet)
                    .presentationDetents([.medium, .large])
#endif
            }
    }

    private var fitPhotosButton: some View {
        Button {
            Task { await session.fitPhotos(animated: !reduceMotion) }
        } label: {
            MapActionLabel(title: "map.fit.photos", symbol: "arrow.up.left.and.arrow.down.right")
        }
        .disabled(!session.hasQueryResult)
    }
}

private struct MapActionLabel: View {
    let title: LocalizedStringKey
    let symbol: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        Label {
            Text(title)
        } icon: {
            Image(systemName: symbol)
#if os(iOS)
                .resizable().scaledToFit().frame(width: 24, height: 24)
#endif
                .contentTransition(reduceMotion ? .identity : .symbolEffect(.replace))
                .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: symbol)
        }
#if os(iOS)
            .frame(width: 52, height: 52)
            .contentShape(Rectangle())
#endif
    }
}

private struct MapSelectionPane: View {
    let presentation: MapPresentation
    let session: MapSession
    @Binding var navigationPath: [PhotoLocation]
    let close: () -> Void
    let addCard: (String) -> Void

    var body: some View {
        switch presentation {
        case .photo(let location):
            PhotoDetailSheet(location: location, thumbnails: session.library.thumbnails,
                             indexVersion: session.library.indexVersion, addCard: addCard,
                             embedded: true, onClose: close)
        case .cluster(let selection):
            ClusterPhotosView(selection: selection, library: session.library, addCard: addCard,
                              navigationPath: $navigationPath, embedded: true, onClose: close)
        }
    }
}

private struct MapLifecycleModifier: ViewModifier {
    let session: MapSession
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .task { await session.activate() }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { Task { await session.activate() } }
                else { session.pause() }
            }
            .onDisappear { session.pause() }
            .onChange(of: session.library.indexVersion) { _, _ in session.indexDidChange() }
            .onChange(of: session.library.authorizationStatus) { _, _ in session.authorizationDidChange() }
            .onChange(of: AppSettings.shared.customStartYear) { _, _ in session.applySettings() }
    }
}
