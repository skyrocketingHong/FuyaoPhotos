import SwiftUI

struct PhotoPreviewActionIcon: View {
    let image: Image

    var body: some View {
        image.resizable().scaledToFit()
            .frame(width: 22, height: 22)
            .frame(width: 28, height: 28)
    }
}
