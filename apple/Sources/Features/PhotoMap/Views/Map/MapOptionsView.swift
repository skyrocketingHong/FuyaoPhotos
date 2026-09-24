import SwiftUI

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
        .frame(width: 370, height: session.displayMode == .heatmap ? 400 : 340)
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
        Section("sidebar.map.style") {
            Picker("map.appearance", selection: $session.options.appearance) {
                ForEach(MapAppearance.allCases) { Text($0.title).tag($0) }
            }
            Toggle("map.traffic", isOn: $session.options.traffic)
            Toggle("map.points", isOn: $session.options.pointsOfInterest)
            Toggle("map.elevation", isOn: $session.options.realisticElevation)
            Toggle("map.compass", isOn: $session.options.compass)
            Toggle("map.scale", isOn: $session.options.scale)
        }
    }
}
