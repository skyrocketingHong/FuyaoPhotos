import SwiftUI

struct CardSaveSheet: View {
    let photoCount: Int
    let canUpdate: Bool
    let hasHDR: Bool
    let hasLive: Bool
    let save: (CardSaveOptions) -> Void
    @State private var options = CardPreferences.shared.saveOptions
    @Environment(\.dismiss) private var dismiss

    private var copyTitle: LocalizedStringKey {
        photoCount == 1 ? "card.save.copy.one" : "card.save.copy.many"
    }

    private var updateTitle: LocalizedStringKey {
        photoCount == 1 ? "card.save.update.one" : "card.save.update.many"
    }

    private var saveTitle: LocalizedStringKey {
        photoCount == 1 ? "card.save.action.one" : "card.save.action.many"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if canUpdate {
                        Picker("card.save.destination", selection: $options.updateOriginal) {
                            Text(copyTitle).tag(false)
                            Text(updateTitle).tag(true)
                        }
                    } else {
                        LabeledContent("card.save.destination") {
                            Text(copyTitle)
                        }
                    }
                } footer: {
                    if canUpdate && options.updateOriginal {
                        if photoCount == 1 { Text("card.save.update.description") }
                        else { Text("card.save.update.description.many") }
                    } else if hasLive {
                        Text("card.save.update.unavailable")
                    } else if photoCount == 1 {
                        Text("card.save.copy.description")
                    } else {
                        Text("card.save.copy.description.many")
                    }
                }
                CardSaveControls(options: $options, hasHDR: hasHDR, hasLive: hasLive)
            }
            .formStyle(.grouped)
            .navigationTitle("card.save.options")
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("card.cancel", action: dismiss.callAsFunction) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saveTitle) {
                        dismiss()
                        save(options)
                    }
                    .disabled((hasHDR || hasLive) && options.format == .png)
                }
            }
        }
        .onAppear {
            options = CardPreferences.shared.saveOptions
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
        Section {
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
