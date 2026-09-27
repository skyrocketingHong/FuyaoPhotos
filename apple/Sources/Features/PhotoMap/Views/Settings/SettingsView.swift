import SwiftUI

struct SettingsView: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var card = CardPreferences.shared
    @AppStorage(CardAppearance.storageKey) private var cardAppearance = CardAppearance.darkroom.rawValue
    @AppStorage("defaultDisplayMode") private var defaultMode = MapDisplayMode.photo.rawValue
    @AppStorage("customStartYear") private var startYear = 0
    @AppStorage("defaultSelectedYear") private var selectedYear = 0
    @State private var selectedCategory: Category? = .cards
    @State private var showLenses = false
    private let currentYear = Calendar.current.component(.year, from: Date())

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
            if geometry.size.width >= 760 && !dynamicTypeSize.isAccessibilitySize {
                HStack(spacing: 0) {
                    List(Category.allCases, id: \.self, selection: $selectedCategory) { category in
                        Label(category.title, systemImage: category.symbol)
                            .tag(category)
                    }
                    .listStyle(.sidebar)
                    .frame(width: 220)
                    Divider()
                    Form {
                        Section { categoryIntro(selectedCategory ?? .cards) }
                        switch selectedCategory ?? .cards {
                        case .cards: cardSettings
                        case .lenses: lensSettings
                        case .map: mapSettings
                        case .about: aboutSettings
                        }
                    }
                    .formStyle(.grouped)
                    .frame(maxWidth: .infinity)
                }
            } else {
#if os(macOS)
                TabView {
                    Tab("tab.cards", systemImage: "photo.badge.plus") {
                        Form { settingsIntro; cardSettings }.formStyle(.grouped)
                    }
                    Tab("lens.profiles.title", systemImage: "camera.aperture") {
                        Form { Section { categoryIntro(.lenses) }; lensSettings }.formStyle(.grouped)
                    }
                    Tab("tab.map", systemImage: "map") {
                        Form { settingsIntro; mapSettings }.formStyle(.grouped)
                    }
                    Tab("settings.about.header", systemImage: "info.circle") {
                        Form { settingsIntro; aboutSettings }.formStyle(.grouped)
                    }
                }
#else
                Form {
                    settingsIntro
                    cardSettings
                    lensSettings
                    mapSettings
                    aboutSettings
                }
                .formStyle(.grouped)
#endif
            }
        }
        .sheet(isPresented: $showLenses) { LensProfilesView(store: .shared) }
#if os(macOS)
        .frame(minWidth: 500, minHeight: 420)
#else
        .navigationTitle("settings.title")
        .navigationBarTitleDisplayMode(.inline)
#endif
    }

    private var lensSettings: some View {
        Section {
            Button { showLenses = true } label: {
                Label("lens.profiles.title", systemImage: "camera.aperture")
            }
        } header: { Text("lens.settings.header") }
        footer: { Text("lens.settings.footer") }
    }

    private func categoryIntro(_ category: Category) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: category.symbol)
                .font(.title)
                .foregroundStyle(.primary)
                .frame(width: 56, height: 56)
                .background(.fill.tertiary, in: .rect(cornerRadius: 12))
                .accessibilityHidden(true)
            Text(category.title)
                .font(.title.bold())
            Text(category.description)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
    }

    private var settingsIntro: some View {
        Section {
            VStack(alignment: .leading, spacing: 12) {
                Image(systemName: "gearshape")
                    .font(.title)
                    .foregroundStyle(.primary)
                    .frame(width: 56, height: 56)
                    .background(.fill.tertiary, in: .rect(cornerRadius: 12))
                    .accessibilityHidden(true)
                Text("settings.title")
                    .font(.title.bold())
                Text("settings.intro")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 8)
        }
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
            VStack(alignment: .leading, spacing: 4) {
                TextField("card.defaultAuthor", text: $card.author)
                Text("settings.author.hint")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Toggle(isOn: $card.resolveLocation) {
                settingLabel("card.resolveLocation", hint: "card.resolveLocation.description")
            }
        } header: {
            Text("settings.photo.header")
        }
        Section {
            Toggle(isOn: $card.metadataSharesCards) {
                settingLabel("metadata.sharesCards", hint: "metadata.sharesCards.footer")
            }
        } header: {
            Text("metadata.settings.header")
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
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
            Text(hint)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    @ViewBuilder private var aboutSettings: some View {
        Section("settings.about.header") {
            LabeledContent("settings.version", value: version)
            Text("settings.apple.notice").font(.footnote).foregroundStyle(.secondary)
        }
    }
}
