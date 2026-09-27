import SwiftUI

#if os(iOS)
import UIKit

/// Keep the system bar and material; set only the requested selected foreground.
struct NativeTabSelectionStyle: UIViewControllerRepresentable {
    let color: Color
    func makeUIViewController(context: Context) -> Controller { Controller() }
    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.color = UIColor(color)
        controller.applyColor()
    }

    final class Controller: UIViewController {
        var color = UIColor.systemYellow
        override func loadView() {
            view = UIView()
            view.backgroundColor = .clear
            view.isUserInteractionEnabled = false
        }
        override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); applyColor() }
        override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); applyColor() }
        func applyColor() {
            guard isViewLoaded, view.window != nil, let tabs = tabBarController,
                  let selected = tabs.selectedViewController, selected.isViewLoaded,
                  view.isDescendant(of: selected.view) else { return }
            let bar = tabs.tabBar
            if bar.tintColor != color { bar.tintColor = color }
            func update(_ original: UITabBarAppearance) -> UITabBarAppearance? {
                let items = [original.stackedLayoutAppearance, original.inlineLayoutAppearance, original.compactInlineLayoutAppearance]
                guard items.contains(where: {
                    $0.selected.iconColor != color || ($0.selected.titleTextAttributes[.foregroundColor] as? UIColor) != color
                }) else { return nil }
                let appearance = original.copy()
                for item in [appearance.stackedLayoutAppearance, appearance.inlineLayoutAppearance, appearance.compactInlineLayoutAppearance] {
                    item.selected.iconColor = color
                    item.selected.titleTextAttributes[.foregroundColor] = color
                }
                return appearance
            }
            if let appearance = update(bar.standardAppearance) { bar.standardAppearance = appearance }
            if let edge = bar.scrollEdgeAppearance, let appearance = update(edge) { bar.scrollEdgeAppearance = appearance }
        }
    }
}
#endif
