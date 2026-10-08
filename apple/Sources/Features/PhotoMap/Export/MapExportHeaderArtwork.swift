import SwiftUI
import MapKit
import PhotoMapCore

@MainActor enum MapExportHeaderArtwork {
    static func render(background: CGImage, snapshot: MapExportSnapshot,
                       projection: MapExportProjection, scale: CGFloat,
                       colorScheme: ColorScheme, context: MapExportRenderingContext) async throws -> CGImage {
        let years: String
        if let minimum = snapshot.minYear, let maximum = snapshot.maxYear {
            years = minimum == maximum ? String(minimum) : "\(minimum)-\(maximum)"
        } else { years = String.localized("map.export.header.years.unknown") }
        let metersPerPoint = MKMetersPerMapPointAtLatitude(projection.centerMapPoint.coordinate.latitude)
            * projection.mapPointsPerPoint
        guard metersPerPoint.isFinite, metersPerPoint > 0 else { throw MapExportError.unsupportedExtent }
        let maximum = metersPerPoint * 88
        let power = pow(10, floor(log10(maximum)))
        let units = maximum / power
        let meters = (units >= 5 ? 5.0 : units >= 2 ? 2.0 : 1.0) * power
        let distance = Measurement(value: meters >= 1_000 ? meters / 1_000 : meters,
            unit: meters >= 1_000 ? UnitLength.kilometers : UnitLength.meters)
        let formatter = MeasurementFormatter()
        formatter.unitOptions = .providedUnit
        formatter.unitStyle = .short
        formatter.numberFormatter.maximumFractionDigits = meters < 1 ? 2 : 0
        let compass = try MapExportNativeArtwork.compass(colorScheme: colorScheme, scale: scale)
        let scene = ZStack(alignment: .topLeading) {
            Image(decorative: background, scale: 1, orientation: .up)
                .resizable().frame(width: 512, height: 512)
            MapExportHeader(count: snapshot.totalCount, years: years, compass: compass,
                scaleLabel: formatter.string(from: distance), barWidth: CGFloat(meters / metersPerPoint))
                .padding(20)
        }
        .frame(width: 512, height: 512)
        .environment(\.colorScheme, colorScheme)
        .environment(\.dynamicTypeSize, .large)
        .environment(\.displayScale, scale)
        .transaction { transaction in
            transaction.animation = nil
            transaction.disablesAnimations = true
        }
        return try await MapExportGlassRenderer.render(content: scene, pointSize: CGSize(width: 512, height: 512),
            scale: scale, colorScheme: colorScheme, context: context)
    }
}

private struct MapExportHeader: View {
    let count: Int
    let years: String
    let compass: CGImage
    let scaleLabel: String
    let barWidth: CGFloat

    var body: some View {
        GlassEffectContainer(spacing: 8) {
            HStack(spacing: 8) {
                MapPhotoCountBadge(count: count)
                    .fixedSize(horizontal: true, vertical: false)
                Text(verbatim: years)
                    .font(.footnote.weight(.medium)).monospacedDigit()
                    .lineLimit(1).minimumScaleFactor(0.7)
                    .padding(.horizontal, 11).padding(.vertical, 7)
                    .frame(maxWidth: 120)
                    .glassEffect(.regular, in: .capsule)
                    .fixedSize(horizontal: true, vertical: false)
                Spacer(minLength: 8)
                VStack(alignment: .center, spacing: 3) {
                    Text(verbatim: scaleLabel)
                        .font(.caption2.weight(.medium)).monospacedDigit()
                        .lineLimit(1)
                    HStack(spacing: 0) {
                        Rectangle().frame(width: 1.5, height: 7)
                        Rectangle().frame(width: barWidth, height: 1.5)
                        Rectangle().frame(width: 1.5, height: 7)
                    }
                }
                .foregroundStyle(.primary)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .glassEffect(.regular, in: .capsule)
                Image(decorative: compass, scale: 1, orientation: .up)
                    .resizable().scaledToFit().frame(width: 44, height: 44)
                    .glassEffect(.regular, in: .circle)
            }
            .frame(width: 472, height: 44)
        }
    }
}
