import Foundation
import Testing
import Metal
import MetalKit
import CoreGraphics
import PhotoMotionCore

struct PhotoReassemblyTests {
    @Test func largeAndNarrowViewportsStayWithinTheParticleBudget() throws {
        for (source, target) in [
            (CGRect(x: 0, y: 0, width: 1080, height: 810), CGRect(x: 20, y: 60, width: 350, height: 210)),
            (CGRect(x: 0, y: 0, width: 8192, height: 5462), CGRect(x: 64, y: 100, width: 1248, height: 840)),
            (CGRect(x: 0, y: 0, width: 1, height: 100_000), CGRect(x: 3, y: 8, width: 12, height: 40)),
            (CGRect(x: 0, y: 0, width: 100_000, height: 1), CGRect(x: 3, y: 8, width: 40, height: 12)),
        ] {
            let grid = try #require(PhotoReassemblyGeometry.grid(source: source, target: target))
            #expect(grid.columns > 0 && grid.rows > 0)
            #expect(grid.count <= PhotoMotionTokens.reassemblyMaxParticles)
            for progress: Float in [0, 0.22, 0.5, 0.88, 1] {
                for index in 0..<grid.count {
                    let particle = try #require(PhotoReassemblyGeometry.particle(index: index, grid: grid,
                        source: source, target: target, progress: progress))
                    #expect(particle.center.x.isFinite && particle.center.y.isFinite)
                    #expect(particle.size.x.isFinite && particle.size.y.isFinite)
                    #expect(particle.size.x > 0 && particle.size.y > 0)
                    #expect((0...1).contains(particle.colorFraction))
                }
            }
        }
    }

    @Test func endpointsTileTheirActualFramesAndRejectInvalidGeometry() throws {
        let source = CGRect(x: -12, y: 8, width: 1023, height: 711)
        let target = CGRect(x: 24, y: 70, width: 358, height: 228)
        let grid = try #require(PhotoReassemblyGeometry.grid(source: source, target: target))
        for (progress, frame): (Float, CGRect) in [(0, source), (1, target)] {
            let first = try #require(PhotoReassemblyGeometry.particle(index: 0, grid: grid,
                source: source, target: target, progress: progress))
            let last = try #require(PhotoReassemblyGeometry.particle(index: grid.count - 1, grid: grid,
                source: source, target: target, progress: progress))
            #expect(abs(first.center.x - first.size.x / 2 - Float(frame.minX)) < 0.001)
            #expect(abs(first.center.y - first.size.y / 2 - Float(frame.minY)) < 0.001)
            #expect(abs(last.center.x + last.size.x / 2 - Float(frame.maxX)) < 0.001)
            #expect(abs(last.center.y + last.size.y / 2 - Float(frame.maxY)) < 0.001)
            #expect(first.colorFraction == progress && last.colorFraction == progress)
        }
        #expect(PhotoReassemblyGeometry.grid(source: .zero, target: target) == nil)
        #expect(PhotoReassemblyGeometry.grid(source: .infinite, target: target) == nil)
        #expect(PhotoReassemblyGeometry.particle(index: grid.count, grid: grid,
            source: source, target: target, progress: 0.5) == nil)
        #expect(PhotoReassemblyGeometry.particle(index: -1, grid: grid,
            source: source, target: target, progress: 0.5) == nil)
        #expect(PhotoReassemblyGeometry.particle(index: 0, grid: grid,
            source: source, target: target, progress: .nan) == nil)
    }

    @Test func gpuUniformsKeepTheirFloat4Layout() {
        #expect(MemoryLayout<PhotoReassemblyUniforms>.size == 80)
        #expect(MemoryLayout<PhotoReassemblyUniforms>.stride == 80)
        #expect(MemoryLayout<PhotoReassemblyUniforms>.offset(of: \.sourceRect) == 0)
        #expect(MemoryLayout<PhotoReassemblyUniforms>.offset(of: \.targetRect) == 16)
        #expect(MemoryLayout<PhotoReassemblyUniforms>.offset(of: \.canvasAndGrid) == 32)
        #expect(MemoryLayout<PhotoReassemblyUniforms>.offset(of: \.animation) == 48)
        #expect(MemoryLayout<PhotoReassemblyUniforms>.offset(of: \.colorAndPadding) == 64)
    }

    @Test(.enabled(if: MTLCreateSystemDefaultDevice() != nil, "Requires a Metal GPU; geometry tests still run."))
    func gpuFramesPreserveSourceAndTargetPixelsAcrossAspectRatios() throws {
        let renderer = try ReassemblyTestRenderer()
        let source = try Fixture(width: 32, height: 24, target: false)
        let target = try Fixture(width: 20, height: 36, target: true)
        let sourceFrame = CGRect(x: 4, y: 8, width: 32, height: 24)
        let targetFrame = CGRect(x: 56, y: 20, width: 20, height: 36)
        let canvas = CGSize(width: 84, height: 64)
        let start = try renderer.render(source: source.image, target: target.image,
            sourceFrame: sourceFrame, targetFrame: targetFrame, canvas: canvas, progress: 0)
        let finish = try renderer.render(source: source.image, target: target.image,
            sourceFrame: sourceFrame, targetFrame: targetFrame, canvas: canvas, progress: 1)
        #expect(pixelMismatches(start, fixture: source, frame: sourceFrame, canvas: canvas) == 0)
        #expect(pixelMismatches(finish, fixture: target, frame: targetFrame, canvas: canvas) == 0)

        let tallerCanvas = CGSize(width: canvas.width, height: canvas.height + 28)
        let resizedStart = try renderer.render(source: source.image, target: target.image,
            sourceFrame: sourceFrame, targetFrame: targetFrame, canvas: tallerCanvas, progress: 0)
        let resizedFinish = try renderer.render(source: source.image, target: target.image,
            sourceFrame: sourceFrame, targetFrame: targetFrame, canvas: tallerCanvas, progress: 1)
        #expect(pixelMismatches(resizedStart, fixture: source, frame: sourceFrame, canvas: tallerCanvas) == 0)
        #expect(pixelMismatches(resizedFinish, fixture: target, frame: targetFrame, canvas: tallerCanvas) == 0)

        let middle = try renderer.render(source: source.image, target: target.image,
            sourceFrame: sourceFrame, targetFrame: targetFrame, canvas: canvas, progress: 0.57)
        #expect(middle != start && middle != finish)
        #expect(stride(from: 3, to: middle.count, by: 4).contains { middle[$0] > 0 })
        let invalidPremultiplication = stride(from: 0, to: middle.count, by: 4).contains { offset in
            let alpha = Int(middle[offset + 3])
            return (0..<3).contains { Int(middle[offset + $0]) > alpha + 1 }
        }
        #expect(!invalidPremultiplication)
    }
}

private struct Fixture {
    let width: Int
    let height: Int
    let rgba: [UInt8]
    let image: CGImage

    init(width: Int, height: Int, target: Bool) throws {
        self.width = width
        self.height = height
        let colors: [[UInt8]] = target
            ? [[0, 128, 128, 128], [255, 0, 255, 255], [255, 255, 0, 255], [0, 0, 96, 96]]
            : [[255, 0, 0, 255], [0, 255, 0, 255], [0, 0, 255, 255], [128, 128, 0, 128]]
        var pixels: [UInt8] = []
        for y in 0..<height {
            for x in 0..<width {
                if target && ((x < 3 && y < 3) || (x >= width - 3 && y >= height - 3)) {
                    pixels.append(contentsOf: [0, 0, 0, 0])
                } else {
                    pixels.append(contentsOf: colors[(y >= height / 2 ? 2 : 0) + (x >= width / 2 ? 1 : 0)])
                }
            }
        }
        rgba = pixels
        let provider = try #require(CGDataProvider(data: Data(pixels) as CFData))
        let space = try #require(CGColorSpace(name: CGColorSpace.sRGB))
        image = try #require(CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
            bytesPerRow: width * 4, space: space,
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent))
    }
}

private func pixelMismatches(_ actual: [UInt8], fixture: Fixture, frame: CGRect, canvas: CGSize) -> Int {
    var mismatches = 0
    for y in 0..<Int(canvas.height) {
        for x in 0..<Int(canvas.width) {
            let localX = x - Int(frame.minX)
            let localY = y - Int(frame.minY)
            let expected = (0..<fixture.width).contains(localX) && (0..<fixture.height).contains(localY)
                ? Array(fixture.rgba[((localY * fixture.width + localX) * 4)..<((localY * fixture.width + localX) * 4 + 4)])
                : [UInt8](repeating: 0, count: 4)
            let offset = (y * Int(canvas.width) + x) * 4
            if (0..<4).contains(where: { abs(Int(actual[offset + $0]) - Int(expected[$0])) > 1 }) { mismatches += 1 }
        }
    }
    return mismatches
}

private final class ReassemblyTestRenderer {
    let device: MTLDevice
    let queue: MTLCommandQueue
    let pipeline: MTLRenderPipelineState

    init() throws {
        device = try #require(MTLCreateSystemDefaultDevice())
        queue = try #require(device.makeCommandQueue())
        let sourceURL = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Sources/DesignSystem/Motion/Dust/PhotoReassemblyShaders.metal")
        let library = try device.makeLibrary(source: String(contentsOf: sourceURL, encoding: .utf8), options: nil)
        let descriptor = MTLRenderPipelineDescriptor()
        let vertex = try #require(library.makeFunction(name: "photoReassemblyVertex"))
        let fragment = try #require(library.makeFunction(name: "photoReassemblyFragment"))
        descriptor.vertexFunction = vertex
        descriptor.fragmentFunction = fragment
        let attachment = try #require(descriptor.colorAttachments[0])
        attachment.pixelFormat = .bgra8Unorm
        attachment.isBlendingEnabled = true
        attachment.sourceRGBBlendFactor = .one
        attachment.sourceAlphaBlendFactor = .one
        attachment.destinationRGBBlendFactor = .oneMinusSourceAlpha
        attachment.destinationAlphaBlendFactor = .oneMinusSourceAlpha
        pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
    }

    func render(source: CGImage, target: CGImage, sourceFrame: CGRect, targetFrame: CGRect,
                canvas: CGSize, progress: Float) throws -> [UInt8] {
        let grid = try #require(PhotoReassemblyGeometry.grid(source: sourceFrame, target: targetFrame))
        let loader = MTKTextureLoader(device: device)
        let sourceTexture = try loader.newTexture(cgImage: source, options: [.SRGB: false as NSNumber])
        let targetTexture = try loader.newTexture(cgImage: target, options: [.SRGB: false as NSNumber])
        let outputDescriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm,
            width: Int(canvas.width), height: Int(canvas.height), mipmapped: false)
        outputDescriptor.storageMode = .shared
        outputDescriptor.usage = .renderTarget
        let output = try #require(device.makeTexture(descriptor: outputDescriptor))
        let pass = MTLRenderPassDescriptor()
        pass.colorAttachments[0].texture = output
        pass.colorAttachments[0].loadAction = .clear
        pass.colorAttachments[0].storeAction = .store
        pass.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0)
        let command = try #require(queue.makeCommandBuffer())
        let encoder = try #require(command.makeRenderCommandEncoder(descriptor: pass))
        var uniforms = PhotoReassemblyUniforms(source: sourceFrame, target: targetFrame,
            canvas: canvas, grid: grid, progress: progress)
        encoder.setRenderPipelineState(pipeline)
        encoder.setVertexBytes(&uniforms, length: MemoryLayout<PhotoReassemblyUniforms>.stride, index: 0)
        encoder.setFragmentTexture(sourceTexture, index: 0)
        encoder.setFragmentTexture(targetTexture, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: grid.count)
        encoder.endEncoding()
        command.commit()
        command.waitUntilCompleted()
        #expect(command.status == .completed)
        var bgra = [UInt8](repeating: 0, count: Int(canvas.width * canvas.height) * 4)
        bgra.withUnsafeMutableBytes { bytes in
            output.getBytes(bytes.baseAddress!, bytesPerRow: Int(canvas.width) * 4,
                from: MTLRegionMake2D(0, 0, Int(canvas.width), Int(canvas.height)), mipmapLevel: 0)
        }
        for offset in stride(from: 0, to: bgra.count, by: 4) { bgra.swapAt(offset, offset + 2) }
        return bgra
    }
}
