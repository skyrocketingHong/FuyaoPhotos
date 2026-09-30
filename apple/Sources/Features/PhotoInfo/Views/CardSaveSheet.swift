import SwiftUI

struct CardSaveSheet: View {
    let photoCount: Int
    let canUpdate: Bool
    let hasHDR: Bool
    let hasLive: Bool
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
        photoCount == 1 ? "card.save.action.one" : "card.save.action.many"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if canUpdate && !options.exportsMotionPhoto {
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
                    if (!options.exportsMotionPhoto || format == .jpeg) && (format != .png || (!hasHDR && !hasLive)) { Text(format.title).tag(format) }
                }
            }
            if hasLive {
                Picker("card.save.motion.format", selection: $options.exportsMotionPhoto) {
                    Text("Live Photo").tag(false)
                    Text("Motion Photo").tag(true)
                }
                .onChange(of: options.exportsMotionPhoto) { _, enabled in
                    if enabled { options.format = .jpeg; options.updateOriginal = false }
                }
                if options.exportsMotionPhoto { Text("card.save.motion.description").font(.footnote).foregroundStyle(.secondary) }
            }
            if options.format != .png {
                LabeledContent("card.save.quality") {
                    HStack {
                        Slider(value: $options.quality, in: 0...100, step: 1)
                            .frame(minWidth: 80, maxWidth: 180)
                            .accessibilityLabel(Text("card.save.quality"))
                        Text("100%")
                            .monospacedDigit()
                            .hidden()
                            .overlay(alignment: .trailing) {
                                Text("\(Int(options.quality))%")
                                    .monospacedDigit().foregroundStyle(.secondary)
                            }
                            .fixedSize()
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(Text("card.save.quality"))
                            .accessibilityValue(Text("\(Int(options.quality))%"))
                    }
                }
            }
        } header: {
            Text("card.save.output.header")
        } footer: {
            Text("card.save.output.footer")
        }
    }
}
