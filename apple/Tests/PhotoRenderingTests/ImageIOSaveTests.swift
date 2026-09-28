import Testing
import Foundation
import CoreImage
import ImageIO
@testable import PhotoRenderingCore

struct ImageIOSaveTests {
    @Test(arguments: [CardExportFormat.jpeg, .heic])
    func fallbackRetainsHdrMatteAndLiveIdentifier(_ format: CardExportFormat) async throws {
        let folder=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
        defer { try? FileManager.default.removeItem(at:folder) }
        let input=folder.appendingPathComponent("source.heic"), output=folder.appendingPathComponent("result.\(format.fileExtension)")
        let bounds=CGRect(x:0,y:0,width:256,height:192)
        let base=CIImage(color:CIColor(red:0.4,green:0.3,blue:0.5)).cropped(to:bounds)
        let hdr=base.applyingFilter("CIExposureAdjust",parameters:[kCIInputEVKey:2]).settingContentHeadroom(4)
        let matte=CIImage(color:.white).cropped(to:CGRect(x:0,y:0,width:64,height:48))
        try CIContext().writeHEIFRepresentation(of:base,to:input,format:.RGBA8,
            colorSpace:CGColorSpace(name:CGColorSpace.displayP3)!,options:[.hdrImage:hdr,.portraitEffectsMatteImage:matte])
        let source=try #require(CGImageSourceCreateWithURL(input as CFURL,nil))
        let pair=UUID().uuidString
        let metadata:[String:Any]=[kCGImagePropertyMakerAppleDictionary as String:["17":pair]]
        var options=CardSaveOptions();options.format=format
        try await CardImageProcessor.shared.writeImageIO(base:base,hdrImage:hdr,metadata:metadata,source:source,
            orientation:.up,options:options,to:output)
        let result=try #require(CGImageSourceCreateWithURL(output as CFURL,nil))
        #expect(CGImageSourceCopyAuxiliaryDataInfoAtIndex(result,0,kCGImageAuxiliaryDataTypeISOGainMap) != nil)
        #expect(CGImageSourceCopyAuxiliaryDataInfoAtIndex(result,0,kCGImageAuxiliaryDataTypePortraitEffectsMatte) != nil)
        let properties=CGImageSourceCopyPropertiesAtIndex(result,0,nil) as? [String:Any]
        #expect((properties?[kCGImagePropertyMakerAppleDictionary as String] as? [String:Any])?["17"] as? String == pair)
    }

    /// The saved file's SDR base is what every SDR thumbnail shows; it must keep the source
    /// photo's base brightness instead of the encoder's own darker re-derivation.
    @Test(arguments: [CardExportFormat.jpeg, .heic])
    func pairEncodeKeepsSdrBaseBrightness(_ format: CardExportFormat) async throws {
        let folder=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
        defer { try? FileManager.default.removeItem(at:folder) }
        let input=folder.appendingPathComponent("source.heic"), output=folder.appendingPathComponent("result.\(format.fileExtension)")
        let bounds=CGRect(x:0,y:0,width:256,height:192)
        let base=CIImage(color:CIColor(red:0.4,green:0.3,blue:0.5)).cropped(to:bounds)
        let hdr=base.applyingFilter("CIExposureAdjust",parameters:[kCIInputEVKey:2]).settingContentHeadroom(4)
        let matte=CIImage(color:.white).cropped(to:CGRect(x:0,y:0,width:64,height:48))
        try CIContext().writeHEIFRepresentation(of:base,to:input,format:.RGBA8,
            colorSpace:CGColorSpace(name:CGColorSpace.displayP3)!,options:[.hdrImage:hdr,.portraitEffectsMatteImage:matte])
        let source=try #require(CGImageSourceCreateWithURL(input as CFURL,nil))
        let storedBase=try #require(CIImage(contentsOf:input,options:[.applyOrientationProperty:true,.expandToHDR:false,.toneMapHDRtoSDR:true]))
        let expanded=try #require(CIImage(contentsOf:input,options:[.applyOrientationProperty:true,.expandToHDR:true]))
        var options=CardSaveOptions();options.format=format
        try await CardImageProcessor.shared.writeImageIO(base:storedBase,hdrImage:expanded,metadata:[:],
            source:source,orientation:.up,options:options,to:output)
        let savedBase=try #require(CIImage(contentsOf:output,options:[.applyOrientationProperty:true,.expandToHDR:false,.toneMapHDRtoSDR:true]))
        let reference=try meanLinearRGB(storedBase)
        let saved=try meanLinearRGB(savedBase)
        #expect(abs(saved-reference)/max(reference,0.001) < 0.05,
               "SDR base changed: reference \(reference) saved \(saved)")
        let result=try #require(CGImageSourceCreateWithURL(output as CFURL,nil))
        #expect(CGImageSourceCopyAuxiliaryDataInfoAtIndex(result,0,kCGImageAuxiliaryDataTypeISOGainMap) != nil)
    }

    private func meanLinearRGB(_ image: CIImage) throws -> Double {
        let context=CIContext(options:[.cacheIntermediates:false])
        let width=32,height=24
        let scaled=image.transformed(by:CGAffineTransform(scaleX:CGFloat(width)/image.extent.width,
            y:CGFloat(height)/image.extent.height))
        var data=Data(count:width*height*4)
        var sum=0.0
        try data.withUnsafeMutableBytes { pointer in
            guard let address=pointer.baseAddress else { throw CardError.invalidImage }
            context.render(scaled,toBitmap:address,rowBytes:width*4,bounds:scaled.extent,
                format:.RGBA8,colorSpace:CGColorSpace(name:CGColorSpace.extendedLinearDisplayP3)!)
            let bytes=pointer.bindMemory(to:UInt8.self)
            for index in 0..<(width*height) {
                sum += Double(bytes[index*4])+Double(bytes[index*4+1])+Double(bytes[index*4+2])
            }
        }
        return sum/Double(width*height*3)
    }
}
