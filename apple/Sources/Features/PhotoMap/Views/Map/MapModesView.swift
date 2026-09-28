import SwiftUI
import MapKit

struct MapModesView: View {
    @Bindable var session: MapSession
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("sidebar.map.style").font(.headline)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: dynamicTypeSize.isAccessibilitySize ? 130 : 76), alignment: .top)], spacing: 12) {
                            ForEach(MapStyleMode.allCases) { style in
                                Button { session.options.style = style } label: {
                                    choice(title: style.localizedName, selected: session.options.style == style) {
                                        MapStylePreview(style: style, region: session.currentRegion)
                                            .environment(\.colorScheme, session.options.appearance.colorScheme ?? colorScheme)
                                    }
                                }
                                .accessibilityAddTraits(session.options.style == style ? .isSelected : [])
                            }
                        }
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        Text("sidebar.display.mode").font(.headline)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: dynamicTypeSize.isAccessibilitySize ? 130 : 90), alignment: .top)], spacing: 12) {
                            ForEach(MapDisplayMode.allCases) { mode in
                                Button { session.displayMode = mode } label: {
                                    choice(title: mode.localizedName, selected: session.displayMode == mode) {
                                        Image("MapPreview-" + mode.id).resizable().scaledToFit()
                                    }
                                }
                                .accessibilityAddTraits(session.displayMode == mode ? .isSelected : [])
                            }
                        }
                    }
                }
                .padding(20)
                .buttonStyle(.plain)
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .navigationTitle("map.modes")
#if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("done", systemImage: "xmark", action: dismiss.callAsFunction).labelStyle(.iconOnly)
                }
            }
        }
#if os(macOS)
        .frame(width: 460, height: 470)
#endif
    }

    private func choice<Preview: View>(title: LocalizedStringKey, selected: Bool,
                                      @ViewBuilder preview: () -> Preview) -> some View {
        VStack(spacing: 8) {
            preview().aspectRatio(1, contentMode: .fit)
                .clipShape(.rect(cornerRadius: 14))
                .overlay { RoundedRectangle(cornerRadius: 14).strokeBorder(selected ? Color.accentColor : .clear, lineWidth: 3) }
                .accessibilityHidden(true)
            Text(title).font(.subheadline.weight(.medium)).foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .top)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(title))
    }
}

private struct MapStylePreview: View {
    let style: MapStyleMode
    let region: MKCoordinateRegion
    @Environment(\.colorScheme) private var colorScheme
    @State private var image: Image?
    @State private var request: MKMapSnapshotter?
    @State private var failed = false

    var body: some View {
        ZStack {
            Rectangle().fill(.fill.quaternary)
            if let image { image.resizable().scaledToFit() }
            else if failed {
                VStack(spacing: 6) {
                    Image(systemName: style.icon).font(.title2)
                    Text("map.preview.unavailable").font(.caption2).multilineTextAlignment(.center)
                }.foregroundStyle(.secondary).padding(6)
            }
            else { ProgressView().controlSize(.small) }
        }
        .task(id: colorScheme) {
            request?.cancel()
            image = nil; failed = false
            let options = MKMapSnapshotter.Options()
            options.region = MKCoordinateRegion(center: region.center,
                span: MKCoordinateSpan(latitudeDelta: min(region.span.latitudeDelta, 0.02),
                                       longitudeDelta: min(region.span.longitudeDelta, 0.02)))
            options.size = CGSize(width: 180, height: 180)
            switch style {
            case .explore: options.preferredConfiguration = MKStandardMapConfiguration()
            case .muted: options.preferredConfiguration = MKStandardMapConfiguration(emphasisStyle: .muted)
            case .satellite: options.preferredConfiguration = MKImageryMapConfiguration()
            case .hybrid: options.preferredConfiguration = MKHybridMapConfiguration()
            }
#if os(macOS)
            options.appearance = NSAppearance(named: colorScheme == .dark ? .darkAqua : .aqua)
#else
            options.traitCollection = UITraitCollection(userInterfaceStyle: colorScheme == .dark ? .dark : .light)
#endif
            let snapshotter = MKMapSnapshotter(options: options)
            request = snapshotter
            do {
                let snapshot = try await snapshotter.start()
                try Task.checkCancellation()
#if os(macOS)
                image = Image(nsImage: snapshot.image)
#else
                image = Image(uiImage: snapshot.image)
#endif
            } catch { if !Task.isCancelled { failed = true } }
        }
        .onDisappear { request?.cancel() }
    }
}
