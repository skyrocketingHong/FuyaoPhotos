import SwiftUI

struct MapModesView: View {
    @Bindable var session: MapSession

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("sidebar.map.style").font(.headline)
                        MapStyleChoiceGrid(selection: $session.options.style, appearance: session.options.appearance)
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        Text("sidebar.display.mode").font(.headline)
                        MapDisplayModeChoiceGrid(selection: $session.displayMode)
                    }
                }
                .padding(20)
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .navigationTitle("map.modes")
#if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
        }
#if os(macOS)
        .frame(width: 460, height: 470)
#endif
    }
}
