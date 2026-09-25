import Foundation
import CoreLocation
import Photos

struct PhotoInformationField: Identifiable {
    enum Group: String, CaseIterable { case file, camera, capture, location }
    let id: String
    let value: String
    var numeric = false
    var group: Group = .file
}

enum PhotoInformationFacts {
    static func rows(asset: PHAsset?, metadata: CardPhotoMetadata?, details: PhotoTechnicalDetails?,
                     coordinate: CLLocationCoordinate2D?) -> [PhotoInformationField] {
        var rows: [PhotoInformationField] = []
        func add(_ key: String, _ value: String?, numeric: Bool = false, group: PhotoInformationField.Group = .file) {
            guard let value, !value.isEmpty else { return }
            rows.append(PhotoInformationField(id: key, value: value, numeric: numeric, group: group))
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
        add("photo.info.where", asset != nil ? String.localized("photo.info.photos.library") : nil)

        let width = metadata?.width ?? asset?.pixelWidth ?? 0
        let height = metadata?.height ?? asset?.pixelHeight ?? 0
        if width > 0 && height > 0 { add("photo.dimensions", "\(width) × \(height)", numeric: true) }
        if let x = details?.horizontalDPI, let y = details?.verticalDPI,
           x.isFinite, y.isFinite, x > 0, y > 0, x <= 100_000, y <= 100_000 {
            add("photo.info.resolution", "\(Int(x.rounded())) × \(Int(y.rounded()))", numeric: true)
        }
        add("photo.info.color.profile", details?.colorProfile)
        add("photo.info.device.make", details?.deviceMake, group: .camera)
        add("photo.info.device.model", details?.deviceModel, group: .camera)
        add("photo.info.lens.model", details?.lensModel, group: .camera)
        if let aperture = details?.apertureValue, aperture.isFinite, aperture > 0 {
            add("photo.info.aperture.value", decimal(aperture), numeric: true, group: .capture)
        }
        if let exposure = details?.exposureTime, exposure.isFinite, exposure > 0 {
            let reciprocal = 1 / exposure
            let value = exposure < 1 && reciprocal <= 1_000_000
                ? "1/\(Int(reciprocal.rounded())) s" : "\(decimal(exposure)) s"
            add("photo.detail.exposure.time", value, numeric: true, group: .capture)
        }
        if let code = details?.exposureProgram { add("photo.info.exposure.program", codedValue(code, prefix: "photo.info.exposure"), group: .capture) }
        if let focal = details?.focalLength, focal.isFinite, focal > 0 {
            add("photo.info.focal.length", "\(decimal(focal)) mm", numeric: true, group: .capture)
        }
        if let iso = details?.iso, iso > 0 { add("photo.info.iso.speed", String(iso), numeric: true, group: .capture) }
        if let flash = details?.flash {
            add("photo.info.flash", String.localized(flash & 1 == 1 ? "photo.info.yes" : "photo.info.no"), group: .capture)
            add("photo.info.red.eye", String.localized(flash & 0x40 != 0 ? "photo.info.yes" : "photo.info.no"), group: .capture)
        }
        if let fNumber = details?.fNumber, fNumber.isFinite, fNumber > 0 {
            add("photo.info.f.number", "ƒ/\(decimal(fNumber))", numeric: true, group: .capture)
        }
        if let code = details?.meteringMode { add("photo.info.metering.mode", codedValue(code, prefix: "photo.info.metering"), group: .capture) }
        if let code = details?.whiteBalance { add("photo.info.white.balance", codedValue(code, prefix: "photo.info.white.balance"), group: .capture) }
        add("photo.info.content.creator", details?.artist, group: .camera)

        var resolved = coordinate
        if resolved == nil, let latitude = metadata?.latitude, let longitude = metadata?.longitude {
            resolved = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        }
        if let resolved, CLLocationCoordinate2DIsValid(resolved) {
            add("photo.detail.latitude", degreesMinutesSeconds(resolved.latitude, positive: "N", negative: "S"), numeric: true, group: .location)
            add("photo.detail.longitude", degreesMinutesSeconds(resolved.longitude, positive: "E", negative: "W"), numeric: true, group: .location)
        }
        return rows
    }

    static func grouped(asset: PHAsset?, metadata: CardPhotoMetadata?, details: PhotoTechnicalDetails?,
                        coordinate: CLLocationCoordinate2D?) -> [(group: PhotoInformationField.Group, rows: [PhotoInformationField])] {
        let all = rows(asset: asset, metadata: metadata, details: details, coordinate: coordinate)
        return PhotoInformationField.Group.allCases.compactMap { group in
            let matched = all.filter { $0.group == group }
            return matched.isEmpty ? nil : (group, matched)
        }
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
