import SwiftUI
import MapKit

struct MapStyleChoiceGrid: View {
    @Binding var selection: MapStyleMode
    let appearance: MapAppearance
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: dynamicTypeSize.isAccessibilitySize ? 130 : 76), alignment: .top)], spacing: 12) {
            ForEach(MapStyleMode.allCases) { style in
                Button { selection = style } label: {
                    MapModeChoice(title: style.localizedName, selected: selection == style) {
                        MapStylePreview(style: style)
                            .environment(\.colorScheme, appearance.colorScheme ?? colorScheme)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selection == style ? .isSelected : [])
            }
        }
    }
}

struct MapDisplayModeChoiceGrid: View {
    @Binding var selection: MapDisplayMode
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: dynamicTypeSize.isAccessibilitySize ? 130 : 90), alignment: .top)], spacing: 12) {
            ForEach(MapDisplayMode.allCases) { mode in
                Button { selection = mode } label: {
                    MapModeChoice(title: mode.localizedName, selected: selection == mode) {
                        Image("MapPreview-" + mode.id).resizable().scaledToFit()
                    }
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selection == mode ? .isSelected : [])
            }
        }
    }
}

private struct MapModeChoice<Preview: View>: View {
    let title: LocalizedStringKey
    let selected: Bool
    @ViewBuilder var preview: () -> Preview

    var body: some View {
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
            options.region = Self.appleParkRegion
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

    private static var appleParkRegion: MKCoordinateRegion {
        // Apple Maps place 559098170073364042: 37.334859, -122.0090403.
        // A southwest offset places the ring toward the upper right, like the Maps icon.
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.3336, longitude: -122.0117),
            latitudinalMeters: 1_000,
            longitudinalMeters: 1_000
        )
    }
}
