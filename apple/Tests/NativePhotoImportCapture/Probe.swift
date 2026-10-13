import AppKit
import SwiftUI
import Foundation
import Metal

private struct CaptureProbeCard: View {
    let measurement: PhotoImportNativeSnapshotter.Measurement
    let space: UUID

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Color(red: 1, green: 0, blue: 0).frame(width: 12, height: 12)
                Spacer()
                Color(red: 0, green: 1, blue: 0).frame(width: 12, height: 12)
            }
            Label("照片元数据", systemImage: "info.circle")
                .font(.title2.bold())
            Text("查看和编辑照片中实际存在的信息。Native Form capture probe.")
                .font(.subheadline).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Button {} label: {
                Label("选择照片", systemImage: "photo.badge.plus").frame(maxWidth: .infinity)
            }.buttonStyle(.borderedProminent)
            Button {} label: {
                Label("从文件导入", systemImage: "folder").frame(maxWidth: .infinity)
            }.buttonStyle(.bordered)
            HStack {
                Color(red: 0, green: 0, blue: 1).frame(width: 12, height: 12)
                Spacer()
                Color(red: 1, green: 1, blue: 0).frame(width: 12, height: 12)
            }
        }
        .padding(.vertical, 8)
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(space)) } action: {
            measurement.frame = $0
        }
    }
}

private struct ProbeForm: View {
    let showsCard: Bool
    let measurement: PhotoImportNativeSnapshotter.Measurement
    let space: UUID

    var body: some View {
        Form {
            if showsCard {
                Section { CaptureProbeCard(measurement: measurement, space: space) }
            }
        }
        .formStyle(.grouped)
        .controlSize(.regular)
        .tint(.accentColor)
        .scrollEdgeEffectStyle(.soft, for: .top)
        .frame(maxWidth: 960)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .coordinateSpace(name: space)
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

@MainActor private func inspect(_ snapshot: PhotoImportNativeSnapshotter.Snapshot) throws -> String {
    let image = snapshot.image
    let width = image.width, height = image.height
    var bytes = [UInt8](repeating: 0, count: width * height * 4)
    guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else { throw ProbeFailure.check("missing sRGB") }
    try bytes.withUnsafeMutableBytes { buffer in
        guard let context = CGContext(data: buffer.baseAddress, width: width, height: height,
            bitsPerComponent: 8, bytesPerRow: width * 4, space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { throw ProbeFailure.check("bitmap context") }
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
    }
    var red = ColorArea(), green = ColorArea(), blue = ColorArea(), yellow = ColorArea()
    var opaque = 0, darkPixels = 0, lightPixels = 0
    for y in 0..<height {
        for x in 0..<width {
            let index = (y * width + x) * 4
            let r = bytes[index], g = bytes[index + 1], b = bytes[index + 2], a = bytes[index + 3]
            if a > 200 { opaque += 1 }
            guard a > 200 else { continue }
            if r > 210, g < 50, b < 50 { red.add(x, y) }
            if r < 50, g > 210, b < 50 { green.add(x, y) }
            if r < 50, g < 50, b > 210 { blue.add(x, y) }
            if r > 210, g > 210, b < 50 { yellow.add(x, y) }
            if r < 70, g < 70, b < 70 { darkPixels += 1 }
            if r > 200, g > 200, b > 200 { lightPixels += 1 }
        }
    }
    try expect(width > 24 && height > 24, "empty/tiny crop")
    try expect(Double(opaque) / Double(width * height) > 0.80, "transparent target")
    try expect(red.count >= 16 && green.count >= 16 && blue.count >= 16 && yellow.count >= 16,
               "one or more content corners missing: \(red.count),\(green.count),\(blue.count),\(yellow.count)")
    try expect(red.center.x < green.center.x && blue.center.x < yellow.center.x, "horizontal orientation")
    try expect(red.center.y < blue.center.y && green.center.y < yellow.center.y, "vertical orientation")
    let margin: CGFloat = 1
    try expect(snapshot.frame.insetBy(dx: -margin, dy: -margin).contains(snapshot.contentFrame), "crop omits measured body/buttons")
    try expect(darkPixels > 20 && lightPixels > 20, "missing foreground/background contrast")
    return "pixels=\(width)x\(height), opaque=\(opaque), corners=\(red.count)/\(green.count)/\(blue.count)/\(yellow.count), content=\(snapshot.contentFrame), crop=\(snapshot.frame)"
}

@main struct PhotoImportCaptureProbe {
    @MainActor static func main() async {
        guard MTLCreateSystemDefaultDevice() != nil else {
            print("SKIP Native Form capture: Metal is unavailable.")
            return
        }
        NSApplication.shared.setActivationPolicy(.prohibited)
        let cases: [(String, CGSize, ColorScheme, DynamicTypeSize)] = [
            ("light-phone-cold", CGSize(width: 390, height: 720), .light, .large),
            ("dark-phone", CGSize(width: 390, height: 720), .dark, .large),
            ("light-wide", CGSize(width: 1280, height: 820), .light, .large),
            ("dark-narrow-large-type", CGSize(width: 320, height: 620), .dark, .accessibility1)
        ]
        var failures = 0
        for (name, size, scheme, type) in cases {
            let measurement = PhotoImportNativeSnapshotter.Measurement()
            let context = PhotoImportNativeContext()
            let space = UUID()
            let content = ProbeForm(showsCard: true, measurement: measurement, space: space)
                .environment(\.dynamicTypeSize, type).environment(\.locale, Locale(identifier: "zh_CN"))
            let background = ProbeForm(showsCard: false, measurement: measurement, space: space)
                .environment(\.dynamicTypeSize, type).environment(\.locale, Locale(identifier: "zh_CN"))
            let start = ContinuousClock.now
            do {
                let snapshot = try await PhotoImportNativeSnapshotter.capture(content: content, background: background,
                    pointSize: size, measurement: measurement, colorScheme: scheme, context: context,
                    deadline: start.advanced(by: .seconds(2)))
                let elapsed = start.duration(to: .now)
                let report = try inspect(snapshot)
                print("PASS \(name): elapsed=\(elapsed), \(report)")
            } catch {
                failures += 1
                print("FAIL \(name): elapsed=\(start.duration(to: .now)), error=\(error), measured=\(measurement.frame)")
            }
        }
        do {
            let measurement = PhotoImportNativeSnapshotter.Measurement()
            let space = UUID()
            let content = ProbeForm(showsCard: true, measurement: measurement, space: space)
            let background = ProbeForm(showsCard: false, measurement: measurement, space: space)
            do {
                _ = try await PhotoImportNativeSnapshotter.capture(content: content, background: background,
                    pointSize: CGSize(width: 390, height: 720), measurement: measurement,
                    colorScheme: .light, context: PhotoImportNativeContext(), deadline: .now.advanced(by: .milliseconds(-1)))
                throw ProbeFailure.check("expired capture unexpectedly succeeded")
            } catch PhotoImportNativeSnapshotter.Failure.expired {
                print("PASS already-expired request")
            }
        } catch {
            failures += 1
            print("FAIL already-expired request: \(error)")
        }
        do {
            let measurement = PhotoImportNativeSnapshotter.Measurement()
            let space = UUID()
            let content = ProbeForm(showsCard: true, measurement: measurement, space: space)
            let background = ProbeForm(showsCard: false, measurement: measurement, space: space)
            let operation = Task { @MainActor in
                try await PhotoImportNativeSnapshotter.capture(content: content, background: background,
                    pointSize: CGSize(width: 390, height: 720), measurement: measurement,
                    colorScheme: .light, context: PhotoImportNativeContext(), deadline: .now.advanced(by: .seconds(2)))
            }
            operation.cancel()
            do {
                _ = try await operation.value
                throw ProbeFailure.check("cancelled capture unexpectedly succeeded")
            } catch is CancellationError {
                print("PASS cancelled request")
            }
        } catch {
            failures += 1
            print("FAIL cancelled request: \(error)")
        }
        print("RESULT failures=\(failures)/\(cases.count + 2)")
        exit(failures == 0 ? 0 : 1)
    }
}
