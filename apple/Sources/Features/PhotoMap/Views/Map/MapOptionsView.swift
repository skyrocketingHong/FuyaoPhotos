import SwiftUI
import PhotoMapCore

struct MapOptionsView: View {
    @Bindable var session: MapSession
    @Environment(\.dismiss) private var dismiss

    var body: some View {
#if os(macOS)
        VStack(alignment: .leading, spacing: 0) {
            Text("map.options")
                .font(.headline)
                .padding(.horizontal, 20)
                .padding(.top, 18)
            Form { optionSections }
                .formStyle(.columns)
                .padding(.horizontal, 20)
        }
        .frame(width: 390, height: session.displayMode == .heatmap ? 580 : 500)
#else
        NavigationStack {
            Form { optionSections }
            .formStyle(.grouped)
            .navigationTitle("map.options")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("done", action: dismiss.callAsFunction) }
            }
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
            .tint(.secondary)
        }
        if session.displayMode == .heatmap {
            Section("sidebar.display.mode") {
                LabeledContent("map.heat.radius") {
                    Slider(value: $session.options.heatRadius, in: 32...120)
                        .accessibilityLabel(Text("map.heat.radius"))
                }
                LabeledContent("map.heat.opacity") {
                    Slider(value: $session.options.heatOpacity, in: 0.3...1)
                        .accessibilityLabel(Text("map.heat.opacity"))
                }
            }
        }
        Section {
            Picker("map.coordinates.alignment", selection: $session.coordinateSystem) {
                Text("map.coordinates.gcj02").tag(MapCoordinateSystem.gcj02)
                Text("map.coordinates.wgs84").tag(MapCoordinateSystem.wgs84)
            }
            .tint(.secondary)
        } footer: {
            Text("map.coordinates.description")
        }
        Section("sidebar.map.style") {
            Picker("map.appearance", selection: $session.options.appearance) {
                ForEach(MapAppearance.allCases) { Text($0.title).tag($0) }
            }
            .tint(.secondary)
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
