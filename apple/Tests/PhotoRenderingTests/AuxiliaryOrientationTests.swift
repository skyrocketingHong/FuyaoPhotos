import Testing
import Foundation
import ImageIO
import CoreVideo
@testable import PhotoRenderingCore

struct AuxiliaryOrientationTests {
    @Test func rawMattePlanesHonorAllExifOrientationsAndStride() throws {
        let description: [String: Any] = ["Width": 3, "Height": 2, "BytesPerRow": 4,
            "PixelFormat": kCVPixelFormatType_OneComponent8]
        let input: [AnyHashable: Any] = [
            kCGImageAuxiliaryDataInfoData: Data([1, 2, 3, 0, 4, 5, 6, 0]),
            kCGImageAuxiliaryDataInfoDataDescription: description
        ]
        let expected: [[UInt8]] = [[1,2,3,4,5,6], [3,2,1,6,5,4], [6,5,4,3,2,1], [4,5,6,1,2,3],
                                   [1,4,2,5,3,6], [4,1,5,2,6,3], [6,3,5,2,4,1], [3,6,2,5,1,4]]
        for raw in 1...8 {
            let orientation = try #require(CGImagePropertyOrientation(rawValue: UInt32(raw)))
            let result = try #require(PhotoAuxiliaryData.orientedMonochrome(input, orientation: orientation))
            #expect(result[kCGImageAuxiliaryDataInfoData] as? Data == Data(expected[raw - 1]))
            let description = try #require(result[kCGImageAuxiliaryDataInfoDataDescription] as? [String: Any])
            #expect(description["Width"] as? Int == (raw >= 5 ? 2 : 3))
        }
    }
}
