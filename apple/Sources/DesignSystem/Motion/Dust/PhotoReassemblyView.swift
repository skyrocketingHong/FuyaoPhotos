import SwiftUI
import MetalKit
import QuartzCore

struct PhotoReassemblyView: View {
    let sourceImage: CGImage
    let sourceFrame: CGRect
    let targetImage: CGImage
    let targetFrame: CGRect
    let canvasSize: CGSize
    let onFinished: @MainActor @Sendable () -> Void
    let onUnavailable: @MainActor @Sendable () -> Void
    @State private var ready = false
    @State private var playback = PhotoReassemblyPlayback()

    var body: some View {
        let finished: @MainActor @Sendable () -> Void = onFinished
        let unavailable: @MainActor @Sendable () -> Void = onUnavailable
        ZStack(alignment: .topLeading) {
            if !ready, PhotoReassemblyGeometry.valid(sourceFrame) {
                Image(decorative: sourceImage, scale: 1, orientation: .up)
                    .resizable()
                    .frame(width: sourceFrame.width, height: sourceFrame.height)
                    .position(x: sourceFrame.midX, y: sourceFrame.midY)
            }
            PhotoReassemblySurface(sourceImage: sourceImage, sourceFrame: sourceFrame,
                targetImage: targetImage, targetFrame: targetFrame, canvasSize: canvasSize,
                playback: playback, onReady: { ready = true }, onFinished: finished, onUnavailable: unavailable)
                .opacity(ready ? 1 : 0)
        }
        .frame(width: canvasSize.width.isFinite ? max(0, canvasSize.width) : 0,
               height: canvasSize.height.isFinite ? max(0, canvasSize.height) : 0)
        .clipped()
        .transaction { $0.animation = nil }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

@MainActor private final class PhotoReassemblyPlayback {
    private var claimed = false
    private var ended = false

    func claim() -> Bool {
        guard !claimed, !ended else { return false }
        claimed = true
        return true
    }

    func ready(_ action: @MainActor @Sendable () -> Void) {
        guard claimed, !ended else { return }
        action()
    }

    func finish(_ action: @MainActor @Sendable () -> Void) {
        guard claimed, !ended else { return }
        ended = true
        action()
    }

    func cancel() { ended = true }
}

private struct PhotoReassemblySurface {
    let sourceImage: CGImage
    let sourceFrame: CGRect
    let targetImage: CGImage
    let targetFrame: CGRect
    let canvasSize: CGSize
    let playback: PhotoReassemblyPlayback
    let onReady: @MainActor @Sendable () -> Void
    let onFinished: @MainActor @Sendable () -> Void
    let onUnavailable: @MainActor @Sendable () -> Void

    func makeCoordinator() -> PhotoReassemblyRenderer? {
        guard playback.claim() else { return nil }
        let ready: @MainActor @Sendable () -> Void = onReady
        let finished: @MainActor @Sendable () -> Void = onFinished
        let unavailable: @MainActor @Sendable () -> Void = onUnavailable
        return PhotoReassemblyRenderer(sourceImage: sourceImage, sourceFrame: sourceFrame,
            targetImage: targetImage, targetFrame: targetFrame, canvasSize: canvasSize,
            playback: playback, onReady: ready, onFinished: finished, onUnavailable: unavailable)
    }

    private func makeView(_ renderer: PhotoReassemblyRenderer?) -> MTKView {
        let view = MTKView(frame: .zero, device: renderer?.device)
        view.colorPixelFormat = .bgra8Unorm
        view.clearColor = MTLClearColorMake(0, 0, 0, 0)
        view.framebufferOnly = true
        view.preferredFramesPerSecond = 120
        view.isPaused = renderer == nil
#if os(macOS)
        view.wantsLayer = true
        view.layer?.isOpaque = false
        view.layer?.backgroundColor = NSColor.clear.cgColor
#else
        view.isOpaque = false
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
#endif
        (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        view.delegate = renderer
        renderer?.attach(to: view)
        if renderer == nil {
            let unavailable: @MainActor @Sendable () -> Void = onUnavailable
            Task { @MainActor in playback.finish(unavailable) }
        }
        return view
    }
}

#if os(macOS)
extension PhotoReassemblySurface: NSViewRepresentable {
    func makeNSView(context: Context) -> MTKView { makeView(context.coordinator) }
    func updateNSView(_ view: MTKView, context: Context) {}
    static func dismantleNSView(_ view: MTKView, coordinator: PhotoReassemblyRenderer?) {
        coordinator?.cancel()
        view.isPaused = true
        view.delegate = nil
    }
}
#else
extension PhotoReassemblySurface: UIViewRepresentable {
    func makeUIView(context: Context) -> MTKView { makeView(context.coordinator) }
    func updateUIView(_ view: MTKView, context: Context) {}
    static func dismantleUIView(_ view: MTKView, coordinator: PhotoReassemblyRenderer?) {
        coordinator?.cancel()
        view.isPaused = true
        view.delegate = nil
    }
}
#endif

@MainActor private final class PhotoReassemblyRenderer: NSObject, MTKViewDelegate {
    let device: MTLDevice
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let sourceTexture: MTLTexture
    private let targetTexture: MTLTexture
    private let sourceFrame: CGRect
    private let targetFrame: CGRect
    private let grid: PhotoReassemblyGeometry.Grid
    private let playback: PhotoReassemblyPlayback
    private let onReady: @MainActor @Sendable () -> Void
    private let onFinished: @MainActor @Sendable () -> Void
    private let onUnavailable: @MainActor @Sendable () -> Void
    private weak var view: MTKView?
    private var startedAt: CFTimeInterval?
    private var reportedReady = false
    private var viewportGeneration = 0
    private var finalSubmitted = false
    private var active = true
    private var inFlightFrames = 0

    init?(sourceImage: CGImage, sourceFrame: CGRect, targetImage: CGImage, targetFrame: CGRect,
          canvasSize: CGSize, playback: PhotoReassemblyPlayback,
          onReady: @escaping @MainActor @Sendable () -> Void,
          onFinished: @escaping @MainActor @Sendable () -> Void,
          onUnavailable: @escaping @MainActor @Sendable () -> Void) {
        guard PhotoReassemblyGeometry.valid(CGRect(origin: .zero, size: canvasSize)),
              let grid = PhotoReassemblyGeometry.grid(source: sourceFrame, target: targetFrame),
              let pipelines = PhotoReassemblyPipeline.shared,
              let queue = pipelines.device.makeCommandQueue(),
              let sourceTexture = try? MTKTextureLoader(device: pipelines.device).newTexture(cgImage: sourceImage,
                  options: [.SRGB: false as NSNumber]),
              let targetTexture = try? MTKTextureLoader(device: pipelines.device).newTexture(cgImage: targetImage,
                  options: [.SRGB: false as NSNumber]) else { return nil }
        device = pipelines.device
        pipeline = pipelines.render
        self.queue = queue
        self.sourceTexture = sourceTexture
        self.targetTexture = targetTexture
        self.sourceFrame = sourceFrame
        self.targetFrame = targetFrame
        self.grid = grid
        self.playback = playback
        self.onReady = onReady
        self.onFinished = onFinished
        self.onUnavailable = onUnavailable
    }

    func attach(to view: MTKView) {
        self.view = view
        PhotoDepartureDiagnostics.emit("renderer.attached")
    }

    func cancel() {
        active = false
        playback.cancel()
        view?.isPaused = true
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        guard active else { return }
        PhotoDepartureDiagnostics.emit("renderer.drawable-size", values: [Double(size.width), Double(size.height)])
        viewportGeneration += 1
        finalSubmitted = false
        view.isPaused = false
    }

    func draw(in view: MTKView) {
        guard active, !finalSubmitted, inFlightFrames < 3, !view.bounds.isEmpty,
              let drawable = view.currentDrawable, let descriptor = view.currentRenderPassDescriptor,
              let command = queue.makeCommandBuffer() else { return }
        guard let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else {
            unavailable()
            return
        }
        let now = CACurrentMediaTime()
        if startedAt == nil {
            startedAt = now
            PhotoDepartureDiagnostics.emit("renderer.draw")
        }
        let duration = Double(PhotoMotionTokens.reassemblyMillis) / 1000
        let progress = Float(min(1, max(0, (now - (startedAt ?? now)) / duration)))
        // IME dismissal changes the canvas height, not the frozen source and target frames.
        var uniforms = PhotoReassemblyUniforms(source: sourceFrame, target: targetFrame,
            canvas: view.bounds.size, grid: grid, progress: progress)
        encoder.setRenderPipelineState(pipeline)
        encoder.setVertexBytes(&uniforms, length: MemoryLayout<PhotoReassemblyUniforms>.stride, index: 0)
        encoder.setFragmentTexture(sourceTexture, index: 0)
        encoder.setFragmentTexture(targetTexture, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: grid.count)
        encoder.endEncoding()
        command.present(drawable)
        let generation = viewportGeneration
        let reportsFinished = progress >= 1
        finalSubmitted = reportsFinished
        inFlightFrames += 1
        let completion: @MainActor @Sendable (Bool) -> Void = { [weak self] succeeded in
            guard let self else { return }
            self.inFlightFrames -= 1
            guard self.active else { return }
            guard succeeded else { self.unavailable(); return }
            guard generation == self.viewportGeneration else { return }
            if !self.reportedReady {
                self.reportedReady = true
                PhotoDepartureDiagnostics.emit("renderer.ready")
                self.playback.ready(self.onReady)
            }
            if reportsFinished {
                self.active = false
                self.playback.finish(self.onFinished)
            }
        }
        command.addCompletedHandler { completed in
            let succeeded = completed.status == .completed
            Task { @MainActor in completion(succeeded) }
        }
        command.commit()
        if reportsFinished { view.isPaused = true }
    }

    private func unavailable() {
        active = false
        view?.isPaused = true
        playback.finish(onUnavailable)
    }
}

@MainActor private final class PhotoReassemblyPipeline {
    static let shared = PhotoReassemblyPipeline()
    let device: MTLDevice
    let render: MTLRenderPipelineState

    private init?() {
        guard let device = MTLCreateSystemDefaultDevice(), let library = device.makeDefaultLibrary(),
              let vertex = library.makeFunction(name: "photoReassemblyVertex"),
              let fragment = library.makeFunction(name: "photoReassemblyFragment") else { return nil }
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = vertex
        descriptor.fragmentFunction = fragment
        let attachment = descriptor.colorAttachments[0]!
        attachment.pixelFormat = .bgra8Unorm
        attachment.isBlendingEnabled = true
        attachment.sourceRGBBlendFactor = .one
        attachment.sourceAlphaBlendFactor = .one
        attachment.destinationRGBBlendFactor = .oneMinusSourceAlpha
        attachment.destinationAlphaBlendFactor = .oneMinusSourceAlpha
        guard let render = try? device.makeRenderPipelineState(descriptor: descriptor) else { return nil }
        self.device = device
        self.render = render
    }
}
