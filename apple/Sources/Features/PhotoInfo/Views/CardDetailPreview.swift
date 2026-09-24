import SwiftUI

struct CardDetailPreview: View {
    let document: CardDocument
    var height: CGFloat = 156

    @State private var image: CGImage?
    @State private var loading = false
    @State private var error: String?
    @State private var retry = 0

    private var key: CardDetailPreviewKey {
        CardDetailPreviewKey(documentID: document.id, card: document.card, retry: retry)
    }

    var body: some View {
        ZStack {
            Color(white: 0.08)
            if document.card.rows.isEmpty {
                Text("card.preview")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if let image {
                Image(decorative: image, scale: 1, orientation: .up)
                    .resizable()
                    .scaledToFit()
                    .padding(6)
            } else if let error {
                VStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle")
                        .accessibilityHidden(true)
                    Text(error)
                        .font(.caption)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                    Button("action.retry") { retry += 1 }
                        .controlSize(.small)
                }
                .padding(12)
            } else if loading {
                ProgressView("card.preview.loading")
                    .controlSize(.small)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("card.preview"))
        .accessibilityValue(Text(verbatim: document.card.rows.map(\.text).joined(separator: ", ")))
        .onChange(of: document.id) { _, _ in image = nil; error = nil }
        .task(id: key) {
            let card = document.card
            guard !card.rows.isEmpty else {
                image = nil; error = nil; loading = false
                return
            }
            if image == nil { loading = true }
            error = nil
            do {
                try await Task.sleep(for: .milliseconds(160))
                let crop = try await CardImageProcessor.shared.previewCardDetail(document.sourceURL, card: card)
                try Task.checkCancellation()
                image = crop
                loading = false
            } catch is CancellationError {
            } catch {
                guard !Task.isCancelled else { return }
                image = nil
                self.error = (error as? CardError)?.localizedDescription ?? CardError.invalidImage.localizedDescription
                loading = false
            }
        }
    }

}

private struct CardDetailPreviewKey: Equatable {
    let documentID: UUID
    let card: PhotoCard
    let retry: Int
}
