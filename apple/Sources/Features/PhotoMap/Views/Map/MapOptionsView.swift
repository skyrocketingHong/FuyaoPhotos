import SwiftUI
import PhotoMapCore

struct MapOptionsView: View {
    @Bindable var session: MapSession

    var body: some View {
#if os(macOS)
        VStack(alignment: .leading, spacing: 0) {
            Text("map.options")
                .font(.headline)
                .padding(.horizontal, 20)
                .padding(.top, 18)
            Form { optionSections }
                .photoPageForm()
        }
        .frame(width: 390, height: session.displayMode == .heatmap ? 580 : 500)
#else
        NavigationStack {
            Form { optionSections }
            .photoPageForm()
            .navigationTitle("map.options")
            .navigationBarTitleDisplayMode(.inline)
        }
#endif
    }

    @ViewBuilder
    private var optionSections: some View {
        Section {
            Picker("year.filter.title", selection: $session.selectedYear) {
                Text(L10n.YearFilter.all).tag(nil as Int?)
                ForEach(session.availableYears, id: \.self) { Text($0, format: .number.grouping(.never)).tag(Optional($0)) }
            }
            .photoFormMenuPickerStyle()
        }
        if session.displayMode == .heatmap {
            Section("map.heat.radius") {
                PhotoFormSlider(title: "map.heat.radius", value: $session.options.heatRadius, range: 32...120,
                    minimumLabel: "map.heat.radius.minimum", maximumLabel: "map.heat.radius.maximum", showsTitle: false)
            }
            Section("map.heat.opacity") {
                PhotoFormSlider(title: "map.heat.opacity", value: $session.options.heatOpacity, range: 0.3...1,
                    minimumLabel: "map.heat.opacity.minimum", maximumLabel: "map.heat.opacity.maximum", showsTitle: false)
            }
        }
        Section {
            Picker("map.coordinates.alignment", selection: $session.coordinateSystem) {
                Text("map.coordinates.gcj02").tag(MapCoordinateSystem.gcj02)
                Text("map.coordinates.wgs84").tag(MapCoordinateSystem.wgs84)
            }
            .photoFormMenuPickerStyle()
        } footer: {
            Text("map.coordinates.description")
        }
        Section("sidebar.map.style") {
            Picker("map.appearance", selection: $session.options.appearance) {
                ForEach(MapAppearance.allCases) { Text($0.title).tag($0) }
            }
            .photoFormMenuPickerStyle()
            Group {
                Toggle("map.traffic", isOn: $session.options.traffic)
                Toggle("map.points", isOn: $session.options.pointsOfInterest)
                Toggle("map.elevation", isOn: $session.options.realisticElevation)
                Toggle("map.compass", isOn: $session.options.compass)
                Toggle("map.scale", isOn: $session.options.scale)
            }
#if os(iOS)
            .tint(.green)
#endif
        }
    }
}
