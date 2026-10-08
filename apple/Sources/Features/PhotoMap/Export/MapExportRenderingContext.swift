import SwiftUI
#if os(iOS)
import UIKit
#endif

@MainActor final class MapExportRenderingContext {
    #if os(iOS)
    weak var windowScene: UIWindowScene?
    #endif
}

#if os(iOS)
struct MapExportRenderingContextBridge: UIViewRepresentable {
    let context: MapExportRenderingContext

    func makeUIView(context: Context) -> SceneReaderView {
        SceneReaderView(renderingContext: self.context)
    }

    func updateUIView(_ view: SceneReaderView, context: Context) {
        view.renderingContext = self.context
        view.updateScene()
    }

    final class SceneReaderView: UIView {
        var renderingContext: MapExportRenderingContext

        init(renderingContext: MapExportRenderingContext) {
            self.renderingContext = renderingContext
            super.init(frame: .zero)
            isUserInteractionEnabled = false
            accessibilityElementsHidden = true
            backgroundColor = .clear
        }

        required init?(coder: NSCoder) { nil }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            updateScene()
        }

        func updateScene() {
            renderingContext.windowScene = window?.windowScene
        }
    }
}
#else
struct MapExportRenderingContextBridge: View {
    let context: MapExportRenderingContext
    var body: some View { EmptyView() }
}
#endif
