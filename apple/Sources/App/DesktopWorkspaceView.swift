#if os(macOS)
import SwiftUI

struct DesktopWorkspaceView: View {
    @Bindable var workspace: PhotoWorkspace
    @State private var visibility = NavigationSplitViewVisibility.all
    @State private var roomySidebar: Bool?
    @State private var mapSession = MapSession()
    @State private var cardPreview = CardPreviewState()
    @State private var cardInspector = CardInspectorSelection()
    @State private var colors = ColorSamplingState()

    var body: some View {
        NavigationSplitView(columnVisibility: $visibility) {
            List(selection: Binding<PhotoWorkspace.Tab?>(get: { workspace.selectedTab }, set: { value in
                if let value { workspace.selectedTab = value }
            })) {
                ForEach(PhotoWorkspace.Tab.featureTabs) { tab in
                    Label(tab.title, systemImage: tab.symbol).tag(tab)
                }
            }
            .listStyle(.sidebar)
            .navigationTitle(Text(verbatim: "Fuyao Photos"))
            .navigationSplitViewColumnWidth(min: 180, ideal: 210, max: 260)
            .toolbar {
                ToolbarItem {
                    SettingsLink { Label("settings.title", systemImage: "gearshape") }
                        .labelStyle(.iconOnly).help(Text("settings.title"))
                }
            }
        } detail: {
            switch workspace.selectedTab {
            case .map: PhotoMapScreen(session: mapSession)
            case .cards: PhotoCardScreen(session: workspace.cards, preview: cardPreview, inspector: cardInspector)
            case .metadata: MetadataScreen(state: workspace.metadataEdits)
            case .colors: ColorsScreen(sampling: colors)
            case .settings: PhotoCardScreen(session: workspace.cards, preview: cardPreview, inspector: cardInspector)
            }
        }
        .navigationSplitViewStyle(.balanced)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width in
            guard width > 0 else { return }
            let roomy = width >= 1120
            guard roomySidebar != roomy else { return }
            visibility = roomy ? .all : .detailOnly
            roomySidebar = roomy
        }
    }
}
#endif
