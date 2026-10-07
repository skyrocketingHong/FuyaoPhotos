import SwiftUI

struct CardSaveSheet: View {
    let photoCount: Int
    let canUpdate: Bool
    let hasHDR: Bool
    let hasLive: Bool
    var requiresHEIC = false
    let save: (CardSaveOptions) -> Void
    @State private var options = CardPreferences.shared.saveOptions
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var copyTitle: LocalizedStringKey {
        photoCount == 1 ? "card.save.copy.one" : "card.save.copy.many"
    }

    private var updateTitle: LocalizedStringKey {
        photoCount == 1 ? "card.save.update.one" : "card.save.update.many"
    }

    private var saveTitle: LocalizedStringKey {
        options.exportsMotionPhoto ? "card.files.export" : (photoCount == 1 ? "card.save.action.one" : "card.save.action.many")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if options.exportsMotionPhoto {
                        LabeledContent("card.save.destination", value: String.localized("card.files.destination"))
                    } else if canUpdate {
                        Picker("card.save.destination", selection: $options.updateOriginal) {
                            Text(copyTitle).tag(false)
                            Text(updateTitle).tag(true)
                        }
                        .photoFormMenuPickerStyle()
                    } else {
                        LabeledContent("card.save.destination") {
                            Text(copyTitle)
                        }
                    }
                } footer: {
                    if options.exportsMotionPhoto {
                        Text("card.files.description")
                    } else if canUpdate && options.updateOriginal {
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
                CardSaveControls(options: $options, hasHDR: hasHDR, hasLive: hasLive, requiresHEIC: requiresHEIC)
            }
            .photoPageForm()
            .animation(reduceMotion ? nil : .smooth(duration: 0.2), value: options.format)
            .animation(reduceMotion ? nil : .smooth(duration: 0.2), value: options.exportsMotionPhoto)
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: options.updateOriginal)
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
            options.keepExif = true
            options.keepLocation = true
            options.keepCaptureTime = true
            if !canUpdate { options.updateOriginal = false }
            if options.exportsMotionPhoto { options.format = .jpeg; options.updateOriginal = false }
            if (hasHDR || hasLive) && options.format == .png { options.format = .jpeg }
            if requiresHEIC { options.format = .heic; options.exportsMotionPhoto = false }
        }
#if os(macOS)
        .frame(minWidth: 600, idealWidth: 720, minHeight: 560, idealHeight: 700)
        .presentationSizing(.fitted)
#endif
    }
}

struct CardSaveControls: View {
    @Binding var options: CardSaveOptions
    var hasHDR = false
    var hasLive = false
    var requiresHEIC = false
    var body: some View {
        Section {
            Picker("card.save.format", selection: $options.format) {
                ForEach(CardExportFormat.allCases) { format in
                    if (!requiresHEIC || format == .heic) && (!options.exportsMotionPhoto || format == .jpeg)
                        && (format != .png || (!hasHDR && !hasLive)) { Text(format.title).tag(format) }
                }
            }
            .photoFormMenuPickerStyle()
            if hasLive && !requiresHEIC {
                Picker("card.save.motion.format", selection: $options.exportsMotionPhoto) {
                    Text("Live Photo").tag(false)
                    Text("Motion Photo").tag(true)
                }
                .photoFormMenuPickerStyle()
                .onChange(of: options.exportsMotionPhoto) { _, enabled in
                    if enabled { options.format = .jpeg; options.updateOriginal = false }
                }
                if options.exportsMotionPhoto { Text("card.save.motion.description").font(.footnote).foregroundStyle(.secondary) }
            }
            if options.format != .png {
                PhotoFormSlider(title: "card.save.quality", value: $options.quality, range: 0...100,
                    minimumLabel: "card.save.quality.minimum", maximumLabel: "card.save.quality.maximum",
                    step: 1, formattedValue: (options.quality / 100).formatted(.percent.precision(.fractionLength(0))),
                    maximumValueText: 1.0.formatted(.percent.precision(.fractionLength(0))))
            }
        } header: {
            Text("card.save.output.header")
        } footer: {
            Text(requiresHEIC ? "card.save.nativeMetadata" : "card.save.output.footer")
        }
    }
}
