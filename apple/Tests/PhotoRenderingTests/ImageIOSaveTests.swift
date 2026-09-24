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
        try await CardImageProcessor.shared.writeImageIO(hdr,metadata:metadata,source:source,orientation:.up,
            options:options,hdr:true,to:output)
        let result=try #require(CGImageSourceCreateWithURL(output as CFURL,nil))
        #expect(CGImageSourceCopyAuxiliaryDataInfoAtIndex(result,0,kCGImageAuxiliaryDataTypeISOGainMap) != nil)
        #expect(CGImageSourceCopyAuxiliaryDataInfoAtIndex(result,0,kCGImageAuxiliaryDataTypePortraitEffectsMatte) != nil)
        let properties=CGImageSourceCopyPropertiesAtIndex(result,0,nil) as? [String:Any]
        #expect((properties?[kCGImagePropertyMakerAppleDictionary as String] as? [String:Any])?["17"] as? String == pair)
    }
}
