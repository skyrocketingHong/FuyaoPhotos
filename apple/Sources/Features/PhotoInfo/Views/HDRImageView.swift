import SwiftUI

#if os(macOS)
import AppKit
struct HDRImageView: NSViewRepresentable {
    let image: CGImage
    let enabled: Bool
    func makeNSView(context: Context) -> NSImageView {
        let view = NSImageView()
        view.imageScaling = .scaleProportionallyUpOrDown
        return view
    }
    func updateNSView(_ view: NSImageView, context: Context) {
        view.preferredImageDynamicRange = enabled ? .high : .standard
        view.image = NSImage(cgImage: image, size: .zero)
    }
    func sizeThatFits(_ proposal: ProposedViewSize, nsView: NSImageView, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? 1, height: proposal.height ?? 1)
    }
}
#else
import UIKit
struct HDRImageView: UIViewRepresentable {
    let image: CGImage
    let enabled: Bool
    func makeUIView(context: Context) -> UIImageView {
        let view = UIImageView()
        view.contentMode = .scaleAspectFit
        view.clipsToBounds = true
        view.setContentHuggingPriority(.defaultLow, for: .horizontal)
        view.setContentHuggingPriority(.defaultLow, for: .vertical)
        return view
    }
    func updateUIView(_ view: UIImageView, context: Context) {
        // UIKit negotiates EDR headroom with this view's display, including system tone mapping.
        view.preferredImageDynamicRange = enabled ? .high : .standard
        view.image = UIImage(cgImage: image)
    }
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UIImageView, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? 1, height: proposal.height ?? 1)
    }
}
#endif
