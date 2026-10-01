import SwiftUI
import PhotosUI
import Photos

#if os(macOS)
struct NativePhotoPicker: NSViewControllerRepresentable {
    let completion: ([PHPickerResult]) -> Void
    func makeCoordinator() -> PhotoPickerDelegate { PhotoPickerDelegate(completion: completion) }
    func makeNSViewController(context: Context) -> PHPickerViewController {
        let controller = context.coordinator.controller()
        controller.preferredContentSize = CGSize(width: 960, height: 720)
        return controller
    }
    func updateNSViewController(_ controller: PHPickerViewController, context: Context) {}
}
#else
struct NativePhotoPicker: UIViewControllerRepresentable {
    let completion: ([PHPickerResult]) -> Void
    func makeCoordinator() -> PhotoPickerDelegate { PhotoPickerDelegate(completion: completion) }
    func makeUIViewController(context: Context) -> PHPickerViewController { context.coordinator.controller() }
    func updateUIViewController(_ controller: PHPickerViewController, context: Context) {}
}
#endif

extension View {
    func photoPickerPresentation() -> some View {
#if os(macOS)
        frame(minWidth: 720, idealWidth: 960, maxWidth: .infinity,
              minHeight: 560, idealHeight: 720, maxHeight: .infinity)
            .presentationSizing(.fitted)
#else
        presentationSizing(.page)
            .presentationDetents([.large])
#endif
    }
}

@MainActor final class PhotoPickerDelegate: NSObject, PHPickerViewControllerDelegate {
    private let completion: ([PHPickerResult]) -> Void
    init(completion: @escaping ([PHPickerResult]) -> Void) { self.completion = completion }
    func controller() -> PHPickerViewController {
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = CardSession.selectionLimit
        configuration.selection = .ordered
        configuration.preferredAssetRepresentationMode = .current
        let controller = PHPickerViewController(configuration: configuration)
        controller.delegate = self
        return controller
    }
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) { completion(results) }
}
