import SwiftUI

struct LensHardwareBindingView: View {
    @Environment(\.dismiss) private var dismiss
    let profile: LensProfile
    let profiles: [LensProfile]
    let hardware: [HardwareLens]
    let hardwareDevice: String
    let apply: (String?) -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(profile.device).foregroundStyle(.secondary)
                    Text(profile.name)
                    if hardware.isEmpty { Text("lens.hardware.empty").foregroundStyle(.secondary) }
                    ForEach(hardware) { lens in
                        let occupied = profiles.contains { $0.id != profile.id && $0.hardwareDevice == hardwareDevice && $0.cameraID == lens.id }
                        let compatible = LensBindings.compatible(profile, with: lens, automatic: false)
                        Button { apply(lens.id); dismiss() } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(lens.name).foregroundStyle(.primary)
                                Text(lens.id).font(.footnote).foregroundStyle(.secondary)
                                if let focal = lens.equivalentFocal { Text(verbatim: "\(focal.formatted(.number.precision(.fractionLength(0...2)))) mm").font(.subheadline) }
                                if occupied { Text("lens.hardware.occupied").font(.footnote).foregroundStyle(.secondary) }
                                else if !compatible { Text("lens.hardware.mismatch").font(.footnote).foregroundStyle(.secondary) }
                            }.fixedSize(horizontal: false, vertical: true)
                        }.disabled(occupied || !compatible || hardwareDevice.isEmpty)
                    }
                } footer: { Text("lens.hardware.manualDescription") }
                if profile.cameraID != nil {
                    Button("lens.hardware.unbind", role: .destructive) { apply(nil); dismiss() }
                }
            }
            .navigationTitle("lens.hardware.bind")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("cancel") { dismiss() } } }
        }
#if os(macOS)
        .frame(minWidth: 440, minHeight: 360)
#endif
    }
}
