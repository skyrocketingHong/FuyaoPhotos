import SwiftUI
import MapKit
import PhotoMapCore
import ImageIO
import UniformTypeIdentifiers

@MainActor enum MapExportRenderer {
    static let logicalSide = 512.0

    static func render(
        snapshot: MapExportSnapshot,
        settings: MapExportSettings,
        colorScheme: ColorScheme,
        thumbnail: @escaping @MainActor (String, Int) async throws -> CGImage?,
        progress: @escaping @MainActor (MapExportRenderStage) -> Void
    ) async throws -> MapExportRenderedImage {
        guard snapshot.totalCount > 0 else { throw MapExportError.empty }
        try Task.checkCancellation()
        let radius = min(120, max(32, settings.mapOptions.heatRadius))
        let extraInset: Double
        switch settings.displayMode {
        case .photo: extraInset = 76
        case .cluster: extraInset = 88
        case .heatmap: extraInset = max(76, radius + 48)
        }
        let frame = try MapExportFrame.fit(displayCoordinates: snapshot.displayCoordinates,
            outputSide: logicalSide, padding: 20, additionalInset: extraInset)
        progress(.map)
        let mapImage = try await mapSnapshot(frame: frame, settings: settings, colorScheme: colorScheme)
        try Task.checkCancellation()
        let projection = try MapExportProjection(image: mapImage, frame: frame)
        try projection.validate(snapshot.displayCoordinates, inset: 20)
        let index = PhotoSpatialIndex()
        try await index.replace(with: snapshot.displayCoordinates)
        let query = try await index.query(viewport: frame.queryViewport,
            width: frame.mapSide, height: frame.mapSide, year: nil,
            cellSize: settings.displayMode == .heatmap ? 24 : 64,
            limit: settings.displayMode == .heatmap ? 1_024 : 160)
        guard query.totalCount == snapshot.totalCount else { throw MapExportError.unsupportedExtent }
        let pixels = settings.resolution.rawValue
        let canvas = try MapExportCanvas(pixels: pixels, colorScheme: colorScheme)
        canvas.draw(mapImage.cgImage, in: projection.destination)
        switch settings.displayMode {
        case .photo:
            for (offset, cluster) in query.clusters.enumerated() {
                try Task.checkCancellation()
                progress(.markers(completed: offset, total: query.clusters.count))
                let image = try await thumbnail(cluster.representativeID,
                    Int(64 * Double(pixels) / logicalSide))
                try Task.checkCancellation()
                canvas.drawPhoto(image, cluster: cluster, at: projection.point(cluster))
            }
        case .cluster:
            for (offset, cluster) in query.clusters.enumerated() {
                try Task.checkCancellation()
                progress(.markers(completed: offset, total: query.clusters.count))
                try canvas.drawMarker(cluster, at: projection.point(cluster))
                if offset.isMultiple(of: 16) { await Task.yield() }
            }
        case .heatmap:
            try canvas.drawHeatmap(query.clusters, projection: projection,
                radius: radius, opacity: settings.mapOptions.heatOpacity)
        }
        progress(.compositing)
        try Task.checkCancellation()
        try canvas.drawHeader(snapshot: snapshot, projection: projection)
        canvas.drawAttribution()
        let image = try canvas.finish()
        let data = try await encode(image)
        try Task.checkCancellation()
        return MapExportRenderedImage(pngData: data, image: image)
    }

    /// MKMapSnapshotter renders requested spans above half the Mercator world at a silently
    /// smaller scale, so wider windows are stitched from faithful half-span tiles instead.
    private static func mapSnapshot(frame: MapExportFrame, settings: MapExportSettings,
                                    colorScheme: ColorScheme) async throws -> MapExportMapImage {
        let world = MKMapRect.world.width
        let divisions = frame.squareRect.side > 0.5 ? 2 : 1
        let tileSpan = frame.squareRect.side / Double(divisions)
        var mapOptions = settings.mapOptions
        mapOptions.realisticElevation = false
        let pixels = settings.resolution.rawValue
        let tilePixels = pixels / divisions
        let tilePoints: Double
#if os(macOS)
        // AppKit snapshots come back with a 2x bitmap; the point size requests pixel density.
        tilePoints = max(256, Double(tilePixels) / 2)
#else
        // MapKit rejects display scales beyond 3, so the point size carries the resolution.
        tilePoints = Double(tilePixels) / 2
#endif
        var pieces: [(rect: CGRect, cgImage: CGImage)] = []
        pieces.reserveCapacity(divisions * divisions)
        for row in 0..<divisions {
            for column in 0..<divisions {
                try Task.checkCancellation()
                let originX = frame.squareRect.x + Double(column) * tileSpan
                let originY = frame.squareRect.y + Double(row) * tileSpan
                let options = MKMapSnapshotter.Options()
                options.preferredConfiguration = mapOptions.configuration()
                options.mapRect = MKMapRect(x: originX * world, y: originY * world,
                    width: tileSpan * world, height: tileSpan * world)
                options.size = CGSize(width: tilePoints, height: tilePoints)
#if os(macOS)
                options.appearance = NSAppearance(named: colorScheme == .dark ? .darkAqua : .aqua)
#else
                options.traitCollection = UITraitCollection { traits in
                    traits.userInterfaceStyle = colorScheme == .dark ? .dark : .light
                    traits.displayScale = 2
                }
#endif
                let renderer = MKMapSnapshotter(options: options)
                let timeout = Task { @MainActor in
                    try await Task.sleep(for: .seconds(45))
                    renderer.cancel()
                }
                defer { timeout.cancel() }
                let snapshot: MKMapSnapshotter.Snapshot
                do {
                    snapshot = try await withTaskCancellationHandler {
                        try Task.checkCancellation()
                        return try await renderer.start()
                    } onCancel: {
                        Task { @MainActor in renderer.cancel() }
                    }
                } catch {
                    try Task.checkCancellation()
                    throw MapExportError.mapUnavailable
                }
                try verifyCenterMapping(of: snapshot, originX: originX, originY: originY,
                    tileSpan: tileSpan, tilePoints: tilePoints)
                guard let cgImage = snapshot.exportCGImage else { throw MapExportError.imageFailed }
                pieces.append((CGRect(x: CGFloat(column * tilePixels), y: CGFloat(row * tilePixels),
                    width: CGFloat(tilePixels), height: CGFloat(tilePixels)), cgImage))
            }
        }
        return try MapExportMapImage(stitching: pieces, pixels: pixels,
            originX: frame.squareRect.x, originY: frame.squareRect.y, span: frame.squareRect.side)
    }

    /// Guards against MapKit honoring a tile differently than its requested rect:
    /// the tile's own point(for:) must agree with the requested center within a pixel.
    private static func verifyCenterMapping(of snapshot: MKMapSnapshotter.Snapshot, originX: Double,
                                            originY: Double, tileSpan: Double, tilePoints: Double) throws {
        let latitude = mercatorLatitude(y: originY + tileSpan / 2)
        let longitude = (originX + tileSpan / 2) * 360 - 180
        let point = snapshot.point(for: CLLocationCoordinate2D(latitude: latitude, longitude: longitude))
        let expected = tilePoints / 2
#if os(macOS)
        let y = snapshot.image.size.height - point.y
#else
        let y = point.y
#endif
        guard abs(point.x - expected) <= 1.5, abs(y - expected) <= 1.5 else {
            throw MapExportError.unsupportedExtent
        }
    }

    static func mercatorLatitude(y fraction: Double) -> Double {
        atan(sinh(.pi * (1 - 2 * fraction))) * 180 / .pi
    }

    @concurrent private static func encode(_ image: CGImage) async throws -> Data {
        try Task.checkCancellation()
        let data = NSMutableData()
        guard let writer = CGImageDestinationCreateWithData(data, UTType.png.identifier as CFString, 1, nil)
        else { throw MapExportError.imageFailed }
        let properties: [CFString: Any] = [
            kCGImagePropertyPNGDictionary: [
                kCGImagePropertyPNGDescription: "Apple Maps. https://www.apple.com/legal/internet-services/maps/terms-en.html",
                kCGImagePropertyPNGSoftware: "Fuyao Photos"
            ]
        ]
        CGImageDestinationAddImage(writer, image, properties as CFDictionary)
        guard CGImageDestinationFinalize(writer) else { throw MapExportError.imageFailed }
        return data as Data
    }
}

extension MKMapSnapshotter.Snapshot {
    var exportCGImage: CGImage? {
#if os(macOS)
        image.cgImage(forProposedRect: nil, context: nil, hints: nil)
#else
        image.cgImage
#endif
    }
}

/// The stitched map window: an exact linear slice of the Mercator plane.
@MainActor struct MapExportMapImage {
    let cgImage: CGImage
    let originX: Double
    let originY: Double
    let span: Double

    init(stitching pieces: [(rect: CGRect, cgImage: CGImage)], pixels: Int,
         originX: Double, originY: Double, span: Double) throws {
        guard pieces.allSatisfy({ $0.cgImage.width > 0 && $0.cgImage.height > 0 }),
              let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(data: nil, width: pixels, height: pixels, bitsPerComponent: 8,
                bytesPerRow: 0, space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { throw MapExportError.imageFailed }
        context.interpolationQuality = .none
        // Match MapExportCanvas.draw's flipped-context placement so tiles and the final
        // canvas share one orientation: row 0 is the northern half of the window.
        context.translateBy(x: 0, y: CGFloat(pixels))
        context.scaleBy(x: 1, y: -1)
        for piece in pieces {
            guard let scaled = piece.cgImage.scaled(toFill: piece.rect.size) else {
                throw MapExportError.imageFailed
            }
            context.draw(scaled, in: piece.rect)
        }
        guard let composite = context.makeImage() else { throw MapExportError.imageFailed }
        self.cgImage = composite
        self.originX = originX
        self.originY = originY
        self.span = span
    }
}

extension CGImage {
    /// Tiles carry their own pixel density; normalize them to the export resolution before
    /// stitching so seam columns stay aligned without subpixel resampling.
    func scaled(toFill size: CGSize) -> CGImage? {
        guard width != Int(size.width.rounded()) || height != Int(size.height.rounded()) else { return self }
        guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(data: nil, width: Int(size.width.rounded()),
                height: Int(size.height.rounded()), bitsPerComponent: 8, bytesPerRow: 0,
                space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { return nil }
        context.interpolationQuality = .high
        context.draw(self, in: CGRect(origin: .zero, size: size))
        return context.makeImage()
    }
}

@MainActor struct MapExportProjection {
    let destination: CGRect
    let mapPointsPerPoint: Double
    let centerMapPoint: MKMapPoint
    private let image: MapExportMapImage

    init(image: MapExportMapImage, frame: MapExportFrame) throws {
        guard image.span.isFinite, image.span > 0, frame.mapSide > 0 else {
            throw MapExportError.unsupportedExtent
        }
        self.image = image
        destination = CGRect(x: frame.outerPadding, y: frame.outerPadding,
            width: frame.mapSide, height: frame.mapSide)
        let world = MKMapRect.world.width
        mapPointsPerPoint = world * image.span / frame.mapSide
        let x = (frame.squareRect.midX - floor(frame.squareRect.midX)) * world
        centerMapPoint = MKMapPoint(x: x, y: frame.squareRect.midY * world)
    }

    var centerOnCanvas: CGPoint {
        CGPoint(x: destination.midX, y: destination.midY)
    }

    func point(_ cluster: MapCluster) -> CGPoint {
        point(latitude: cluster.latitude, longitude: cluster.longitude)
    }

    func point(latitude: Double, longitude: Double) -> CGPoint {
        let projected = Self.mercator(latitude: latitude, longitude: longitude)
        var x = projected.x
        x += (image.originX + image.span / 2 - x).rounded()
        return CGPoint(x: destination.minX + (x - image.originX) / image.span * destination.width,
            y: destination.minY + (projected.y - image.originY) / image.span * destination.height)
    }

    func validate(_ coordinates: [PhotoCoordinate], inset: CGFloat) throws {
        let bounds = CGRect(x: inset, y: inset, width: 512 - inset * 2, height: 512 - inset * 2)
        for (offset, coordinate) in coordinates.enumerated() {
            if offset.isMultiple(of: 256) { try Task.checkCancellation() }
            let point = point(latitude: coordinate.latitude, longitude: coordinate.longitude)
            guard point.x.isFinite, point.y.isFinite,
                  point.x >= bounds.minX - 0.5, point.x <= bounds.maxX + 0.5,
                  point.y >= bounds.minY - 0.5, point.y <= bounds.maxY + 0.5 else {
                throw MapExportError.unsupportedExtent
            }
        }
    }

    private static func mercator(latitude: Double, longitude: Double) -> (x: Double, y: Double) {
        let x = min(1.nextDown, max(0, (longitude + 180) / 360))
        let radians = min(85.05112878, max(-85.05112878, latitude)) * .pi / 180
        let y = min(1.nextDown, max(0, (1 - log(tan(.pi / 4 + radians / 2)) / .pi) / 2))
        return (x, y)
    }
}
