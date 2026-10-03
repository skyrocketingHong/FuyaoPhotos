#if os(macOS)
import AppKit
import SwiftUI

/// Apply the compact style after the Settings scene attaches its preference toolbar.
struct SettingsWindowChrome: NSViewControllerRepresentable {
    func makeNSViewController(context: Context) -> Controller { Controller() }
    func updateNSViewController(_ controller: Controller, context: Context) { controller.configure() }
    static func dismantleNSViewController(_ controller: Controller, coordinator: ()) { controller.restore() }

    final class Controller: NSViewController {
        private weak var configuredWindow: NSWindow?
        private var previousStyle: NSWindow.ToolbarStyle = .automatic

        override func loadView() { view = NSView() }
        override func viewDidAppear() { super.viewDidAppear(); configure() }
        override func viewDidLayout() { super.viewDidLayout(); configure() }

        func configure() {
            guard let window = view.window else { return }
            if configuredWindow !== window {
                restore()
                previousStyle = window.toolbarStyle
                configuredWindow = window
            }
            if window.toolbarStyle != .unifiedCompact {
                window.toolbarStyle = .unifiedCompact
            }
        }

        func restore() {
            configuredWindow?.toolbarStyle = previousStyle
            configuredWindow = nil
        }
    }
}
#endif
