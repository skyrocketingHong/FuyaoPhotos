import Testing
import Foundation
import CryptoKit
import Metal
import MetalKit
import CoreGraphics

private let dustSources = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    .appendingPathComponent("Sources/DesignSystem/Motion/Dust")

private func originalDustLibrary(_ device: MTLDevice) throws -> MTLLibrary {
    let text = try ["loki_header.metal", "loki.metal", "DustEffectShaders.metal"].map {
        try String(contentsOf: dustSources.appendingPathComponent($0), encoding: .utf8)
            .replacingOccurrences(of: "#include \"loki_header.metal\"", with: "")
    }.joined(separator: "\n")
    return try device.makeLibrary(source: text, options: nil)
}

@Test func dustShadersRemainThePinnedUpstreamImplementation() throws {
    let expected = [
        "DustEffectShaders.metal": "f526ef9d43b0720945d5bde07371150e55208497abfaefb4632a8d7af44c9cae",
        "loki.metal": "71fe7cb7922c92766fff4d386cde4857357bd2ce5f8f2cf4de7881f929999559",
        "loki_header.metal": "03ff37f00baf36c07b8a93470c14b33fbcb8dff8a81815f194002fe65e5d5af4"
    ]
    for (file, hash) in expected {
        let bytes = try Data(contentsOf: dustSources.appendingPathComponent(file))
        #expect(SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined() == hash)
    }
}

@Test(.enabled(if: MTLCreateSystemDefaultDevice() != nil, "Requires a Metal GPU; source-hash validation still runs."))
func originalDustComputePreservesTextureGridBoundsAndLifetime() throws {
    let device = try #require(MTLCreateSystemDefaultDevice())
    let library = try originalDustLibrary(device)
    let initializer = try device.makeComputePipelineState(function: #require(library.makeFunction(name: "dustEffectInitializeParticle")))
    let updater = try device.makeComputePipelineState(function: #require(library.makeFunction(name: "dustEffectUpdateParticle")))
    let queue = try #require(device.makeCommandQueue())
    let count = 137
    let bytes = count * 20
    let buffer = try #require(device.makeBuffer(length: bytes + 32, options: .storageModeShared))
    buffer.contents().initializeMemory(as: UInt8.self, repeating: 0xA5, count: bytes + 32)

    func run(phase: Float?) throws {
        let command = try #require(queue.makeCommandBuffer())
        let encoder = try #require(command.makeComputeCommandEncoder())
        encoder.setBuffer(buffer, offset: 0, index: 0)
        var direction: Float = 1
        if let phase {
            var resolution = SIMD2<UInt32>(UInt32(count), 1)
            var phase = phase
            var step: Float = 1.0 / 30.0
            encoder.setComputePipelineState(updater)
            encoder.setBytes(&resolution, length: 8, index: 1)
            encoder.setBytes(&phase, length: 4, index: 2)
            encoder.setBytes(&step, length: 4, index: 3)
            encoder.setBytes(&direction, length: 4, index: 4)
        } else {
            encoder.setComputePipelineState(initializer)
            encoder.setBytes(&direction, length: 4, index: 1)
        }
        encoder.dispatchThreads(MTLSize(width: count, height: 1, depth: 1),
            threadsPerThreadgroup: MTLSize(width: 32, height: 1, depth: 1))
        encoder.endEncoding()
        command.commit()
        command.waitUntilCompleted()
        #expect(command.status == .completed)
        let sentinel = buffer.contents().assumingMemoryBound(to: UInt8.self)
        #expect((bytes..<(bytes + 32)).allSatisfy { sentinel[$0] == 0xA5 })
    }
    func particles() -> [Float] {
        Array(UnsafeBufferPointer(start: buffer.contents().assumingMemoryBound(to: Float.self), count: count * 5))
    }

    try run(phase: nil)
    let initialized = particles()
    for index in 0..<count {
        let p = index * 5
        #expect(initialized[p] == 0 && initialized[p + 1] == 0)
        let speed = hypot(initialized[p + 2], initialized[p + 3])
        #expect(speed >= 42 && speed <= 84)
        #expect((Float(0.7)...Float(1.5)).contains(initialized[p + 4]))
    }
    try run(phase: 0)
    #expect(zip(particles(), initialized).allSatisfy { abs($0 - $1) < 0.00001 })
    try run(phase: 0.8)
    let advanced = particles()
    for index in 0..<count {
        let p = index * 5
        #expect(abs(advanced[p] - initialized[p + 2] / 30) < 0.00001)
        #expect(abs(advanced[p + 1] - initialized[p + 3] / 30) < 0.00001)
        #expect(abs(advanced[p + 3] - initialized[p + 3] - 4) < 0.00001)
        #expect(abs(advanced[p + 4] - initialized[p + 4] + 1.0 / 30.0) < 0.00001)
    }
}

@Test(.enabled(if: MTLCreateSystemDefaultDevice() != nil, "Requires a Metal GPU; source-hash validation still runs."))
func firstDustRenderPreservesImageOrientationAndTransparentMargins() throws {
    let device = try #require(MTLCreateSystemDefaultDevice())
    let library = try originalDustLibrary(device)
    let initializer = try device.makeComputePipelineState(function: #require(library.makeFunction(name: "dustEffectInitializeParticle")))
    let descriptor = MTLRenderPipelineDescriptor()
    let vertex = try #require(library.makeFunction(name: "dustEffectVertex"))
    let fragment = try #require(library.makeFunction(name: "dustEffectFragment"))
    descriptor.vertexFunction = vertex
    descriptor.fragmentFunction = fragment
    let attachment = try #require(descriptor.colorAttachments[0])
    attachment.pixelFormat = .bgra8Unorm
    attachment.isBlendingEnabled = true
    attachment.sourceRGBBlendFactor = .one
    attachment.sourceAlphaBlendFactor = .one
    attachment.destinationRGBBlendFactor = .oneMinusSourceAlpha
    attachment.destinationAlphaBlendFactor = .one
    let pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
    let colors: [[UInt8]] = [[255, 0, 0, 255], [0, 255, 0, 255], [0, 0, 255, 255], [128, 128, 0, 128]]
    var rgba = [UInt8]()
    for y in 0..<32 {
        for x in 0..<32 { rgba.append(contentsOf: colors[(y / 16) * 2 + x / 16]) }
    }
    let provider = try #require(CGDataProvider(data: Data(rgba) as CFData))
    let colorSpace = try #require(CGColorSpace(name: CGColorSpace.sRGB))
    let image = try #require(CGImage(width: 32, height: 32, bitsPerComponent: 8, bitsPerPixel: 32,
        bytesPerRow: 128, space: colorSpace,
        bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
        provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent))
    let source = try MTKTextureLoader(device: device).newTexture(cgImage: image, options: [.SRGB: false as NSNumber])
    let outputDescriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm, width: 64, height: 64, mipmapped: false)
    outputDescriptor.storageMode = .shared
    outputDescriptor.usage = [.renderTarget]
    let output = try #require(device.makeTexture(descriptor: outputDescriptor))
    let particles = try #require(device.makeBuffer(length: 32 * 32 * 20, options: .storageModePrivate))
    let queue = try #require(device.makeCommandQueue())
    let command = try #require(queue.makeCommandBuffer())
    let compute = try #require(command.makeComputeCommandEncoder())
    var direction: Float = 1
    compute.setComputePipelineState(initializer)
    compute.setBuffer(particles, offset: 0, index: 0)
    compute.setBytes(&direction, length: 4, index: 1)
    compute.dispatchThreads(MTLSize(width: 1024, height: 1, depth: 1),
        threadsPerThreadgroup: MTLSize(width: 32, height: 1, depth: 1))
    compute.endEncoding()
    let pass = MTLRenderPassDescriptor()
    pass.colorAttachments[0].texture = output
    pass.colorAttachments[0].loadAction = .clear
    pass.colorAttachments[0].storeAction = .store
    pass.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0)
    let render = try #require(command.makeRenderCommandEncoder(descriptor: pass))
    var rect = SIMD4<Float>(0.25, 0.25, 0.5, 0.5)
    var size = SIMD2<Float>(32, 32)
    var resolution = SIMD2<UInt32>(32, 32)
    render.setRenderPipelineState(pipeline)
    render.setVertexBytes(&rect, length: 16, index: 0)
    render.setVertexBytes(&size, length: 8, index: 1)
    render.setVertexBytes(&resolution, length: 8, index: 2)
    render.setVertexBuffer(particles, offset: 0, index: 3)
    render.setFragmentTexture(source, index: 0)
    render.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: 1024)
    render.endEncoding()
    command.commit()
    command.waitUntilCompleted()
    #expect(command.status == .completed)
    var bgra = [UInt8](repeating: 0, count: 64 * 64 * 4)
    bgra.withUnsafeMutableBytes {
        output.getBytes($0.baseAddress!, bytesPerRow: 64 * 4, from: MTLRegionMake2D(0, 0, 64, 64), mipmapLevel: 0)
    }
    for (index, position) in [(24, 24), (40, 24), (24, 40), (40, 40)].enumerated() {
        let offset = (position.1 * 64 + position.0) * 4
        let pixel = [bgra[offset + 2], bgra[offset + 1], bgra[offset], bgra[offset + 3]]
        #expect(pixel == colors[index])
    }
    #expect(bgra[(8 * 64 + 8) * 4 + 3] == 0)
    #expect(bgra[(56 * 64 + 56) * 4 + 3] == 0)
}
