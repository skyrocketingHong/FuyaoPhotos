import SwiftUI

struct CardDetailPreview: View {
    let document: CardDocument
    var height: CGFloat = 156
    var processing = false
    var highlightedField: CardField?
    var highlightedStyle: CardAdjustment?

    /// The crop region always keeps the reference card ratio, so the preview box never jumps between photos.
    static let referenceAspect: CGFloat = 215.0 / 168.0

    @State private var render: CardDetailRender?
    @State private var loading = false
    @State private var error: String?
    @State private var retry = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var key: CardDetailPreviewKey {
        CardDetailPreviewKey(documentID: document.id, card: document.card, retry: retry)
    }

    var body: some View {
        ZStack {
            if document.card.rows.isEmpty {
                Text("card.preview")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if let render {
                ZStack {
                    Image(decorative: render.image, scale: 1, orientation: .up)
                        .resizable()
                        .scaledToFit()
                        .padding(6)
                    CardSelectionHighlight(render: render, field: highlightedField, style: highlightedStyle)
                }
                .modifier(ProcessingVeil(active: processing || loading, pulse: loading && !processing))
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
                VStack(spacing: 6) {
                    Image(systemName: "rectangle.on.rectangle")
                        .font(.title3)
                        .symbolEffect(.pulse, isActive: !reduceMotion)
                    Text("card.preview.loading").font(.caption)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("card.preview"))
        .accessibilityValue(Text(verbatim: document.card.rows.map(\.text).joined(separator: ", ")))
        .onChange(of: document.id) { _, _ in render = nil; error = nil }
        .task(id: key) {
            let card = document.card
            guard !card.rows.isEmpty else {
                render = nil; error = nil; loading = false
                return
            }
            loading = true
            error = nil
            do {
                try await Task.sleep(for: .milliseconds(160))
                let crop = try await CardImageProcessor.shared.previewCardDetail(document.sourceURL, card: card)
                try Task.checkCancellation()
                render = crop
                loading = false
            } catch is CancellationError {
            } catch {
                guard !Task.isCancelled else { return }
                render = nil
                self.error = (error as? CardError)?.localizedDescription ?? CardError.invalidImage.localizedDescription
                loading = false
            }
        }
    }

}

private struct CardSelectionHighlight: View {
    let render: CardDetailRender
    let field: CardField?
    let style: CardAdjustment?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private struct Box: Equatable {
        var rect: CGRect
        var filled = false
        var strokeWidth: CGFloat = 1
        var cornerRadius: CGFloat = 3
    }

    private var boxes: [Box] {
        guard let field else {
            guard let style else { return [] }
            if style == .textScale {
                return render.textRects.values.flatMap { $0 }.map { Box(rect: $0, filled: true) }
            }
            var rect = render.cardRect
            if style == .rightInset { rect = CGRect(x: rect.maxX - 3, y: rect.minY, width: 3, height: rect.height) }
            if style == .bottomInset { rect = CGRect(x: rect.minX, y: rect.maxY - 3, width: rect.width, height: 3) }
            return [Box(rect: rect, strokeWidth: 1.5, cornerRadius: style == .cornerRadius ? 12 : 4)]
        }
        return (render.textRects[field] ?? []).map { Box(rect: $0, filled: true, strokeWidth: 1) }
    }

    var body: some View {
        GeometryReader { geometry in
            let scale = min((geometry.size.width - 12) / CGFloat(render.image.width),
                            (geometry.size.height - 12) / CGFloat(render.image.height))
            let origin = CGPoint(x: (geometry.size.width - CGFloat(render.image.width) * scale) / 2,
                                 y: (geometry.size.height - CGFloat(render.image.height) * scale) / 2)
            ZStack {
                ForEach(boxes.indices, id: \.self) { index in
                    let box = boxes[index]
                    HighlightBoxShape(cornerRadius: box.cornerRadius)
                        .fill(.yellow.opacity(box.filled ? 0.20 : 0))
                        .overlay {
                            HighlightBoxShape(cornerRadius: box.cornerRadius)
                                .stroke(.yellow.opacity(box.filled ? 0.82 : 0.88), lineWidth: box.strokeWidth)
                        }
                        .frame(width: box.rect.width * scale, height: box.rect.height * scale)
                        .position(x: origin.x + box.rect.midX * scale, y: origin.y + box.rect.midY * scale)
                        .animation(reduceMotion ? nil : .snappy(duration: 0.32, extraBounce: 0.08), value: box.rect)
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

private struct HighlightBoxShape: Shape {
    var cornerRadius: CGFloat
    func path(in rect: CGRect) -> Path {
        Path(roundedRect: rect.insetBy(dx: -2, dy: -2), cornerRadius: cornerRadius)
    }
}

private struct CardDetailPreviewKey: Equatable {
    let documentID: UUID
    let card: PhotoCard
    let retry: Int
}
