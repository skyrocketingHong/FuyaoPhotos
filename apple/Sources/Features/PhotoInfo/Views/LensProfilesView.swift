import SwiftUI

struct LensProfilesView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draft: [LensProfile]
    @State private var editor: LensProfileDraft?
    @State private var confirmDiscard = false
    @State private var saveFailed = false
    private let initial: [LensProfile]
    private let store: LensProfileStore

    init(store: LensProfileStore) {
        self.store = store
        initial = store.profiles
        _draft = State(initialValue: store.profiles)
    }

    private var hasChanges: Bool { draft != initial }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("lens.profiles.description")
                        .foregroundStyle(.secondary)
                    Button("lens.add", systemImage: "plus") { editor = LensProfileDraft() }
                }
                if draft.isEmpty {
                    ContentUnavailableView("lens.empty.title", systemImage: "camera.aperture",
                                           description: Text("lens.empty.description"))
                } else {
                    Section {
                        ForEach(draft) { profile in
                            Button { editor = LensProfileDraft(profile: profile) } label: {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 5) {
                                        Text(profile.name).font(.headline).foregroundStyle(.primary)
                                        Text(profile.device).foregroundStyle(.secondary)
                                        Text(profile.exifModel).font(.subheadline).foregroundStyle(.secondary)
                                        Text(equivalentRange(profile)).font(.subheadline).monospacedDigit()
                                            .foregroundStyle(.secondary)
                                    }
                                    .fixedSize(horizontal: false, vertical: true)
                                    Spacer(minLength: 8)
                                    Image(systemName: "chevron.forward").font(.caption).foregroundStyle(.tertiary)
                                        .accessibilityHidden(true)
                                }
                                .contentShape(.rect)
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button("lens.edit", systemImage: "pencil") { editor = LensProfileDraft(profile: profile) }
                                Button("lens.delete", systemImage: "trash", role: .destructive) { remove(profile) }
                            }
                            .swipeActions {
                                Button("lens.delete", systemImage: "trash", role: .destructive) { remove(profile) }
                            }
                        }
                        .onDelete { draft.remove(atOffsets: $0) }
                    } header: {
                        Text("lens.profiles.saved")
                    } footer: {
                        Text("lens.profiles.footer")
                    }
                }
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .navigationTitle("lens.profiles.title")
#if !os(macOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(hasChanges ? "cancel" : "done") { if hasChanges { confirmDiscard = true } else { dismiss() } }
                        .keyboardShortcut(.cancelAction)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("lens.save") { save() }
                        .disabled(!hasChanges)
                        .keyboardShortcut("s", modifiers: .command)
                }
            }
            .sheet(item: $editor) { value in
                LensProfileEditor(draft: value) { profile in
                    if let index = draft.firstIndex(where: { $0.id == profile.id }) { draft[index] = profile }
                    else { draft.append(profile) }
                }
            }
            .confirmationDialog("lens.discard.title", isPresented: $confirmDiscard, titleVisibility: .visible) {
                Button("lens.discard", role: .destructive) { dismiss() }
                Button("lens.keepEditing", role: .cancel) { }
            } message: {
                Text("lens.discard.message")
            }
            .alert("lens.save.failed", isPresented: $saveFailed) { Button("done", role: .cancel) { } }
        }
        .interactiveDismissDisabled(hasChanges)
#if os(macOS)
        .frame(minWidth: 520, idealWidth: 640, minHeight: 480, idealHeight: 640)
#endif
    }

    private func equivalentRange(_ profile: LensProfile) -> String {
        let low = profile.equivalentMin.formatted(.number.precision(.fractionLength(0...2)))
        let high = profile.equivalentMax.formatted(.number.precision(.fractionLength(0...2)))
        return profile.equivalentMin == profile.equivalentMax ? "\(low) mm" : "\(low)–\(high) mm"
    }

    private func remove(_ profile: LensProfile) { draft.removeAll { $0.id == profile.id } }

    private func save() {
        do { try store.save(draft); dismiss() }
        catch { saveFailed = true }
    }
}
