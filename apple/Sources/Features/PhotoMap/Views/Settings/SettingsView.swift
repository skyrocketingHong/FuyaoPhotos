import SwiftUI

struct SettingsView: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var card = CardPreferences.shared
    @State private var workspacePreferences = WorkspacePreferences.shared
    @AppStorage(CardAppearance.storageKey) private var cardAppearance = CardAppearance.system.rawValue
    @AppStorage("defaultDisplayMode") private var defaultMode = MapDisplayMode.photo.rawValue
    @AppStorage("customStartYear") private var startYear = 0
    @AppStorage("defaultSelectedYear") private var selectedYear = 0
    @State private var selectedCategory: Category? = .cards
    @State private var showLenses = false
    @State private var lensWorkspace: LensWorkspaceDraft?
    @State private var pendingCategory: Category?
    @State private var confirmCategoryChange = false
    @State private var lensDraftRevision = UUID()
    private let currentYear = Calendar.current.component(.year, from: Date())
    private var lensesDirty: Bool { lensWorkspace?.hasPendingChanges == true }

    private enum Category: Hashable, CaseIterable {
        case cards, lenses, map, about

        var title: LocalizedStringKey {
            switch self {
            case .cards: "tab.cards"
            case .lenses: "lens.profiles.title"
            case .map: "tab.map"
            case .about: "settings.about.header"
            }
        }

        var description: LocalizedStringKey {
            switch self {
            case .cards: "settings.category.cards.description"
            case .lenses: "lens.profiles.description"
            case .map: "settings.category.map.description"
            case .about: "settings.category.about.description"
            }
        }

        var symbol: String {
            switch self {
            case .cards: "photo.badge.plus"
            case .lenses: "camera.aperture"
            case .map: "map"
            case .about: "info.circle"
            }
        }
    }
    private var version: String {
        let raw = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let marketing = raw.hasSuffix(".0") ? String(raw.dropLast(2)) : raw
        let build = Bundle.main.infoDictionary?["FuyaoBuildTrain"] as? String ?? ""
        return build.isEmpty ? marketing : "\(marketing) (\(build))"
    }

    var body: some View {
        GeometryReader { geometry in
            if usesSidebar(width: geometry.size.width) {
                NavigationSplitView {
                    List(Category.allCases, id: \.self, selection: Binding(get: { selectedCategory }, set: selectCategory)) { category in
                        Label(category.title, systemImage: category.symbol).tag(category)
                    }
                    .listStyle(.sidebar)
                    .navigationTitle("settings.title")
                    .navigationSplitViewColumnWidth(min: 170, ideal: 200, max: 240)
                } detail: {
                    if selectedCategory == .lenses {
                        LensProfilesView(store: .shared, workspace: lensWorkspace, embedded: true)
                            .id(lensDraftRevision)
                    } else {
                        categoryForm(selectedCategory ?? .cards)
                    }
                }
                .navigationSplitViewStyle(.balanced)
            } else {
                Form {
                    settingsIntro
                    workspaceSettings
                    cardSettings
                    lensSettings
                    mapSettings
                    aboutSettings
                }
                .photoPageForm()
            }
        }
        .tint(.secondary)
        .toggleStyle(NativeFormToggleStyle())
        .sheet(isPresented: $showLenses) {
            LensProfilesView(store: .shared)
                .presentationSizing(.page)
                .presentationDetents([.large])
        }
        .confirmationDialog("lens.discard.title", isPresented: $confirmCategoryChange, titleVisibility: .visible) {
            Button("lens.discard", role: .destructive) {
                lensWorkspace = nil; lensDraftRevision = UUID()
                selectedCategory = pendingCategory ?? selectedCategory; pendingCategory = nil
            }
            Button("lens.keepEditing", role: .cancel) { pendingCategory = nil }
        } message: { Text("lens.discard.message") }
    }

    private func usesSidebar(width: CGFloat) -> Bool {
#if os(macOS)
        true
#else
        width >= 760 || selectedCategory == .lenses
#endif
    }

    private func selectCategory(_ category: Category?) {
        guard let category, category != selectedCategory else { return }
        if selectedCategory == .lenses && lensesDirty {
            pendingCategory = category; confirmCategoryChange = true
        } else {
            if category == .lenses && !lensesDirty { lensWorkspace = LensWorkspaceDraft(profiles: LensProfileStore.shared.profiles) }
            selectedCategory = category
        }
    }

    private func categoryForm(_ category: Category) -> some View {
        Form {
            Section { Text(category.description).foregroundStyle(.secondary) }
            switch category {
            case .cards: workspaceSettings; cardSettings
            case .lenses: EmptyView()
            case .map: mapSettings
            case .about: aboutSettings
            }
        }
        .photoPageForm()
        .navigationTitle(category.title)
    }

    private var lensSettings: some View {
        Section {
            Button { showLenses = true } label: {
                Label("lens.profiles.title", systemImage: "camera.aperture")
            }
        } header: { Text("lens.settings.header") }
        footer: { Text("lens.settings.footer") }
    }

    private var settingsIntro: some View {
        Section {
            PhotoPageIntro(title: "settings.title", description: "settings.intro", symbol: "gearshape")
        }
    }

    private var workspaceSettings: some View {
        Section {
            Picker("workspace.startup", selection: $workspacePreferences.startup) {
                ForEach(PhotoWorkspace.Tab.featureTabs) { Text($0.title).tag($0) }
            }
            Picker("workspace.sharing", selection: $workspacePreferences.sharing) {
                ForEach(PhotoSharingMode.allCases) { Text($0.title).tag($0) }
            }
            if workspacePreferences.sharing == .partial {
                ForEach(PhotoWorkspace.Tab.photoTabs) { tab in
                    Toggle(tab.title, isOn: Binding(
                        get: { workspacePreferences.sharedTabs.contains(tab) },
                        set: { enabled in
                            if enabled { workspacePreferences.sharedTabs.insert(tab) }
                            else { workspacePreferences.sharedTabs.remove(tab) }
                        }))
                }
            }
        } header: { Text("workspace.header") }
        footer: { Text("workspace.sharing.description") }
        .tint(.secondary)
        .toggleStyle(NativeFormToggleStyle())
        .animation(reduceMotion ? nil : .smooth(duration: 0.2), value: workspacePreferences.sharing)
    }

    @ViewBuilder private var cardSettings: some View {
        Section {
            Picker(selection: $cardAppearance) {
                Text("settings.appearance.darkroom").tag(CardAppearance.darkroom.rawValue)
                Text("settings.appearance.system").tag(CardAppearance.system.rawValue)
            } label: {
                settingLabel("settings.card.appearance", hint: "settings.appearance.hint")
            }
        } header: {
            Text("settings.editor.header")
        } footer: {
            Text("settings.editor.footer")
        }
        Section {
            LabeledContent("card.defaultAuthor") {
                TextField("card.defaultAuthor", text: $card.author)
                    .labelsHidden().multilineTextAlignment(.trailing)
            }
            Toggle(isOn: $card.resolveLocation) {
                settingLabel("card.resolveLocation", hint: "card.resolveLocation.description")
            }
        } header: {
            Text("settings.photo.header")
        }
        Section {
            Toggle(isOn: $card.saveOptions.updateOriginal) {
                settingLabel("card.save.update", hint: "settings.save.destination.hint")
            }
        } header: {
            Text("settings.save.header")
        } footer: {
            Text("settings.save.footer")
        }
        CardSaveControls(options: $card.saveOptions)
    }

    @ViewBuilder private var mapSettings: some View {
        Section {
            Picker(selection: $defaultMode) {
                ForEach(MapDisplayMode.allCases) { mode in Text(mode.localizedName).tag(mode.rawValue) }
            } label: {
                settingLabel("settings.default.display.mode", hint: "settings.map.mode.hint")
            }
            Picker(selection: $startYear) {
                Text("settings.year.auto").tag(0)
                ForEach(Array((1900...currentYear).reversed()), id: \.self) { Text(String($0)).tag($0) }
            } label: {
                settingLabel("settings.start.year", hint: "settings.start.year.footer")
            }
            Picker(selection: $selectedYear) {
                Text("year.filter.all").tag(0)
                ForEach(Array((1900...currentYear).reversed()), id: \.self) { Text(String($0)).tag($0) }
            } label: {
                settingLabel("settings.default.year", hint: "settings.default.year.footer")
            }
        } header: {
            Text("settings.map.header")
        }
    }

    private func settingLabel(_ title: LocalizedStringKey, hint: LocalizedStringKey) -> some View {
        Text(title).accessibilityHint(Text(hint))
    }

    @ViewBuilder private var aboutSettings: some View {
        Section("settings.about.header") {
            LabeledContent("settings.version", value: version)
            Text("about.project")
            Text("about.features").foregroundStyle(.secondary)
            Link("AGPL-3.0-only", destination: URL(string: "https://github.com/skyrocketingHong/FuyaoPhotos/blob/main/LICENSE")!)
            Link("GitHub", destination: URL(string: "https://github.com/skyrocketingHong/FuyaoPhotos")!)
            Text("about.colors.credit").font(.footnote).foregroundStyle(.secondary)
            Text("settings.apple.notice").font(.footnote).foregroundStyle(.secondary)
        }
    }
}
