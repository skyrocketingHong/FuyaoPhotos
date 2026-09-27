import SwiftUI

enum PhotoPreviewTheme {
    static var surface: Color {
#if os(macOS)
        Color(nsColor: .windowBackgroundColor)
#else
        Color(uiColor: .systemBackground)
#endif
    }

    static func accent(in scheme: ColorScheme) -> Color {
        scheme == .dark ? .yellow : Color(red: 107 / 255, green: 74 / 255, blue: 0)
    }
}
