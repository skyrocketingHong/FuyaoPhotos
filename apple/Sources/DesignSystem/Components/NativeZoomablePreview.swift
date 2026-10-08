import SwiftUI

#if os(iOS)
import UIKit

struct NativeZoomablePreview<Content: View>: UIViewControllerRepresentable {
    let resetVersion: Int
    @ViewBuilder let content: () -> Content
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeUIViewController(context: Context) -> NativePhotoZoomController<Content> {
        NativePhotoZoomController(preview: content())
    }

    func updateUIViewController(_ controller: NativePhotoZoomController<Content>, context: Context) {
        controller.preview = content()
        controller.resetZoom(ifNeededFor: resetVersion, animated: !reduceMotion)
    }
}

final class NativePhotoZoomController<Content: View>: UIViewController, UIScrollViewDelegate {
    private let scrollView = UIScrollView()
    private let previewController: UIHostingController<Content>
    private var lastSize: CGSize = .zero
    private var lastResetVersion = 0

    var preview: Content {
        get { previewController.rootView }
        set { previewController.rootView = newValue }
    }

    init(preview: Content) {
        previewController = UIHostingController(rootView: preview)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        scrollView.backgroundColor = .clear
        previewController.view.backgroundColor = .clear
        scrollView.minimumZoomScale = 1
        scrollView.maximumZoomScale = 8
        scrollView.bouncesZoom = true
        scrollView.delegate = self
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        NSLayoutConstraint.activate([
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.topAnchor.constraint(equalTo: view.topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        addChild(previewController)
        scrollView.addSubview(previewController.view)
        previewController.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let size = scrollView.bounds.size
        guard size.width > 0, size.height > 0, size != lastSize else { return }
        lastSize = size
        scrollView.setZoomScale(1, animated: false)
        previewController.view.frame = CGRect(origin: .zero, size: size)
        scrollView.contentSize = size
    }

    func resetZoom(ifNeededFor version: Int, animated: Bool) {
        guard version != lastResetVersion else { return }
        lastResetVersion = version
        scrollView.setZoomScale(1, animated: animated)
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { previewController.view }
}
#else
import AppKit

struct NativeZoomablePreview<Content: View>: NSViewRepresentable {
    let resetVersion: Int
    @ViewBuilder let content: () -> Content

    func makeNSView(context: Context) -> NativePhotoZoomScrollView<Content> {
        NativePhotoZoomScrollView(preview: content())
    }

    func updateNSView(_ view: NativePhotoZoomScrollView<Content>, context: Context) {
        view.preview.rootView = content()
        view.resetZoom(ifNeededFor: resetVersion)
    }
}

final class NativePhotoZoomScrollView<Content: View>: NSScrollView {
    let preview: NSHostingView<Content>
    private var lastSize: CGSize = .zero
    private var lastResetVersion = 0

    init(preview: Content) {
        self.preview = NSHostingView(rootView: preview)
        super.init(frame: .zero)
        drawsBackground = false
        contentView.drawsBackground = false
        allowsMagnification = true
        minMagnification = 1
        maxMagnification = 8
        documentView = self.preview
    }

    @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }

    override func layout() {
        super.layout()
        let size = contentView.frame.size
        guard size.width > 0, size.height > 0, size != lastSize else { return }
        lastSize = size
        magnification = 1
        preview.frame = CGRect(origin: .zero, size: size)
    }

    func resetZoom(ifNeededFor version: Int) {
        guard version != lastResetVersion else { return }
        lastResetVersion = version
        magnification = 1
        contentView.scroll(to: .zero)
        reflectScrolledClipView(contentView)
    }
}
#endif
