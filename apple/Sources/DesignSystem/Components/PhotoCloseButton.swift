import SwiftUI

struct PhotoCloseButton: View {
    let session: CardSession
    @Environment(PhotoWorkspace.self) private var workspace
    @State private var confirmsDiscard = false
    private var label: LocalizedStringKey { session.documents.count == 1 ? "card.close.one" : "card.close.many" }
    private var prompt: LocalizedStringKey { session.documents.count == 1 ? "card.close.confirm.one" : "card.close.confirm.many" }

    var body: some View {
        Group {
#if os(macOS)
            Button(label, systemImage: "xmark", action: close)
                .labelStyle(.iconOnly).buttonBorderShape(.circle)
#else
            CircularIconButton(label, systemImage: "xmark", action: close)
#endif
        }
        .disabled(session.busy)
        .confirmationDialog(prompt, isPresented: $confirmsDiscard, titleVisibility: .visible) {
            Button(label, role: .destructive) { session.clear() }
            Button("card.cancel", role: .cancel) {}
        }
    }

    private func close() {
        if workspace.hasPendingEdits(in: session) { confirmsDiscard = true }
        else { session.clear() }
    }
}
