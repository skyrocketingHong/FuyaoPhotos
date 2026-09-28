import SwiftUI

struct PhotoImageShadow: View {
    let aspectRatio: CGFloat
    @Environment(\.colorScheme) private var colorScheme
    var body: some View {
        GeometryReader { geometry in
            let width = min(geometry.size.width, geometry.size.height * aspectRatio)
            let height = width / max(0.01, aspectRatio)
            Rectangle().fill(.black.opacity(0.08))
                .frame(width: width, height: height)
                .shadow(color: .black.opacity(colorScheme == .dark ? 0.45 : 0.20), radius: 18, y: 12)
                .shadow(color: .black.opacity(0.16), radius: 3, y: 2)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
