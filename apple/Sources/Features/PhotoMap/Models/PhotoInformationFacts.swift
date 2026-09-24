import Foundation
import CoreLocation
import Photos

struct PhotoInformationField: Identifiable {
    let id: String
    let value: String
    var numeric = false
}

enum PhotoInformationFacts {
    static func rows(asset: PHAsset?, metadata: CardPhotoMetadata?, details: PhotoTechnicalDetails?,
                     coordinate: CLLocationCoordinate2D?) -> [PhotoInformationField] {
        var rows: [PhotoInformationField] = []
        func add(_ key: String, _ value: String?, numeric: Bool = false) {
            guard let value, !value.isEmpty else { return }
            rows.append(PhotoInformationField(id: key, value: value, numeric: numeric))
        }

        add("photo.info.kind", details?.typeDescription)
        if let fileSize = metadata?.fileSize, fileSize > 0 {
            add("photo.file.size", ByteCountFormatter.string(fromByteCount: Int64(fileSize), countStyle: .file), numeric: true)
        }
        if let date = asset?.creationDate {
            add("photo.detail.creation.time", date.formatted(.dateTime.year().month().day().hour().minute()), numeric: true)
        } else {
            add("photo.detail.creation.time", details?.capturedAt, numeric: true)
        }
        if let date = asset?.modificationDate {
            add("photo.info.modified", date.formatted(.dateTime.year().month().day().hour().minute()), numeric: true)
        }
        if asset != nil { add("photo.info.where", String.localized("photo.info.photos.library")) }

        let width = metadata?.width ?? asset?.pixelWidth ?? 0
        let height = metadata?.height ?? asset?.pixelHeight ?? 0
        if width > 0 && height > 0 { add("photo.dimensions", "\(width) × \(height)", numeric: true) }
        if let x = details?.horizontalDPI, let y = details?.verticalDPI,
           x.isFinite, y.isFinite, x > 0, y > 0, x <= 100_000, y <= 100_000 {
            add("photo.info.resolution", "\(Int(x.rounded())) × \(Int(y.rounded()))", numeric: true)
        }
        add("photo.info.color.profile", details?.colorProfile)
        add("photo.info.device.make", details?.deviceMake)
        add("photo.info.device.model", details?.deviceModel)
        add("photo.info.lens.model", details?.lensModel)
        if let aperture = details?.apertureValue, aperture.isFinite, aperture > 0 {
            add("photo.info.aperture.value", decimal(aperture), numeric: true)
        }
        if let exposure = details?.exposureTime, exposure.isFinite, exposure > 0 {
            let reciprocal = 1 / exposure
            let value = exposure < 1 && reciprocal <= 1_000_000
                ? "1/\(Int(reciprocal.rounded())) s" : "\(decimal(exposure)) s"
            add("photo.detail.exposure.time", value, numeric: true)
        }
        if let code = details?.exposureProgram { add("photo.info.exposure.program", codedValue(code, prefix: "photo.info.exposure")) }
        if let focal = details?.focalLength, focal.isFinite, focal > 0 {
            add("photo.info.focal.length", "\(decimal(focal)) mm", numeric: true)
        }
        if let iso = details?.iso, iso > 0 { add("photo.info.iso.speed", String(iso), numeric: true) }
        if let flash = details?.flash {
            add("photo.info.flash", String.localized(flash & 1 == 1 ? "photo.info.yes" : "photo.info.no"))
            add("photo.info.red.eye", String.localized(flash & 0x40 != 0 ? "photo.info.yes" : "photo.info.no"))
        }
        if let fNumber = details?.fNumber, fNumber.isFinite, fNumber > 0 {
            add("photo.info.f.number", "ƒ/\(decimal(fNumber))", numeric: true)
        }
        if let code = details?.meteringMode { add("photo.info.metering.mode", codedValue(code, prefix: "photo.info.metering")) }
        if let code = details?.whiteBalance { add("photo.info.white.balance", codedValue(code, prefix: "photo.info.white.balance")) }
        add("photo.info.content.creator", details?.artist)

        var resolved = coordinate
        if resolved == nil, let latitude = metadata?.latitude, let longitude = metadata?.longitude {
            resolved = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        }
        if let resolved, CLLocationCoordinate2DIsValid(resolved) {
            add("photo.detail.latitude", degreesMinutesSeconds(resolved.latitude, positive: "N", negative: "S"), numeric: true)
            add("photo.detail.longitude", degreesMinutesSeconds(resolved.longitude, positive: "E", negative: "W"), numeric: true)
        }
        return rows
    }

    private static func decimal(_ value: Double) -> String {
        value.formatted(.number.precision(.fractionLength(0...4)))
    }

    private static func codedValue(_ value: Int, prefix: String) -> String {
        let key = "\(prefix).\(value)"
        let localized = String.localized(key)
        return localized == key ? String(value) : localized
    }

    private static func degreesMinutesSeconds(_ value: Double, positive: String, negative: String) -> String {
        let total = (abs(value) * 3_600_000).rounded() / 1_000
        let degrees = Int(total / 3_600)
        let minutes = Int((total - Double(degrees * 3_600)) / 60)
        let seconds = total - Double(degrees * 3_600 + minutes * 60)
        let hemisphere = value < 0 ? negative : positive
        return "\(degrees)° \(minutes)′ \(seconds.formatted(.number.precision(.fractionLength(3))))″ \(hemisphere)"
    }
}
