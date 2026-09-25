import SwiftUI

struct SettingsView: View {
    @State private var card = CardPreferences.shared
    @AppStorage(CardAppearance.storageKey) private var cardAppearance = CardAppearance.darkroom.rawValue
    @AppStorage("defaultDisplayMode") private var defaultMode = MapDisplayMode.photo.rawValue
    @AppStorage("customStartYear") private var startYear = 0
    @AppStorage("defaultSelectedYear") private var selectedYear = 0
    private let currentYear = Calendar.current.component(.year, from: Date())
    private var version: String {
        let raw = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let marketing = raw.hasSuffix(".0") ? String(raw.dropLast(2)) : raw
        let build = Bundle.main.infoDictionary?["FuyaoBuildTrain"] as? String ?? ""
        return build.isEmpty ? marketing : "\(marketing) (\(build))"
    }

    var body: some View {
#if os(macOS)
        TabView {
            Tab("tab.cards", systemImage: "photo.badge.plus") {
                Form { cardSettings }
                    .formStyle(.grouped)
            }
            Tab("tab.map", systemImage: "map") {
                Form { mapSettings }
                    .formStyle(.grouped)
            }
            Tab("settings.about.header", systemImage: "info.circle") {
                Form { aboutSettings }
                    .formStyle(.grouped)
            }
        }
        .frame(minWidth: 500, minHeight: 420)
#else
        Form {
            cardSettings
            mapSettings
            aboutSettings
        }
        .formStyle(.grouped)
        .navigationTitle("settings.title")
        .navigationBarTitleDisplayMode(.inline)
#endif
    }

    @ViewBuilder private var cardSettings: some View {
        Section("card.information") {
            Picker("settings.card.appearance", selection: $cardAppearance) {
                Text("settings.appearance.darkroom").tag(CardAppearance.darkroom.rawValue)
                Text("settings.appearance.system").tag(CardAppearance.system.rawValue)
            }
            TextField("card.defaultAuthor", text: $card.author)
            Toggle("card.resolveLocation", isOn: $card.resolveLocation)
            Text("card.resolveLocation.description").font(.caption).foregroundStyle(.secondary)
        }
        Section("card.save.destination") {
            Toggle("card.save.update", isOn: $card.saveOptions.updateOriginal)
        }
        CardSaveControls(options: $card.saveOptions)
    }

    @ViewBuilder private var mapSettings: some View {
        Section("tab.map") {
            Picker("settings.default.display.mode", selection: $defaultMode) {
                ForEach(MapDisplayMode.allCases) { mode in Text(mode.localizedName).tag(mode.rawValue) }
            }
            Picker("settings.start.year", selection: $startYear) {
                Text("settings.year.auto").tag(0)
                ForEach(Array((1900...currentYear).reversed()), id: \.self) { Text(String($0)).tag($0) }
            }
            Picker("settings.default.year", selection: $selectedYear) {
                Text("year.filter.all").tag(0)
                ForEach(Array((1900...currentYear).reversed()), id: \.self) { Text(String($0)).tag($0) }
            }
        }
    }

    @ViewBuilder private var aboutSettings: some View {
        Section("settings.about.header") {
            LabeledContent("settings.version", value: version)
            Text("settings.apple.notice").font(.footnote).foregroundStyle(.secondary)
        }
    }
}
