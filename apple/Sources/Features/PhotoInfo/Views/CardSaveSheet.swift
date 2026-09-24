import SwiftUI

struct CardSaveSheet: View {
    let canUpdate: Bool
    let hasHDR: Bool
    let hasLive: Bool
    let save: (CardSaveOptions) -> Void
    @State private var options = CardPreferences.shared.saveOptions
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("card.save.destination") {
                    Toggle("card.save.update", isOn: $options.updateOriginal).disabled(!canUpdate)
                    Text(LocalizedStringKey(options.updateOriginal ? "card.save.update.description" : "card.save.copy.description"))
                        .font(.caption).foregroundStyle(.secondary)
                    if !canUpdate { Text("card.save.update.unavailable").font(.caption).foregroundStyle(.secondary) }
                }
                CardSaveControls(options: $options, hasHDR: hasHDR, hasLive: hasLive)
            }
            .formStyle(.grouped)
            .navigationTitle("card.save")
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("card.cancel", action: dismiss.callAsFunction) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("card.save") { dismiss(); save(options) }.disabled((hasHDR || hasLive) && options.format == .png)
                }
            }
        }
        .onAppear {
            if !canUpdate { options.updateOriginal = false }
            if (hasHDR || hasLive) && options.format == .png { options.format = .jpeg }
        }
#if os(macOS)
        .frame(minWidth: 520, idealWidth: 600, minHeight: 520, idealHeight: 600)
#endif
    }
}

struct CardSaveControls: View {
    @Binding var options: CardSaveOptions
    var hasHDR = false
    var hasLive = false
    var body: some View {
        Section("card.save.format") {
            Picker("card.save.format", selection: $options.format) {
                ForEach(CardExportFormat.allCases) { format in
                    if format != .png || (!hasHDR && !hasLive) { Text(format.title).tag(format) }
                }
            }
            if options.format != .png {
                VStack(alignment: .leading) {
                    LabeledContent("card.save.quality", value: "\(Int(options.quality))%")
                    Slider(value: $options.quality, in: 0...100, step: 1).accessibilityLabel(Text("card.save.quality"))
                }
            }
        }
        Section {
            Toggle("card.save.exif", isOn: $options.keepExif)
            Toggle("card.save.location", isOn: $options.keepLocation)
            Toggle("card.save.time", isOn: $options.keepCaptureTime)
        } header: { Text("card.save.metadata") }
        footer: { Text("card.save.metadata.footer") }
    }
}
