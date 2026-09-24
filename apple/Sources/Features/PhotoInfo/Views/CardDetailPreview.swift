import SwiftUI

struct CardDetailPreview: View {
    let document: CardDocument
    var height: CGFloat = 156
    var processing = false
    var highlightedField: CardField?
    var highlightedStyle: CardAdjustment?

    @State private var render: CardDetailRender?
    @State private var loading = false
    @State private var error: String?
    @State private var retry = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var key: CardDetailPreviewKey {
        CardDetailPreviewKey(documentID: document.id, card: document.card, retry: retry)
    }

    private var selectionID: String {
        if let highlightedField { return "field-\(highlightedField.rawValue)" }
        if let highlightedStyle { return "style-\(highlightedStyle.rawValue)" }
        return "none"
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
                        .id(selectionID)
                        .transition(.opacity)
                }
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: selectionID)
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

    var body: some View {
        GeometryReader { geometry in
            Canvas { context, size in
                let scale = min((size.width - 12) / CGFloat(render.image.width),
                                (size.height - 12) / CGFloat(render.image.height))
                guard scale.isFinite, scale > 0 else { return }
                let origin = CGPoint(x: (size.width - CGFloat(render.image.width) * scale) / 2,
                                     y: (size.height - CGFloat(render.image.height) * scale) / 2)
                func scaled(_ rect: CGRect) -> CGRect {
                    CGRect(x: origin.x + rect.minX * scale, y: origin.y + rect.minY * scale,
                           width: rect.width * scale, height: rect.height * scale)
                }
                if let field {
                    for rect in render.textRects[field] ?? [] {
                        let path = Path(roundedRect: scaled(rect).insetBy(dx: -2, dy: -2), cornerRadius: 3)
                        context.fill(path, with: .color(.yellow.opacity(0.20)))
                        context.stroke(path, with: .color(.yellow.opacity(0.82)), lineWidth: 1)
                    }
                } else if let style {
                    if style == .textScale {
                        for rect in render.textRects.values.flatMap({ $0 }) {
                            context.fill(Path(roundedRect: scaled(rect).insetBy(dx: -2, dy: -2), cornerRadius: 3),
                                         with: .color(.yellow.opacity(0.15)))
                        }
                    } else {
                        var rect = scaled(render.cardRect)
                        if style == .rightInset { rect = CGRect(x: rect.maxX - 3, y: rect.minY, width: 3, height: rect.height) }
                        if style == .bottomInset { rect = CGRect(x: rect.minX, y: rect.maxY - 3, width: rect.width, height: 3) }
                        let path = Path(roundedRect: rect, cornerRadius: style == .cornerRadius ? 12 : 4)
                        context.stroke(path, with: .color(.yellow.opacity(0.88)), lineWidth: 1.5)
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

private struct CardDetailPreviewKey: Equatable {
    let documentID: UUID
    let card: PhotoCard
    let retry: Int
}
