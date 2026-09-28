import SwiftUI

#if os(iOS)
import UIKit

struct NativeColorPhotoViewport: UIViewRepresentable {
    let image: CGImage
    let hdr: Bool
    let point: CGPoint
    let onSample: (CGPoint) -> Void
    let onLoupe: (ColorLoupeContact?) -> Void
    func makeUIView(context: Context) -> ColorPhotoScrollView { ColorPhotoScrollView() }
    func updateUIView(_ view: ColorPhotoScrollView, context: Context) {
        view.onSample = onSample
        view.onLoupe = onLoupe
        view.photo.preferredImageDynamicRange = hdr ? .high : .standard
        if view.source !== image {
            view.source = image
            view.photo.image = UIImage(cgImage: image)
            view.scroll.setZoomScale(1, animated: false)
            view.setNeedsLayout()
        }
        view.point = point
        view.updateMarker()
    }
}

final class ColorPhotoScrollView: UIView, UIScrollViewDelegate, UIGestureRecognizerDelegate {
    let scroll = UIScrollView()
    let photo = UIImageView()
    var source: CGImage? { didSet { previousSize = .zero } }
    var point = CGPoint(x: 0.5, y: 0.5)
    var onSample: ((CGPoint) -> Void)?
    var onLoupe: ((ColorLoupeContact?) -> Void)?
    private var samplingPress: UILongPressGestureRecognizer?
    private var panning = false
    private var zooming = false
    private let marker = CAShapeLayer()
    private var previousSize = CGSize.zero

    init() {
        super.init(frame: .zero)
        scroll.delegate = self
        scroll.maximumZoomScale = 8
        scroll.minimumZoomScale = 1
        scroll.panGestureRecognizer.minimumNumberOfTouches = 2
        scroll.backgroundColor = .clear
        photo.backgroundColor = .clear
        photo.isUserInteractionEnabled = true
        photo.contentMode = .scaleAspectFit
        scroll.addSubview(photo)
        addSubview(scroll)
        marker.fillColor = UIColor.clear.cgColor
        marker.strokeColor = UIColor.white.cgColor
        marker.shadowColor = UIColor.black.cgColor
        marker.shadowOpacity = 1
        marker.shadowRadius = 1
        marker.shadowOffset = .zero
        marker.actions = ["path": NSNull(), "lineWidth": NSNull()]
        photo.layer.addSublayer(marker)
        let sample = UILongPressGestureRecognizer(target: self, action: #selector(sampleGesture(_:)))
        sample.minimumPressDuration = 0
        sample.allowableMovement = 10
        sample.delegate = self
        samplingPress = sample
        scroll.addGestureRecognizer(sample)
        accessibilityLabel = String.localized("colors.photo")
        accessibilityHint = String.localized("colors.gesture")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() {
        super.layoutSubviews()
        scroll.frame = bounds
        guard let source else { return }
        if previousSize != bounds.size || photo.bounds.isEmpty {
            previousSize = bounds.size
            scroll.setZoomScale(1, animated: false)
            let scale = min(bounds.width / CGFloat(source.width), bounds.height / CGFloat(source.height))
            photo.frame = CGRect(x: 0, y: 0, width: CGFloat(source.width) * scale, height: CGFloat(source.height) * scale)
            scroll.contentSize = photo.frame.size
        }
        centerPhoto()
        updateMarker()
    }
    func viewForZooming(in scrollView: UIScrollView) -> UIView? { photo }
    func scrollViewDidZoom(_ scrollView: UIScrollView) { centerPhoto(); updateMarker() }
    private func centerPhoto() {
        scroll.contentInset = UIEdgeInsets(top: max(0, (bounds.height - photo.frame.height) / 2), left: max(0, (bounds.width - photo.frame.width) / 2), bottom: 0, right: 0)
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        other === scroll.pinchGestureRecognizer || other === scroll.panGestureRecognizer
    }
    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        gestureRecognizer.numberOfTouches == 1 && photo.bounds.contains(gestureRecognizer.location(in: photo))
    }
    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        panning = true
        samplingPress?.isEnabled = false
        onLoupe?(nil)
    }
    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        panning = false
        samplingPress?.isEnabled = !zooming
    }
    func scrollViewWillBeginZooming(_ scrollView: UIScrollView, with view: UIView?) {
        zooming = true
        samplingPress?.isEnabled = false
        onLoupe?(nil)
    }
    func scrollViewDidEndZooming(_ scrollView: UIScrollView, with view: UIView?, atScale scale: CGFloat) {
        zooming = false
        samplingPress?.isEnabled = !panning
    }
    @objc private func sampleGesture(_ gesture: UIGestureRecognizer) {
        guard (gesture.state == .began || gesture.state == .changed), gesture.numberOfTouches == 1 else {
            onLoupe?(nil)
            return
        }
        guard photo.bounds.width > 0, photo.bounds.height > 0 else { return }
        let location = gesture.location(in: photo)
        let normalized = CGPoint(x: min(1, max(0, location.x / photo.bounds.width)), y: min(1, max(0, location.y / photo.bounds.height)))
        onSample?(normalized)
        onLoupe?(ColorLoupeContact(location: gesture.location(in: self), normalizedPoint: normalized,
            imageSize: CGSize(width: photo.bounds.width * scroll.zoomScale, height: photo.bounds.height * scroll.zoomScale)))
    }
    func updateMarker() {
        let p = CGPoint(x: point.x * photo.bounds.width, y: point.y * photo.bounds.height)
        let radius = 8 / scroll.zoomScale
        marker.lineWidth = 2 / scroll.zoomScale
        marker.path = CGPath(ellipseIn: CGRect(x: p.x - radius, y: p.y - radius, width: radius * 2, height: radius * 2), transform: nil)
    }
}
#else
import AppKit

struct NativeColorPhotoViewport: NSViewRepresentable {
    let image: CGImage
    let hdr: Bool
    let point: CGPoint
    let onSample: (CGPoint) -> Void
    let onLoupe: (ColorLoupeContact?) -> Void
    func makeNSView(context: Context) -> ColorPhotoScrollView { ColorPhotoScrollView() }
    func updateNSView(_ view: ColorPhotoScrollView, context: Context) {
        view.photo.onSample = onSample
        view.photo.onLoupe = { [weak view] location in
            guard let view, let location else { onLoupe(nil); return }
            let rect = view.photo.imageRect
            let displayed = view.photo.convert(rect, to: view)
            let finger = view.photo.convert(location, to: view)
            onLoupe(ColorLoupeContact(location: CGPoint(x: finger.x, y: view.bounds.height - finger.y),
                normalizedPoint: CGPoint(x: min(1, max(0, (location.x - rect.minX) / rect.width)),
                                         y: min(1, max(0, 1 - (location.y - rect.minY) / rect.height))),
                imageSize: displayed.size))
        }
        view.photo.point = point
        view.photo.preferredImageDynamicRange = hdr ? .high : .standard
        if view.source !== image {
            view.source = image
            view.photo.image = NSImage(cgImage: image, size: .zero)
            view.magnification = 1
            view.needsLayout = true
        }
        view.photo.needsDisplay = true
    }
}

final class ColorPhotoScrollView: NSScrollView {
    let photo = SamplingNSImageView()
    var source: CGImage? { didSet { previousSize = .zero } }
    private var previousSize = CGSize.zero
    init() {
        super.init(frame: .zero)
        drawsBackground = false
        allowsMagnification = true
        minMagnification = 1
        maxMagnification = 8
        photo.imageScaling = .scaleProportionallyUpOrDown
        photo.setAccessibilityLabel(.localized("colors.photo"))
        photo.setAccessibilityHelp(.localized("colors.gesture"))
        documentView = photo
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layout() {
        super.layout()
        guard source != nil else { return }
        if previousSize != bounds.size || photo.bounds.isEmpty {
            previousSize = bounds.size
            magnification = 1
            photo.frame = CGRect(origin: .zero, size: bounds.size)
        }
    }
}

final class SamplingNSImageView: NSImageView {
    var point = CGPoint(x: 0.5, y: 0.5)
    var onSample: ((CGPoint) -> Void)?
    var onLoupe: ((CGPoint?) -> Void)?
    private var holding = false
    var imageRect: CGRect {
        guard let image, image.size.width > 0, image.size.height > 0 else { return bounds }
        let scale = min(bounds.width / image.size.width, bounds.height / image.size.height)
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        return CGRect(x: (bounds.width - size.width) / 2, y: (bounds.height - size.height) / 2, width: size.width, height: size.height)
    }
    override func mouseDown(with event: NSEvent) {
        let pointer = convert(event.locationInWindow, from: nil)
        guard imageRect.contains(pointer) else { return }
        holding = true
        sample(pointer)
        onLoupe?(pointer)
    }
    override func mouseDragged(with event: NSEvent) {
        guard holding else { return }
        let pointer = convert(event.locationInWindow, from: nil)
        sample(pointer)
        onLoupe?(pointer)
    }
    override func mouseUp(with event: NSEvent) {
        guard holding else { return }
        sample(convert(event.locationInWindow, from: nil))
        holding = false
        onLoupe?(nil)
    }
    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        if window == nil { holding = false; onLoupe?(nil) }
    }
    private func sample(_ p: CGPoint) {
        let rect = imageRect
        guard rect.width > 0, rect.height > 0 else { return }
        onSample?(CGPoint(x: min(1, max(0, (p.x - rect.minX) / rect.width)),
                          y: min(1, max(0, 1 - (p.y - rect.minY) / rect.height))))
    }
    override func draw(_ dirtyRect: NSRect) {
        super.draw(dirtyRect)
        let rect = imageRect
        let p = CGPoint(x: rect.minX + point.x * rect.width, y: rect.maxY - point.y * rect.height)
        let path = NSBezierPath(ovalIn: CGRect(x: p.x - 7, y: p.y - 7, width: 14, height: 14))
        NSColor.black.setStroke(); path.lineWidth = 3; path.stroke()
        NSColor.white.setStroke(); path.lineWidth = 1.5; path.stroke()
    }
}
#endif
