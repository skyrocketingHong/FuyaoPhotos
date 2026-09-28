import SwiftUI

struct PhotoImageShadow: View {
    let aspectRatio: CGFloat
    var body: some View {
        GeometryReader { geometry in
            let width = min(geometry.size.width, geometry.size.height * aspectRatio)
            let height = width / max(0.01, aspectRatio)
            Rectangle().fill(.black.opacity(0.12))
                .frame(width: width, height: height)
                .shadow(color: .black.opacity(0.22), radius: 8, y: 3)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
