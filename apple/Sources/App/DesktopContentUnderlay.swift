#if os(macOS)
import AppKit
import SwiftUI

/// SwiftUI has no macOS modifier for NSSplitViewItem's content-under-sidebar behavior.
struct DesktopContentUnderlay: NSViewControllerRepresentable {
    func makeNSViewController(context: Context) -> Controller { Controller() }
    func updateNSViewController(_ controller: Controller, context: Context) { controller.configure() }
    static func dismantleNSViewController(_ controller: Controller, coordinator: ()) { controller.restore() }

    final class Controller: NSViewController {
        private weak var configuredItem: NSSplitViewItem?
        private var previousAdjustment = false

        override func loadView() { view = NSView() }
        override func viewDidAppear() { super.viewDidAppear(); configure() }
        override func viewDidLayout() { super.viewDidLayout(); configure() }

        func configure() {
            var child: NSViewController = self
            while let parent = child.parent {
                if let split = parent as? NSSplitViewController,
                   let item = split.splitViewItems.first(where: { $0.viewController === child }),
                   item.behavior != .sidebar {
                    guard configuredItem !== item else { return }
                    restore()
                    previousAdjustment = item.automaticallyAdjustsSafeAreaInsets
                    configuredItem = item
                    item.automaticallyAdjustsSafeAreaInsets = true
                    return
                }
                child = parent
            }
        }

        func restore() {
            configuredItem?.automaticallyAdjustsSafeAreaInsets = previousAdjustment
            configuredItem = nil
        }
    }
}
#endif
