#if os(iOS)
import SwiftUI
import UIKit
import ImageIO
import UniformTypeIdentifiers

struct ColorCameraCapture: UIViewControllerRepresentable {
    let completion: (Result<URL?, Error>) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(completion: completion) }
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.mediaTypes = [UTType.image.identifier]
        picker.cameraCaptureMode = .photo
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) { }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let completion: (Result<URL?, Error>) -> Void
        init(completion: @escaping (Result<URL?, Error>) -> Void) { self.completion = completion }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { completion(.success(nil)) }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            guard let image = info[.originalImage] as? UIImage, let pixels = image.cgImage else {
                completion(.failure(CardError.invalidImage)); return
            }
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("FuyaoCapture-\(UUID().uuidString).jpg")
            guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else {
                completion(.failure(CardError.imageEncoding)); return
            }
            var metadata = info[.mediaMetadata] as? [String: Any] ?? [:]
            let orientations: [UInt32] = [1, 3, 8, 6, 2, 4, 5, 7]
            metadata[kCGImagePropertyOrientation as String] = orientations[image.imageOrientation.rawValue]
            metadata[kCGImageDestinationLossyCompressionQuality as String] = 1.0
            CGImageDestinationAddImage(destination, pixels, metadata as CFDictionary)
            if CGImageDestinationFinalize(destination) { completion(.success(url)) }
            else { try? FileManager.default.removeItem(at: url); completion(.failure(CardError.imageEncoding)) }
        }
    }
}
#endif
