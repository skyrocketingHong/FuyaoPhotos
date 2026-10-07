import SwiftUI
import MetalKit

/// MTKView integration for Telegram's original DustEffect compute and instanced-quad shaders.
struct TelegramDustView: View {
    static let lifetime: Duration = .seconds(4)
    static let overflow: CGFloat = 160
    let image: CGImage
    let sourceSize: CGSize
    @State private var ready = false

    var body: some View {
        ZStack {
            if !ready {
                Image(decorative: image, scale: 1)
                    .resizable()
                    .frame(width: sourceSize.width, height: sourceSize.height)
            }
            DustMetalSurface(image: image, sourceSize: sourceSize) { ready = true }
        }
        .frame(width: sourceSize.width + Self.overflow * 2,
               height: sourceSize.height + Self.overflow * 2)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

private struct DustMetalSurface {
    let image: CGImage
    let sourceSize: CGSize
    let onReady: @MainActor @Sendable () -> Void

    func makeCoordinator() -> DustMetalRenderer? {
        DustMetalRenderer(image: image, sourceSize: sourceSize, onReady: onReady)
    }

    private func makeView(_ coordinator: DustMetalRenderer?) -> MTKView {
        let view = MTKView(frame: .zero, device: coordinator?.device)
        view.colorPixelFormat = .bgra8Unorm
        view.clearColor = MTLClearColorMake(0, 0, 0, 0)
        view.framebufferOnly = true
        view.preferredFramesPerSecond = 120
        view.isPaused = coordinator == nil
#if os(macOS)
        view.wantsLayer = true
        view.layer?.isOpaque = false
        view.layer?.backgroundColor = NSColor.clear.cgColor
#else
        view.isOpaque = false
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
#endif
        view.delegate = coordinator
        if coordinator == nil { Task { @MainActor in onReady() } }
        return view
    }
}

#if os(macOS)
extension DustMetalSurface: NSViewRepresentable {
    func makeNSView(context: Context) -> MTKView { makeView(context.coordinator) }
    func updateNSView(_ view: MTKView, context: Context) {}
    static func dismantleNSView(_ view: MTKView, coordinator: DustMetalRenderer?) {
        view.isPaused = true
        view.delegate = nil
    }
}
#else
extension DustMetalSurface: UIViewRepresentable {
    func makeUIView(context: Context) -> MTKView { makeView(context.coordinator) }
    func updateUIView(_ view: MTKView, context: Context) {}
    static func dismantleUIView(_ view: MTKView, coordinator: DustMetalRenderer?) {
        view.isPaused = true
        view.delegate = nil
    }
}
#endif

@MainActor private final class DustMetalRenderer: NSObject, MTKViewDelegate {
    let device: MTLDevice
    private let queue: MTLCommandQueue
    private let pipelines: DustMetalPipelines
    private let texture: MTLTexture
    private let particles: MTLBuffer
    private let sourceSize: CGSize
    private let resolution: SIMD2<UInt32>
    private let count: Int
    private let onReady: @MainActor @Sendable () -> Void
    private var initialized = false
    private var firstFrame = true
    private var phase: Float = 0
    private var lastTimestamp: CFTimeInterval?

    init?(image: CGImage, sourceSize: CGSize, onReady: @escaping @MainActor @Sendable () -> Void) {
        let columns = Int(sourceSize.width)
        let rows = Int(sourceSize.height)
        guard columns > 0, rows > 0, columns <= 16_384, rows <= 16_384,
              columns * rows <= 4_000_000,
              let pipelines = DustMetalPipelines.shared,
              let queue = pipelines.device.makeCommandQueue(),
              let particles = pipelines.device.makeBuffer(length: columns * rows * 20, options: .storageModePrivate),
              let texture = try? MTKTextureLoader(device: pipelines.device).newTexture(cgImage: image,
                  options: [.SRGB: false as NSNumber]) else { return nil }
        self.device = pipelines.device
        self.queue = queue
        self.pipelines = pipelines
        self.particles = particles
        self.texture = texture
        self.sourceSize = sourceSize
        self.resolution = SIMD2(UInt32(columns), UInt32(rows))
        self.count = columns * rows
        self.onReady = onReady
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard !view.bounds.isEmpty, let drawable = view.currentDrawable,
              let descriptor = view.currentRenderPassDescriptor,
              let command = queue.makeCommandBuffer(),
              let compute = command.makeComputeCommandEncoder() else { return }
        let timestamp = CACurrentMediaTime()
        let elapsed = lastTimestamp.map { timestamp - $0 } ?? 0
        let delta = elapsed > 0.001 && elapsed < 0.2 ? elapsed : 1.0 / Double(view.preferredFramesPerSecond)
        lastTimestamp = timestamp
        phase += Float(delta)
        if phase >= 4 { compute.endEncoding(); view.isPaused = true; return }

        var direction: Float = 1
        var particleResolution = resolution
        var currentPhase = phase
        // DustEffectLayer advances its physical simulation at twice display time.
        var timeStep = firstFrame ? Float(0) : Float(delta) * 2
        let threads = MTLSize(width: count, height: 1, depth: 1)
        let group = MTLSize(width: 32, height: 1, depth: 1)
        compute.setBuffer(particles, offset: 0, index: 0)
        if !initialized {
            compute.setComputePipelineState(pipelines.initialize)
            compute.setBytes(&direction, length: 4, index: 1)
            // Exact dispatch avoids the original host's rounded-up initialization overrun.
            compute.dispatchThreads(threads, threadsPerThreadgroup: group)
            initialized = true
        }
        compute.setComputePipelineState(pipelines.update)
        compute.setBytes(&particleResolution, length: 8, index: 1)
        compute.setBytes(&currentPhase, length: 4, index: 2)
        compute.setBytes(&timeStep, length: 4, index: 3)
        compute.setBytes(&direction, length: 4, index: 4)
        compute.dispatchThreads(threads, threadsPerThreadgroup: group)
        compute.endEncoding()

        guard let render = command.makeRenderCommandEncoder(descriptor: descriptor) else { return }
        let canvas = view.bounds.size
        var rect = SIMD4<Float>(Float((canvas.width - sourceSize.width) / 2 / canvas.width),
            Float((canvas.height - sourceSize.height) / 2 / canvas.height),
            Float(sourceSize.width / canvas.width), Float(sourceSize.height / canvas.height))
        var size = SIMD2<Float>(Float(sourceSize.width), Float(sourceSize.height))
        render.setRenderPipelineState(pipelines.render)
        render.setVertexBytes(&rect, length: 16, index: 0)
        render.setVertexBytes(&size, length: 8, index: 1)
        render.setVertexBytes(&particleResolution, length: 8, index: 2)
        render.setVertexBuffer(particles, offset: 0, index: 3)
        render.setFragmentTexture(texture, index: 0)
        render.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: count)
        render.endEncoding()
        command.present(drawable)
        if firstFrame {
            firstFrame = false
            let ready = onReady
            command.addCompletedHandler { _ in Task { @MainActor in ready() } }
        }
        command.commit()
    }
}

@MainActor private final class DustMetalPipelines {
    static let shared = DustMetalPipelines()
    let device: MTLDevice
    let initialize: MTLComputePipelineState
    let update: MTLComputePipelineState
    let render: MTLRenderPipelineState

    private init?() {
        guard let device = MTLCreateSystemDefaultDevice(), let library = device.makeDefaultLibrary(),
              let initialize = library.makeFunction(name: "dustEffectInitializeParticle"),
              let update = library.makeFunction(name: "dustEffectUpdateParticle"),
              let vertex = library.makeFunction(name: "dustEffectVertex"),
              let fragment = library.makeFunction(name: "dustEffectFragment") else { return nil }
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = vertex
        descriptor.fragmentFunction = fragment
        let attachment = descriptor.colorAttachments[0]!
        attachment.pixelFormat = .bgra8Unorm
        attachment.isBlendingEnabled = true
        attachment.sourceRGBBlendFactor = .one
        attachment.sourceAlphaBlendFactor = .one
        attachment.destinationRGBBlendFactor = .oneMinusSourceAlpha
        attachment.destinationAlphaBlendFactor = .one
        do {
            self.device = device
            self.initialize = try device.makeComputePipelineState(function: initialize)
            self.update = try device.makeComputePipelineState(function: update)
            self.render = try device.makeRenderPipelineState(descriptor: descriptor)
        } catch { return nil }
    }
}
