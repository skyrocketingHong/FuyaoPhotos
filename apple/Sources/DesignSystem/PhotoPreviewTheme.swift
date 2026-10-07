import SwiftUI

enum PhotoPreviewTheme {
    static var surface: Color {
#if os(macOS)
        Color(nsColor: .windowBackgroundColor)
#else
        Color(uiColor: .systemBackground)
#endif
    }

    static var accent: Color { .accentColor }
}
