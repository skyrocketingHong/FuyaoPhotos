import SwiftUI
import Metal
import QuartzCore
#if os(macOS)
import AppKit
#else
import UIKit
#endif

struct PhotoImportTransitionTarget {
    let attempt: PhotoImportTargetLifecycle.Attempt
    let image: CGImage
    let frame: CGRect
    let contentFrame: CGRect
    let appearance: PhotoImportAppearance
    var requestID: UUID { attempt.requestID }
}

struct PhotoImportAppearance: Equatable {
    let colorScheme: ColorScheme
    let contrast: ColorSchemeContrast
    let dynamicTypeSize: DynamicTypeSize
    let localeIdentifier: String
    let layoutDirection: LayoutDirection
    let displayScale: CGFloat
    let reduceTransparency: Bool
    let legibilityWeight: LegibilityWeight?
    let font: Font?
    let enabled: Bool
    let accent: SIMD4<Float>
#if os(macOS)
    let controlActiveState: ControlActiveState
#endif

    init(environment: EnvironmentValues) {
        colorScheme = environment.colorScheme
        contrast = environment.colorSchemeContrast
        dynamicTypeSize = environment.dynamicTypeSize
        localeIdentifier = environment.locale.identifier
        layoutDirection = environment.layoutDirection
        displayScale = environment.displayScale
        reduceTransparency = environment.accessibilityReduceTransparency
        legibilityWeight = environment.legibilityWeight
        font = environment.font
        enabled = environment.isEnabled
        let color = Color.accentColor.resolve(in: environment)
        accent = SIMD4(color.red, color.green, color.blue, color.opacity)
#if os(macOS)
        controlActiveState = environment.controlActiveState
#endif
    }
}

struct PhotoImportSnapshotAppearance: ViewModifier {
    let appearance: PhotoImportAppearance
    var contentVisible = false

    func body(content: Content) -> some View {
        // Keep the new host's layout environment; only carry public appearance values across hosts.
        content
            .environment(\.colorScheme, appearance.colorScheme)
            .environment(\.dynamicTypeSize, appearance.dynamicTypeSize)
            .environment(\.locale, Locale(identifier: appearance.localeIdentifier))
            .environment(\.layoutDirection, appearance.layoutDirection)
            .environment(\.displayScale, appearance.displayScale)
            .environment(\.legibilityWeight, appearance.legibilityWeight)
            .environment(\.font, appearance.font)
            .environment(\.isEnabled, appearance.enabled)
            .environment(\.photoImportContentVisible, contentVisible)
            .accentColor(Color(.sRGB, red: Double(appearance.accent.x), green: Double(appearance.accent.y),
                blue: Double(appearance.accent.z), opacity: Double(appearance.accent.w)))
#if os(macOS)
            .environment(\.controlActiveState, appearance.controlActiveState)
#endif
    }
}

@MainActor @Observable final class PhotoImportTransitionCapture {
    struct Request: Equatable {
        let id: UUID
        let deadline: ContinuousClock.Instant
    }

    private(set) var request: Request?
    private(set) var target: PhotoImportTransitionTarget?
    private(set) var failedID: UUID?
    private(set) var liveContentFrame: CGRect?
    @ObservationIgnored private var lifecycle = PhotoImportTargetLifecycle()

    func begin(id: UUID, deadline: ContinuousClock.Instant) {
        lifecycle.begin(id: id)
        target = nil
        failedID = nil
        liveContentFrame = nil
        request = Request(id: id, deadline: deadline)
    }

    func nextAttempt(id: UUID) -> PhotoImportTargetLifecycle.Attempt? {
        guard let request, request.id == id, .now < request.deadline else { return nil }
        return lifecycle.nextAttempt(id: id)
    }

    func submit(_ value: PhotoImportTransitionTarget) {
        guard let request, request.id == value.requestID, .now < request.deadline,
              target == nil, lifecycle.complete(value.attempt) else { return }
        target = value
    }

    func cancel(id: UUID? = nil) {
        guard id == nil || request?.id == id else { return }
        lifecycle.cancel(id: id)
        request = nil
        target = nil
        failedID = nil
        liveContentFrame = nil
    }

    func fail(_ attempt: PhotoImportTargetLifecycle.Attempt) {
        guard lifecycle.fail(attempt) else { return }
        failedID = attempt.requestID
    }

    func invalidate(id: UUID) {
        guard request?.id == id, lifecycle.cancel(id: id) else { return }
        failedID = id
    }

    func updateContentFrame(_ frame: CGRect) {
        guard request != nil, !frame.isEmpty else { return }
        liveContentFrame = frame
    }
}

extension EnvironmentValues {
    @Entry var photoImportTransitionCapture: PhotoImportTransitionCapture? = nil
}

@MainActor @Observable final class PhotoImportNativeContext {
    var attached = false
#if os(macOS)
    @ObservationIgnored weak var window: NSWindow?
#else
    @ObservationIgnored weak var windowScene: UIWindowScene?
#endif
}

struct PhotoImportNativeContextReader {
    let context: PhotoImportNativeContext
}

#if os(macOS)
extension PhotoImportNativeContextReader: NSViewRepresentable {
    func makeNSView(context: Context) -> Marker {
        let view = Marker()
        view.captureContext = self.context
        return view
    }

    func updateNSView(_ view: Marker, context: Context) { view.captureContext = self.context }

    final class Marker: NSView {
        weak var captureContext: PhotoImportNativeContext?

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            Task { @MainActor [weak self] in
                guard let self, let captureContext else { return }
                captureContext.window = window
                captureContext.attached = window != nil
            }
        }
    }
}
#else
extension PhotoImportNativeContextReader: UIViewRepresentable {
    func makeUIView(context: Context) -> Marker {
        let view = Marker()
        view.isUserInteractionEnabled = false
        view.captureContext = self.context
        return view
    }

    func updateUIView(_ view: Marker, context: Context) { view.captureContext = self.context }

    final class Marker: UIView {
        weak var captureContext: PhotoImportNativeContext?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            Task { @MainActor [weak self] in
                guard let self, let captureContext else { return }
                captureContext.windowScene = window?.windowScene
                captureContext.attached = window != nil
            }
        }
    }
}
#endif

/// Renders the actual native Form in a non-key host, then crops its measured card.
@MainActor enum PhotoImportNativeSnapshotter {
    final class Measurement {
        var frame = CGRect.zero
    }

    struct Snapshot {
        let image: CGImage
        let frame: CGRect
        let contentFrame: CGRect
    }

    private struct RenderedForm {
        let image: CGImage
        let scale: CGFloat
        let contentFrame: CGRect
    }

    enum Failure: Error { case unavailable, expired, invalidBounds }

    private struct Host {
        let layer: CALayer
        let layout: @MainActor () -> Void
        let detach: @MainActor () -> Void
        let cleanup: @MainActor () -> Void
    }

    static func capture<Content: View, Background: View>(
        content: Content,
        background: Background,
        pointSize: CGSize,
        measurement: Measurement,
        colorScheme: ColorScheme,
        displayScale: CGFloat,
        context: PhotoImportNativeContext,
        deadline: ContinuousClock.Instant
    ) async throws -> Snapshot {
        let foreground = try await render(content: content, pointSize: pointSize, measurement: measurement,
            colorScheme: colorScheme, displayScale: displayScale, context: context, deadline: deadline)
        let background = try await render(content: background, pointSize: pointSize, measurement: nil,
            colorScheme: colorScheme, displayScale: displayScale, context: context, deadline: deadline)
        try check(deadline)
        let contentPixels = CGRect(x: foreground.contentFrame.minX * foreground.scale,
            y: foreground.contentFrame.minY * foreground.scale, width: foreground.contentFrame.width * foreground.scale,
            height: foreground.contentFrame.height * foreground.scale)
        let crop = try differenceBounds(foreground.image, background.image,
            content: contentPixels, deadline: deadline)
        let calibration = CGRect(x: 0, y: 0, width: ceil(4 * foreground.scale), height: ceil(4 * foreground.scale))
        guard !crop.intersects(calibration) else { throw Failure.invalidBounds }
        guard let image = foreground.image.cropping(to: crop) else { throw Failure.invalidBounds }
        let frame = CGRect(x: crop.minX / foreground.scale, y: crop.minY / foreground.scale,
            width: crop.width / foreground.scale, height: crop.height / foreground.scale)
        guard frame.intersects(foreground.contentFrame) else { throw Failure.invalidBounds }
        return Snapshot(image: image, frame: frame, contentFrame: foreground.contentFrame)
    }

    private static func render<Content: View>(
        content: Content,
        pointSize: CGSize,
        measurement: Measurement?,
        colorScheme: ColorScheme,
        displayScale: CGFloat,
        context: PhotoImportNativeContext,
        deadline: ContinuousClock.Instant
    ) async throws -> RenderedForm {
        try check(deadline)
        guard pointSize.width.isFinite, pointSize.height.isFinite,
              displayScale.isFinite, displayScale > 0,
              pointSize.width >= 8, pointSize.height >= 8,
              pointSize.width <= 16_384, pointSize.height <= 16_384,
              let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue(),
              let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else { throw Failure.unavailable }
        let scale = min(displayScale, sqrt(Double(PhotoMotionTokens.snapshotPixelLimit) / (pointSize.width * pointSize.height)))
        let width = max(1, Int((pointSize.width * scale).rounded()))
        let height = max(1, Int((pointSize.height * scale).rounded()))
        guard width <= 8192, height <= 8192 else { throw Failure.invalidBounds }
        let host = try makeHost(content: content, pointSize: pointSize, colorScheme: colorScheme, context: context)
        defer { host.cleanup() }

        var previousFrame = CGRect.zero
        for pass in 0..<4 {
            host.layout()
            CATransaction.flush()
            await nextMainTurn()
            try check(deadline)
            if let measurement {
                if !measurement.frame.isEmpty, measurement.frame == previousFrame { break }
                previousFrame = measurement.frame
            }
            if measurement == nil, pass > 0 { break }
        }
        if let measurement, measurement.frame.isEmpty { throw Failure.invalidBounds }
        let contentFrame = measurement?.frame ?? .zero
        host.detach()

        let root = CALayer()
        root.frame = CGRect(x: 0, y: 0, width: width, height: height)
        host.layer.anchorPoint = .zero
        host.layer.position = .zero
        host.layer.transform = CATransform3DMakeScale(scale, scale, 1)
        root.addSublayer(host.layer)
        defer { host.layer.removeFromSuperlayer() }

        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm,
            width: width, height: height, mipmapped: false)
        descriptor.storageMode = .shared
        descriptor.usage = [.renderTarget, .shaderRead, .shaderWrite]
        guard let texture = device.makeTexture(descriptor: descriptor) else { throw Failure.unavailable }
        let renderer = CARenderer(mtlTexture: texture, options: [
            kCARendererMetalCommandQueue: queue, kCARendererColorSpace: colorSpace
        ])
        renderer.layer = root
        renderer.bounds = root.bounds
        defer { renderer.layer = nil }

        try await draw(renderer, queue: queue, deadline: deadline)
        let reversesRows = try needsRowReversal(texture, scale: scale)
        let image = try readImage(texture, reversingRows: reversesRows, colorSpace: colorSpace)
        return RenderedForm(image: image, scale: scale, contentFrame: contentFrame)
    }

    private static func differenceBounds(_ foreground: CGImage, _ background: CGImage,
                                         content: CGRect, deadline: ContinuousClock.Instant) throws -> CGRect {
        guard foreground.width == background.width, foreground.height == background.height,
              foreground.bitsPerPixel == 32, background.bitsPerPixel == 32,
              let firstData = foreground.dataProvider?.data, let secondData = background.dataProvider?.data,
              let first = CFDataGetBytePtr(firstData), let second = CFDataGetBytePtr(secondData)
        else { throw Failure.invalidBounds }
        let width = foreground.width, height = foreground.height
        var mask = [UInt8](repeating: 0, count: width * height)
        for y in 0..<height {
            if y.isMultiple(of: 64) { try check(deadline) }
            for x in 0..<width {
                let a = y * foreground.bytesPerRow + x * 4
                let b = y * background.bytesPerRow + x * 4
                let difference = max(max(abs(Int(first[a]) - Int(second[b])), abs(Int(first[a + 1]) - Int(second[b + 1]))),
                    max(abs(Int(first[a + 2]) - Int(second[b + 2])), abs(Int(first[a + 3]) - Int(second[b + 3]))))
                if difference >= 6 {
                    mask[y * width + x] = 1
                }
            }
        }
        var result = CGRect.null
        var queue: [Int] = []
        for index in mask.indices where mask[index] == 1 {
            queue.removeAll(keepingCapacity: true)
            queue.append(index)
            mask[index] = 0
            var next = 0
            var minX = width, minY = height, maxX = -1, maxY = -1
            var touchesContent = false
            while next < queue.count {
                if next.isMultiple(of: 4096) { try check(deadline) }
                let pixel = queue[next]
                next += 1
                let x = pixel % width, y = pixel / width
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
                if content.contains(CGPoint(x: x, y: y)) { touchesContent = true }
                if x > 0, mask[pixel - 1] == 1 { mask[pixel - 1] = 0; queue.append(pixel - 1) }
                if x + 1 < width, mask[pixel + 1] == 1 { mask[pixel + 1] = 0; queue.append(pixel + 1) }
                if y > 0, mask[pixel - width] == 1 { mask[pixel - width] = 0; queue.append(pixel - width) }
                if y + 1 < height, mask[pixel + width] == 1 { mask[pixel + width] = 0; queue.append(pixel + width) }
                if x > 0, y > 0, mask[pixel - width - 1] == 1 {
                    mask[pixel - width - 1] = 0; queue.append(pixel - width - 1)
                }
                if x + 1 < width, y > 0, mask[pixel - width + 1] == 1 {
                    mask[pixel - width + 1] = 0; queue.append(pixel - width + 1)
                }
                if x > 0, y + 1 < height, mask[pixel + width - 1] == 1 {
                    mask[pixel + width - 1] = 0; queue.append(pixel + width - 1)
                }
                if x + 1 < width, y + 1 < height, mask[pixel + width + 1] == 1 {
                    mask[pixel + width + 1] = 0; queue.append(pixel + width + 1)
                }
            }
            let component = CGRect(x: minX, y: minY, width: maxX - minX + 1, height: maxY - minY + 1)
            // Native card fills, borders and shadows belong to the measured content; unrelated scrollers do not.
            if touchesContent || component.contains(content) { result = result.union(component) }
        }
        guard !result.isNull, !result.isEmpty else { throw Failure.invalidBounds }
        return result
    }

    private static func check(_ deadline: ContinuousClock.Instant) throws {
        try Task.checkCancellation()
        guard .now < deadline else { throw Failure.expired }
    }

    private static func nextMainTurn() async {
        await withCheckedContinuation { continuation in
            DispatchQueue.main.async { continuation.resume() }
        }
    }

    private static func draw(_ renderer: CARenderer, queue: any MTLCommandQueue,
                             deadline: ContinuousClock.Instant) async throws {
        try check(deadline)
        CATransaction.flush()
        renderer.beginFrame(atTime: CACurrentMediaTime(), timeStamp: nil)
        renderer.addUpdate(renderer.bounds)
        renderer.render()
        renderer.endFrame()
        guard let command = queue.makeCommandBuffer() else { throw Failure.unavailable }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            command.addCompletedHandler { _ in continuation.resume() }
            command.commit()
        }
        try check(deadline)
        guard command.status == .completed else { throw Failure.unavailable }
    }

    private static func needsRowReversal(_ texture: any MTLTexture, scale: CGFloat) throws -> Bool {
        let inset = max(1, Int(2 * scale))
        func isMagenta(row: Int) -> Bool {
            var pixel = [UInt8](repeating: 0, count: 4)
            pixel.withUnsafeMutableBytes { buffer in
                texture.getBytes(buffer.baseAddress!, bytesPerRow: 4,
                    from: MTLRegionMake2D(inset, row, 1, 1), mipmapLevel: 0)
            }
            return pixel[0] > 200 && pixel[1] < 128 && pixel[2] > 200 && pixel[3] > 240
        }
        let first = isMagenta(row: inset)
        let last = isMagenta(row: texture.height - 1 - inset)
        guard first != last else { throw Failure.unavailable }
        return last
    }

    private static func readImage(_ texture: any MTLTexture, reversingRows: Bool,
                                  colorSpace: CGColorSpace) throws -> CGImage {
        let width = texture.width, height = texture.height, rowBytes = width * 4
        var data = Data(count: rowBytes * height)
        try data.withUnsafeMutableBytes { buffer in
            guard let address = buffer.baseAddress else { throw Failure.unavailable }
            texture.getBytes(address, bytesPerRow: rowBytes,
                from: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0)
            if reversingRows {
                let temporary = UnsafeMutableRawPointer.allocate(byteCount: rowBytes, alignment: 64)
                defer { temporary.deallocate() }
                for row in 0..<(height / 2) {
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
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue),
                provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
        else { throw Failure.unavailable }
        return image
    }

    private static func makeHost<Content: View>(content: Content, pointSize: CGSize,
                                                colorScheme: ColorScheme, context: PhotoImportNativeContext) throws -> Host {
        let bounds = CGRect(origin: .zero, size: pointSize)
        let scene = content.frame(width: pointSize.width, height: pointSize.height)
            .environment(\.colorScheme, colorScheme).ignoresSafeArea()
            .transaction { $0.disablesAnimations = true }
#if os(macOS)
        let hosting = NSHostingView(rootView: scene)
        hosting.frame = bounds
        let container = NSView(frame: bounds)
        container.wantsLayer = true
        container.addSubview(hosting)
        let calibration = NSView(frame: bounds)
        calibration.wantsLayer = true
        calibration.layer?.backgroundColor = CGColor(gray: 0, alpha: 0)
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
        guard let layer = container.layer else { window.close(); throw Failure.unavailable }
        return Host(layer: layer, layout: {
            hosting.layoutSubtreeIfNeeded()
            hosting.displayIfNeeded()
        }, detach: { window.contentView = nil }, cleanup: {
            window.contentView = nil
            hosting.removeFromSuperview()
            window.close()
        })
#else
        guard let windowScene = context.windowScene else { throw Failure.unavailable }
        let hosting = UIHostingController(rootView: scene)
        hosting.safeAreaRegions = []
        hosting.loadViewIfNeeded()
        hosting.view.frame = bounds
        hosting.view.backgroundColor = .clear
        let owner = UIViewController()
        let container = UIView(frame: bounds)
        container.backgroundColor = .clear
        owner.view = container
        owner.addChild(hosting)
        container.addSubview(hosting.view)
        hosting.didMove(toParent: owner)
        let calibration = UIView(frame: bounds)
        calibration.backgroundColor = .clear
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
        return Host(layer: container.layer, layout: {
            container.setNeedsLayout()
            container.layoutIfNeeded()
            hosting.view.setNeedsLayout()
            hosting.view.layoutIfNeeded()
            hosting.view.layer.displayIfNeeded()
        }, detach: {
            window.rootViewController = nil
            container.removeFromSuperview()
        }, cleanup: {
            window.rootViewController = nil
            hosting.willMove(toParent: nil)
            hosting.view.removeFromSuperview()
            hosting.removeFromParent()
            window.windowScene = nil
        })
#endif
    }
}
