import AppKit
import SwiftUI
import Foundation
import Metal

private enum WorkspaceCase: String, CaseIterable {
    case cards, metadata, colors
    var title: String {
        switch self { case .cards: "tab.cards"; case .metadata: "metadata.intro.title"; case .colors: "tab.colors" }
    }
    var description: String {
        switch self { case .cards: "card.empty.description"; case .metadata: "metadata.intro.description"; case .colors: "colors.description" }
    }
    var symbol: String {
        switch self { case .cards: "photo.badge.plus"; case .metadata: "info.circle"; case .colors: "eyedropper.halffull" }
    }
}

/// Uses the production Form and action builders; corner markers only diagnose crop and orientation.
private struct ProbeForm: View {
    let workspace: WorkspaceCase
    let measurement: PhotoImportNativeSnapshotter.Measurement
    let space: UUID
    let width: CGFloat
    let strings: [String: String]
    @State private var contentFrame = CGRect.zero

    private func text(_ key: String) -> LocalizedStringKey { LocalizedStringKey(strings[key] ?? key) }

    var body: some View {
        PhotoImportForm(title: text(workspace.title), description: text(workspace.description),
            symbol: workspace.symbol, busy: false, availableWidth: width,
            measurementSpace: space, measured: {
                measurement.frame = $0
                contentFrame = $0
            }) {
                PhotoImportAction(title: text("card.open"), symbol: "photo.badge.plus", prominence: .primary) {}
                if workspace == .cards {
                    PhotoImportAction(title: text("package.import.action"), symbol: "square.and.arrow.down") {}
                }
                if workspace == .colors {
                    PhotoImportAction(title: text("colors.files"), symbol: "folder") {}
                    PhotoImportAction(title: text("colors.camera"), symbol: "camera", prominence: .secondary) {}
                }
            }
            .coordinateSpace(name: space)
            .overlay(alignment: .topLeading) {
                if !contentFrame.isEmpty {
                    ZStack(alignment: .topLeading) {
                        Color(red: 1, green: 0, blue: 0).frame(width: 12, height: 12)
                            .position(x: contentFrame.minX + 6, y: contentFrame.minY + 6)
                        Color(red: 0, green: 1, blue: 0).frame(width: 12, height: 12)
                            .position(x: contentFrame.maxX - 6, y: contentFrame.minY + 6)
                        Color(red: 0, green: 0, blue: 1).frame(width: 12, height: 12)
                            .position(x: contentFrame.minX + 6, y: contentFrame.maxY - 6)
                        Color(red: 1, green: 1, blue: 0).frame(width: 12, height: 12)
                            .position(x: contentFrame.maxX - 6, y: contentFrame.maxY - 6)
                    }
                }
            }
    }
}

private enum ProbeFailure: Error, CustomStringConvertible {
    case check(String)
    var description: String { switch self { case .check(let text): text } }
}

@MainActor private func expect(_ condition: @autoclosure () -> Bool, _ message: String) throws {
    if !condition() { throw ProbeFailure.check(message) }
}

private struct ColorArea {
    var count = 0
    var x = 0.0
    var y = 0.0
    mutating func add(_ px: Int, _ py: Int) { count += 1; x += Double(px); y += Double(py) }
    var center: CGPoint { CGPoint(x: x / Double(max(1, count)), y: y / Double(max(1, count))) }
}

@MainActor private func pixels(_ image: CGImage) throws -> [UInt8] {
    var bytes = [UInt8](repeating: 0, count: image.width * image.height * 4)
    guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else { throw ProbeFailure.check("missing sRGB") }
    try bytes.withUnsafeMutableBytes { buffer in
        guard let context = CGContext(data: buffer.baseAddress, width: image.width, height: image.height,
            bitsPerComponent: 8, bytesPerRow: image.width * 4, space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { throw ProbeFailure.check("bitmap context") }
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
    }
    return bytes
}

@MainActor private func inspect(_ snapshot: PhotoImportNativeSnapshotter.Snapshot,
                                size: CGSize, displayScale: CGFloat) throws -> String {
    let image = snapshot.image
    let width = image.width, height = image.height
    let bytes = try pixels(image)
    var red = ColorArea(), green = ColorArea(), blue = ColorArea(), yellow = ColorArea()
    var opaque = 0
    for y in 0..<height {
        for x in 0..<width {
            let index = (y * width + x) * 4
            let r = bytes[index], g = bytes[index + 1], b = bytes[index + 2], a = bytes[index + 3]
            guard a > 200 else { continue }
            opaque += 1
            if r > 210, g < 50, b < 50 { red.add(x, y) }
            if r < 50, g > 210, b < 50 { green.add(x, y) }
            if r < 50, g < 50, b > 210 { blue.add(x, y) }
            if r > 210, g > 210, b < 50 { yellow.add(x, y) }
        }
    }
    try expect(width > 24 && height > 24, "empty/tiny crop")
    try expect(Double(opaque) / Double(width * height) > 0.80, "transparent target")
    try expect(red.count >= 16 && green.count >= 16 && blue.count >= 16 && yellow.count >= 16,
               "content corners missing: \(red.count),\(green.count),\(blue.count),\(yellow.count)")
    try expect(red.center.x < green.center.x && blue.center.x < yellow.center.x, "horizontal orientation")
    try expect(red.center.y < blue.center.y && green.center.y < yellow.center.y, "vertical orientation")
    try expect(snapshot.frame.insetBy(dx: -1, dy: -1).contains(snapshot.contentFrame), "crop omits body/buttons")
    let expectedScale = min(displayScale, sqrt(Double(PhotoMotionTokens.snapshotPixelLimit) / (size.width * size.height)))
    try expect(abs(Double(width) / snapshot.frame.width - expectedScale) < 0.01, "requested display scale lost")
    return "pixels=\(width)x\(height), scale=\(expectedScale), opaque=\(opaque), corners=\(red.count)/\(green.count)/\(blue.count)/\(yellow.count), content=\(snapshot.contentFrame), crop=\(snapshot.frame)"
}

@MainActor private func assertAppearanceEqual(_ first: PhotoImportNativeSnapshotter.Snapshot,
                                              _ second: PhotoImportNativeSnapshotter.Snapshot) throws {
    try expect(first.image.width == second.image.width && first.image.height == second.image.height, "hidden target changed size")
    let a = try pixels(first.image), b = try pixels(second.image)
    let differences = zip(a, b).reduce(0) { $0 + (abs(Int($1.0) - Int($1.1)) > 4 ? 1 : 0) }
    try expect(Double(differences) / Double(a.count) < 0.003, "hidden target changed native control appearance")
}

private struct ProbeEnvironment {
    let name: String
    let size: CGSize
    let scheme: ColorScheme
    let type: DynamicTypeSize
    let locale: String
    let scale: CGFloat
    let active: ControlActiveState

    @MainActor func values(visible: Bool = false) -> EnvironmentValues {
        var values = EnvironmentValues()
        values.colorScheme = scheme
        values.dynamicTypeSize = type
        values.locale = Locale(identifier: locale)
        values.displayScale = scale
        values.controlActiveState = active
        values.photoImportContentVisible = visible
        return values
    }
}

@main struct PhotoImportCaptureProbe {
    @MainActor private static func capture(_ workspace: WorkspaceCase, environment: ProbeEnvironment,
                                           strings: [String: String], deadline: ContinuousClock.Instant,
                                           visible: Bool = false) async throws -> PhotoImportNativeSnapshotter.Snapshot {
        let measurement = PhotoImportNativeSnapshotter.Measurement()
        let space = UUID()
        let values = environment.values(visible: visible)
        let appearance = PhotoImportSnapshotAppearance(appearance: PhotoImportAppearance(environment: values),
            contentVisible: visible)
        let content = ProbeForm(workspace: workspace, measurement: measurement, space: space,
            width: environment.size.width, strings: strings).modifier(appearance)
        let background = PhotoImportFormBackground().modifier(appearance)
        return try await PhotoImportNativeSnapshotter.capture(content: content, background: background,
            pointSize: environment.size, measurement: measurement, colorScheme: environment.scheme,
            displayScale: environment.scale, context: PhotoImportNativeContext(), deadline: deadline)
    }

    @MainActor static func main() async {
        guard MTLCreateSystemDefaultDevice() != nil else {
            print("SKIP Native Form capture: Metal is unavailable.")
            return
        }
        NSApplication.shared.setActivationPolicy(.prohibited)
        let arguments = Array(CommandLine.arguments.dropFirst())
        guard let resourcePath = arguments.first else {
            print("FAIL missing Sources/Resources path"); exit(2)
        }
        var deadlineMillis = 2000
        if let index = arguments.firstIndex(of: "--capture-deadline-ms") {
            guard arguments.indices.contains(index + 1), let value = Int(arguments[index + 1]), value > 0 else {
                print("FAIL invalid --capture-deadline-ms"); exit(2)
            }
            deadlineMillis = value
        }
        let environments = [
            ProbeEnvironment(name: "light-phone", size: CGSize(width: 390, height: 720), scheme: .light,
                type: .large, locale: "en_US", scale: 3, active: .key),
            ProbeEnvironment(name: "dark-phone", size: CGSize(width: 390, height: 720), scheme: .dark,
                type: .large, locale: "zh_CN", scale: 3, active: .active),
            ProbeEnvironment(name: "light-wide", size: CGSize(width: 1280, height: 820), scheme: .light,
                type: .large, locale: "en_US", scale: 2, active: .key),
            ProbeEnvironment(name: "dark-narrow-large-type", size: CGSize(width: 320, height: 620), scheme: .dark,
                type: .accessibility1, locale: "zh_CN", scale: 3, active: .inactive)
        ]
        var stringsByLocale: [String: [String: String]] = [:]
        do {
            for (locale, folder) in [("en_US", "en.lproj"), ("zh_CN", "zh-Hans.lproj")] {
                let path = URL(fileURLWithPath: resourcePath).appendingPathComponent(folder).appendingPathComponent("Localizable.strings")
                let raw = try PropertyListSerialization.propertyList(from: Data(contentsOf: path), format: nil)
                guard let strings = raw as? [String: String] else { throw ProbeFailure.check("invalid localization \(locale)") }
                stringsByLocale[locale] = strings
            }
        } catch {
            print("FAIL localizations: \(error)"); exit(2)
        }
        var failures = 0, checks = 0
        checks += 1
        do {
            let count = try PhotoImportDifferenceChecks.run()
            print("PASS native difference bounds: \(count) reference-equivalence fixtures")
        } catch {
            failures += 1; print("FAIL native difference bounds: \(error)")
        }
        var paritySnapshot: PhotoImportNativeSnapshotter.Snapshot?
        for workspace in WorkspaceCase.allCases {
            for environment in environments {
                checks += 1
                let start = ContinuousClock.now
                do {
                    let snapshot = try await capture(workspace, environment: environment,
                        strings: stringsByLocale[environment.locale] ?? [:],
                        deadline: start.advanced(by: .milliseconds(deadlineMillis)))
                    let elapsed = start.duration(to: .now)
                    let report = try inspect(snapshot, size: environment.size, displayScale: environment.scale)
                    print("PASS \(workspace.rawValue)/\(environment.name): elapsed=\(elapsed), \(report)")
                    if workspace == .colors, environment.name == "light-phone" { paritySnapshot = snapshot }
                } catch {
                    failures += 1
                    print("FAIL \(workspace.rawValue)/\(environment.name): elapsed=\(start.duration(to: .now)), error=\(error)")
                }
            }
        }
        let environment = environments[0]
        let strings = stringsByLocale[environment.locale] ?? [:]
        checks += 1
        do {
            guard let paritySnapshot else { throw ProbeFailure.check("missing hidden baseline") }
            let shown = try await capture(.colors, environment: environment, strings: strings,
                deadline: .now.advanced(by: .seconds(2)), visible: true)
            try assertAppearanceEqual(paritySnapshot, shown)
            print("PASS hidden/shown controls have the same native appearance")
        } catch {
            failures += 1; print("FAIL hidden/shown appearance: \(error)")
        }
        checks += 1
        do {
            var changed = environment.values()
            changed.controlActiveState = .inactive
            try expect(PhotoImportAppearance(environment: environment.values()) != PhotoImportAppearance(environment: changed),
                "controlActiveState missing from appearance stamp")
            changed = environment.values()
            changed.displayScale = 2
            try expect(PhotoImportAppearance(environment: environment.values()) != PhotoImportAppearance(environment: changed),
                "displayScale missing from appearance stamp")
            changed = environment.values()
            changed.locale = Locale(identifier: "zh_CN")
            try expect(PhotoImportAppearance(environment: environment.values()) != PhotoImportAppearance(environment: changed),
                "locale missing from appearance stamp")
            print("PASS appearance stamp tracks native control state, scale and locale")
        } catch {
            failures += 1; print("FAIL appearance stamp: \(error)")
        }
        checks += 1
        do {
            do {
                _ = try await capture(.metadata, environment: environment, strings: strings,
                    deadline: .now.advanced(by: .milliseconds(-1)))
                throw ProbeFailure.check("expired capture unexpectedly succeeded")
            } catch PhotoImportNativeSnapshotter.Failure.expired {
                print("PASS already-expired request")
            }
        } catch {
            failures += 1; print("FAIL already-expired request: \(error)")
        }
        checks += 1
        do {
            let operation = Task { @MainActor in
                try await capture(.metadata, environment: environment, strings: strings,
                    deadline: .now.advanced(by: .seconds(2)))
            }
            operation.cancel()
            do {
                _ = try await operation.value
                throw ProbeFailure.check("cancelled capture unexpectedly succeeded")
            } catch is CancellationError {
                print("PASS cancelled request")
            }
        } catch {
            failures += 1; print("FAIL cancelled request: \(error)")
        }
        print("RESULT failures=\(failures)/\(checks), capture-deadline-ms=\(deadlineMillis)")
        exit(failures == 0 ? 0 : 1)
    }
}
