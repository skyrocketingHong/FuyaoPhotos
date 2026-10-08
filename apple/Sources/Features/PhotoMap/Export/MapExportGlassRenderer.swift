import SwiftUI
import Metal
import QuartzCore
#if os(macOS)
import AppKit
#else
import UIKit
#endif

@MainActor enum MapExportGlassRenderer {
    private struct PreparedScene {
        let layer: CALayer
        let removeCalibration: @MainActor () -> Void
        let cleanup: @MainActor () -> Void
    }

    static func render<Content: View>(
        content: Content,
        pointSize: CGSize,
        scale: CGFloat,
        colorScheme: ColorScheme,
        context: MapExportRenderingContext
    ) async throws -> CGImage {
        try Task.checkCancellation()
        guard pointSize.width.isFinite, pointSize.height.isFinite, scale.isFinite,
              pointSize.width >= 8, pointSize.height >= 8, scale >= 1,
              pointSize.width * scale <= 8192, pointSize.height * scale <= 8192 else {
            throw MapExportError.imageFailed
        }
        let width = Int((pointSize.width * scale).rounded())
        let height = Int((pointSize.height * scale).rounded())
        guard width * height <= 4096 * 4096,
              let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
              let device = MTLCreateSystemDefaultDevice(),
              let queue = device.makeCommandQueue() else { throw MapExportError.imageFailed }
        let scene = try await prepare(content: content, pointSize: pointSize,
                                      colorScheme: colorScheme, context: context)
        defer { scene.cleanup() }
        let root = CALayer()
        root.frame = CGRect(x: 0, y: 0, width: width, height: height)
        scene.layer.anchorPoint = .zero
        scene.layer.position = .zero
        scene.layer.transform = CATransform3DMakeScale(scale, scale, 1)
        root.addSublayer(scene.layer)
        defer { scene.layer.removeFromSuperlayer() }

        let descriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .bgra8Unorm, width: width, height: height, mipmapped: false
        )
        descriptor.storageMode = .shared
        descriptor.usage = [.renderTarget, .shaderRead, .shaderWrite]
        guard let texture = device.makeTexture(descriptor: descriptor) else { throw MapExportError.imageFailed }
        let renderer = CARenderer(mtlTexture: texture, options: [
            kCARendererMetalCommandQueue: queue,
            kCARendererColorSpace: colorSpace
        ])
        renderer.layer = root
        renderer.bounds = root.bounds
        defer { renderer.layer = nil }

        // The native view's origin may differ by platform; calibrate before exporting content.
        try await draw(renderer, queue: queue)
        let reversesRows = try needsRowReversal(texture, scale: scale)
        scene.removeCalibration()
        try await draw(renderer, queue: queue)
        return try readImage(texture, reversingRows: reversesRows, colorSpace: colorSpace)
    }

    private static func draw(_ renderer: CARenderer, queue: any MTLCommandQueue) async throws {
        try Task.checkCancellation()
        CATransaction.flush()
        renderer.beginFrame(atTime: CACurrentMediaTime(), timeStamp: nil)
        renderer.addUpdate(renderer.bounds)
        renderer.render()
        renderer.endFrame()
        guard let completion = queue.makeCommandBuffer() else { throw MapExportError.imageFailed }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            completion.addCompletedHandler { _ in continuation.resume() }
            completion.commit()
        }
        guard completion.status == .completed else { throw MapExportError.imageFailed }
        try Task.checkCancellation()
    }

    private static func needsRowReversal(_ texture: any MTLTexture, scale: CGFloat) throws -> Bool {
        let inset = max(1, Int(2 * scale))
        func isMagenta(row: Int) -> Bool {
            var pixel = [UInt8](repeating: 0, count: 4)
            pixel.withUnsafeMutableBytes { bytes in
                texture.getBytes(bytes.baseAddress!, bytesPerRow: 4,
                                 from: MTLRegionMake2D(inset, row, 1, 1), mipmapLevel: 0)
            }
            return pixel[0] > 200 && pixel[1] < 128 && pixel[2] > 200 && pixel[3] > 240
        }
        let first = isMagenta(row: inset)
        let last = isMagenta(row: texture.height - 1 - inset)
        guard first != last else { throw MapExportError.imageFailed }
        return last
    }

    private static func readImage(_ texture: any MTLTexture, reversingRows: Bool,
                                  colorSpace: CGColorSpace) throws -> CGImage {
        let width = texture.width, height = texture.height, rowBytes = texture.width * 4
        var data = Data(count: rowBytes * height)
        try data.withUnsafeMutableBytes { buffer in
            guard let address = buffer.baseAddress else { throw MapExportError.imageFailed }
            texture.getBytes(address, bytesPerRow: rowBytes,
                             from: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0)
            if reversingRows {
                let temporary = UnsafeMutableRawPointer.allocate(byteCount: rowBytes, alignment: 64)
                defer { temporary.deallocate() }
                for row in 0..<(height / 2) {
                    if row.isMultiple(of: 64) { try Task.checkCancellation() }
                    let first = address.advanced(by: row * rowBytes)
                    let last = address.advanced(by: (height - 1 - row) * rowBytes)
                    temporary.copyMemory(from: first, byteCount: rowBytes)
                    first.copyMemory(from: last, byteCount: rowBytes)
                    last.copyMemory(from: temporary, byteCount: rowBytes)
                }
            }
        }
        guard let provider = CGDataProvider(data: data as CFData),
              let image = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                                  bytesPerRow: rowBytes, space: colorSpace,
                                  bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedFirst.rawValue
                                                          | CGBitmapInfo.byteOrder32Little.rawValue),
                                  provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
        else { throw MapExportError.imageFailed }
        return image
    }

    private static func prepare<Content: View>(content: Content, pointSize: CGSize,
                                               colorScheme: ColorScheme,
                                               context: MapExportRenderingContext) async throws -> PreparedScene {
        let bounds = CGRect(origin: .zero, size: pointSize)
        let scene = content.frame(width: pointSize.width, height: pointSize.height)
            .environment(\.colorScheme, colorScheme)
            .ignoresSafeArea()
        #if os(macOS)
        let host = NSHostingView(rootView: scene)
        host.frame = bounds
        let container = NSView(frame: bounds)
        container.wantsLayer = true
        container.addSubview(host)
        let calibration = NSView(frame: bounds)
        calibration.wantsLayer = true
        calibration.layer?.backgroundColor = CGColor(gray: 0, alpha: 1)
        calibration.layer?.zPosition = 10_000
        let marker = NSView(frame: CGRect(x: 0, y: pointSize.height - 4, width: 4, height: 4))
        marker.wantsLayer = true
        marker.layer?.backgroundColor = CGColor(red: 1, green: 0, blue: 1, alpha: 1)
        calibration.addSubview(marker)
        container.addSubview(calibration)
        let window = NSWindow(contentRect: bounds, styleMask: .borderless, backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.appearance = NSAppearance(named: colorScheme == .dark ? .darkAqua : .aqua)
        window.contentView = container
        let cleanup: @MainActor () -> Void = {
            window.contentView = nil
            container.removeFromSuperview()
            host.removeFromSuperview()
            window.close()
        }
        var completed = false
        defer { if !completed { cleanup() } }
        host.layoutSubtreeIfNeeded()
        host.displayIfNeeded()
        CATransaction.flush()
        try await Task.sleep(for: .milliseconds(500))
        host.layoutSubtreeIfNeeded()
        host.displayIfNeeded()
        CATransaction.flush()
        window.contentView = nil
        guard let layer = container.layer else { throw MapExportError.imageFailed }
        completed = true
        return PreparedScene(layer: layer, removeCalibration: { calibration.removeFromSuperview() },
                             cleanup: cleanup)
        #else
        guard let windowScene = context.windowScene else { throw MapExportError.imageFailed }
        let host = UIHostingController(rootView: scene)
        host.safeAreaRegions = []
        host.loadViewIfNeeded()
        host.view.frame = bounds
        host.view.backgroundColor = .clear
        let owner = UIViewController()
        let container = UIView(frame: bounds)
        container.backgroundColor = .clear
        owner.view = container
        owner.addChild(host)
        container.addSubview(host.view)
        host.didMove(toParent: owner)
        let calibration = UIView(frame: bounds)
        calibration.backgroundColor = .black
        calibration.layer.zPosition = 10_000
        let marker = UIView(frame: CGRect(x: 0, y: 0, width: 4, height: 4))
        marker.backgroundColor = .magenta
        calibration.addSubview(marker)
        container.addSubview(calibration)
        let window = UIWindow(windowScene: windowScene)
        window.isHidden = true
        window.overrideUserInterfaceStyle = colorScheme == .dark ? .dark : .light
        window.frame = bounds
        window.rootViewController = owner
        container.frame = bounds
        let cleanup: @MainActor () -> Void = {
            window.rootViewController = nil
            container.removeFromSuperview()
            host.willMove(toParent: nil)
            host.view.removeFromSuperview()
            host.removeFromParent()
            window.windowScene = nil
        }
        var completed = false
        defer { if !completed { cleanup() } }
        container.setNeedsLayout()
        container.layoutIfNeeded()
        host.view.setNeedsDisplay()
        CATransaction.flush()
        try await Task.sleep(for: .milliseconds(500))
        container.layoutIfNeeded()
        host.view.layoutIfNeeded()
        host.view.layer.displayIfNeeded()
        CATransaction.flush()
        window.rootViewController = nil
        container.removeFromSuperview()
        completed = true
        return PreparedScene(layer: container.layer, removeCalibration: { calibration.removeFromSuperview() },
                             cleanup: cleanup)
        #endif
    }
}
