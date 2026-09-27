import CoreGraphics
import SwiftUI

struct PortraitDepthLayerDisclosure: View {
    let layer: PortraitDepthLayerPixels
    @State private var expanded = false

    var body: some View {
        Group {
            if let image = layer.image {
                Button {
                    expanded.toggle()
                } label: {
                    HStack {
                        Label("photo.depth.layer", systemImage: "square.3.layers.3d")
                        Spacer()
                        Text(layer.isDisparity ? "photo.depth.disparity" : "photo.depth.depth")
                            .foregroundStyle(.secondary)
                        Image(systemName: expanded ? "chevron.up" : "chevron.down")
                            .font(.footnote.weight(.semibold))
                    }
                }
                .buttonStyle(.bordered)
                .frame(minHeight: 44)
                .listRowSeparator(.hidden)
                if expanded {
                    Image(decorative: image, scale: 1)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity, maxHeight: 320)
                        .background(.fill.tertiary, in: RoundedRectangle(cornerRadius: 8))
                        .accessibilityLabel(Text("photo.depth.layer"))
                        .listRowSeparator(.hidden)
                    Text("photo.depth.visualization")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .listRowSeparator(.hidden)
                }
            }
        }
    }
}

extension PortraitDepthLayerPixels {
    var image: CGImage? {
        guard let provider = CGDataProvider(data: bytes as CFData) else { return nil }
        return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 8,
                       bytesPerRow: width, space: CGColorSpaceCreateDeviceGray(),
                       bitmapInfo: CGBitmapInfo(rawValue: 0), provider: provider,
                       decode: nil, shouldInterpolate: true, intent: .defaultIntent)
    }
}
