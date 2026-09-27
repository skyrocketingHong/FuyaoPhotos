import SwiftUI
import MapKit

struct ContentView: View {
    @State private var workspace = PhotoWorkspace()
    @AppStorage(CardAppearance.storageKey) private var cardAppearance = CardAppearance.darkroom.rawValue
    private var darkroomCards: Bool { cardAppearance != CardAppearance.system.rawValue }
    var body: some View {
        TabView(selection: $workspace.selectedTab) {
            Tab("tab.map", systemImage: "map", value: PhotoWorkspace.Tab.map) {
                PhotoMapScreen()
#if !os(macOS)
                    .toolbarBackground(.hidden, for: .tabBar)
#endif
                    .modifier(TabContentEntrance(active: workspace.selectedTab == .map))
            }
            Tab("tab.cards", systemImage: "photo.badge.plus", value: PhotoWorkspace.Tab.cards) {
                PhotoCardScreen(session: workspace.cards)
#if !os(macOS)
                    .toolbarBackground(.visible, for: .tabBar)
                    .toolbarColorScheme(darkroomCards ? .dark : nil, for: .tabBar)
#endif
                    .modifier(TabContentEntrance(active: workspace.selectedTab == .cards, darkroom: darkroomCards))
            }
            Tab("tab.metadata", systemImage: "info.circle", value: PhotoWorkspace.Tab.metadata) {
                MetadataScreen(session: workspace.cards)
#if !os(macOS)
                    .toolbarBackground(.visible, for: .tabBar)
#endif
                    .modifier(TabContentEntrance(active: workspace.selectedTab == .metadata))
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
        .tint(workspace.selectedTab == .cards ? .yellow : .accentColor)
#if os(macOS)
        .frame(minWidth: 760, minHeight: 560)
#endif
        .environment(workspace)
    }
}

private struct TabContentEntrance: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visible = false
    let active: Bool
    var darkroom = false

    func body(content: Content) -> some View {
        ZStack {
            if darkroom { Color.black.ignoresSafeArea() }
            content.opacity(visible ? 1 : 0)
        }
        .onChange(of: active, initial: true) { _, isActive in
            if reduceMotion {
                visible = isActive
            } else {
                withAnimation(.easeOut(duration: 0.2)) { visible = isActive }
            }
        }
    }
}

struct PhotoMapScreen: View {
    @Namespace private var mapScope
    @State private var session = MapSession()
    @State private var showingOptions = false
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
                    .overlay(alignment: .topTrailing) {
                        VStack(alignment: .trailing, spacing: 16) {
                            mapStyleMenu
                            DisplayModeMenu(displayMode: $session.displayMode)
                                .accessibilityLabel(Text("sidebar.display.mode"))
                            YearFilterMenu(selectedYear: $session.selectedYear, availableYears: session.availableYears)
                                .accessibilityLabel(Text("year.filter.title"))
                            mapOptionsButton
                            fitPhotosButton
                            if session.displayMode != .heatmap && session.options.compass { MapCompass(scope: mapScope) }
                        }
                        .labelStyle(.iconOnly)
                        .buttonStyle(.glass)
                        .buttonBorderShape(.circle)
                        .controlSize(.large)
                        .padding(20)
                    }
#else
                    .overlay(alignment: .topTrailing) {
                        if session.displayMode != .heatmap && session.options.compass {
                            MapCompass(scope: mapScope)
                                .padding(20)
                        }
                    }
#endif
                    .overlay(alignment: .bottomLeading) {
                        if session.hasQueryResult {
                            Text("photo.count.visible \(session.visiblePhotoCount)")
                                .font(.caption.monospacedDigit())
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .glassEffect(in: .capsule)
                                .padding(12)
                        }
                    }
                if showsSelectionPane, let presentation = session.presentation {
                    Divider()
                    MapSelectionPane(presentation: presentation, session: session,
                                     navigationPath: $clusterNavigationPath,
                                     close: { session.presentation = nil }, addCard: addCard)
                        .id(presentation.id)
                        .frame(width: selectionPaneWidth)
                        .frame(maxHeight: .infinity)
                        .background(.regularMaterial)
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
                    DisplayModeMenu(displayMode: $session.displayMode)
                        .labelStyle(.iconOnly)
                        .buttonBorderShape(.circle)
                        .help(Text("sidebar.display.mode"))
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
        @Bindable var session = session
        return Menu {
            Picker("sidebar.map.style", selection: $session.options.style) {
                ForEach(MapStyleMode.allCases) { style in
                    Label(style.localizedName, systemImage: style.icon).tag(style)
                }
            }
        } label: {
            Label("sidebar.map.style", systemImage: "map")
        }
        .accessibilityLabel(Text("sidebar.map.style"))
    }

    private var mapOptionsButton: some View {
        Button("map.options", systemImage: "slider.horizontal.3") { showingOptions = true }
            .popover(isPresented: $showingOptions) {
                MapOptionsView(session: session)
#if !os(macOS)
                    .presentationCompactAdaptation(.sheet)
                    .presentationDetents([.medium, .large])
#endif
            }
    }

    private var fitPhotosButton: some View {
        Button("map.fit.photos", systemImage: "arrow.up.left.and.arrow.down.right") {
            Task { await session.fitPhotos() }
        }
        .disabled(!session.hasQueryResult)
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
