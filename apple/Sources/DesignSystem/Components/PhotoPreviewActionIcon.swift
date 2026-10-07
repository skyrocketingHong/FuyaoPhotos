import SwiftUI

struct PhotoPreviewActionIcon: View {
    let image: Image

    var body: some View {
        image.resizable().scaledToFit()
            .frame(width: 20, height: 20)
            .frame(width: 24, height: 24)
    }
}
