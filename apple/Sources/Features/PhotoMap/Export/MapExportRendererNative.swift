import SwiftUI
import MapKit

@MainActor enum MapExportNativeArtwork {
    static func marker(count: Int, colorScheme: ColorScheme, scale: CGFloat) throws
        -> (image: CGImage, size: CGSize) {
        let marker = MKMarkerAnnotationView(annotation: MKPointAnnotation(), reuseIdentifier: nil)
        marker.markerTintColor = .systemRed
        marker.glyphText = count.formatted()
        marker.titleVisibility = .hidden
        marker.subtitleVisibility = .hidden
        marker.animatesWhenAdded = false
        marker.prepareForDisplay()
        let image = try rasterize(marker, colorScheme: colorScheme, scale: scale)
        return (image, marker.bounds.size)
    }

    static func compass(colorScheme: ColorScheme, scale: CGFloat) throws -> CGImage {
        let map = MKMapView(frame: CGRect(x: 0, y: 0, width: 512, height: 512))
        map.camera.heading = 0
        map.camera.pitch = 0
        let compass = MKCompassButton(mapView: map)
        compass.compassVisibility = .visible
        compass.frame = CGRect(x: 0, y: 0, width: 44, height: 44)
        return try rasterize(compass, colorScheme: colorScheme, scale: scale)
    }

    static func photoSymbol(colorScheme: ColorScheme, scale: CGFloat) -> CGImage? {
#if os(macOS)
        let configuration = NSImage.SymbolConfiguration(pointSize: 28, weight: .regular)
        let image = NSImage(systemSymbolName: "photo", accessibilityDescription: nil)?
            .withSymbolConfiguration(configuration)
        let view = NSImageView(frame: CGRect(x: 0, y: 0, width: 28, height: 28))
        view.image = image
        view.contentTintColor = colorScheme == .dark ? .white : .darkGray
#else
        let configuration = UIImage.SymbolConfiguration(pointSize: 28, weight: .regular)
        let view = UIImageView(image: UIImage(systemName: "photo", withConfiguration: configuration))
        view.frame = CGRect(x: 0, y: 0, width: 28, height: 28)
        view.tintColor = colorScheme == .dark ? .white : .darkGray
#endif
        return try? rasterize(view, colorScheme: colorScheme, scale: scale)
    }

#if os(macOS)
    private static func rasterize(_ view: NSView, colorScheme: ColorScheme, scale: CGFloat) throws -> CGImage {
        view.appearance = NSAppearance(named: colorScheme == .dark ? .darkAqua : .aqua)
        view.layoutSubtreeIfNeeded()
        let size = view.bounds.size
        guard size.width > 0, size.height > 0,
              let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil,
                pixelsWide: Int(ceil(size.width * scale)), pixelsHigh: Int(ceil(size.height * scale)),
                bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)
        else { throw MapExportError.imageFailed }
        bitmap.size = size
        view.cacheDisplay(in: view.bounds, to: bitmap)
        guard let image = bitmap.cgImage else { throw MapExportError.imageFailed }
        return image
    }
#else
    private static func rasterize(_ view: UIView, colorScheme: ColorScheme, scale: CGFloat) throws -> CGImage {
        view.overrideUserInterfaceStyle = colorScheme == .dark ? .dark : .light
        view.setNeedsLayout()
        view.layoutIfNeeded()
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.preferredRange = .standard
        let result = UIGraphicsImageRenderer(size: view.bounds.size, format: format).image { renderer in
            view.layer.render(in: renderer.cgContext)
        }
        guard let image = result.cgImage else { throw MapExportError.imageFailed }
        return image
    }
#endif
}
