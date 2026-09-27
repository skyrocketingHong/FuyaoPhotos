import SwiftUI
#if os(macOS)
import AppKit
#else
import UIKit
#endif

struct ColorResultsPanel: View {
    let sample: PhotoColorSample
    let information: PhotoColorDescription?
    @Binding var space: ColorResultSpace
    @Binding var hdr: Bool
    let showInfo: () -> Void
    let movePixel: (Int, Int) -> Void

    var body: some View {
        Section {
            HStack(spacing: 16) {
                if let patch = sample.magnifier {
                    Image(decorative: patch, scale: 1).resizable().interpolation(.none)
                        .frame(width: 72, height: 72)
                        .overlay { Image(systemName: "plus").foregroundStyle(.white).shadow(color: .black, radius: 1) }
                        .accessibilityLabel(Text("colors.magnifier"))
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("(\(sample.color.x), \(sample.color.y))").monospacedDigit()
                    if let information {
                        Text("\((Double(sample.color.x) / Double(max(1, information.width - 1))).formatted(.percent.precision(.fractionLength(1)))), \((Double(sample.color.y) / Double(max(1, information.height - 1))).formatted(.percent.precision(.fractionLength(1))))")
                            .font(.caption).foregroundStyle(.secondary)
                        Text(information.colorSpace).font(.caption).foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 0)
                Button("colors.source", systemImage: "info.circle", action: showInfo).labelStyle(.iconOnly)
            }
            if information?.headroom ?? 1 > 1 { Toggle("HDR", isOn: $hdr).tint(.green) }
            Picker("colors.space", selection: $space) {
                ForEach(ColorResultSpace.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.menu)
        }
        Section {
            LabeledContent("colors.sample") {
                let rgb = (space == .css ? sample.color.cssReference?.rgb : space == .ral ? sample.color.ralReference?.rgb : nil) ?? sample.color.srgb
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color(.sRGB, red: rgb.clipped.x, green: rgb.clipped.y, blue: rgb.clipped.z, opacity: 1))
                    .frame(width: 64, height: 44)
                    .accessibilityLabel(Text(verbatim: rgb.hex))
            }
            ForEach(sample.color.readouts(space)) { row in
                LabeledContent(row.label) {
                    Text(row.value).monospaced().textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                        .contextMenu { Button("colors.copy", systemImage: "doc.on.doc") { copy(row.value) } }
                }
            }
        } footer: {
            if space == .ral { Text("colors.ral.note") }
            else if space == .css { Text("colors.css.note") }
            else if sample.color.srgb.outOfGamut { Text("colors.gamut.note") }
            else if space == .sRGB { Text("colors.cmyk.note") }
        }
        Section("colors.pixel") {
            Stepper("X: \(sample.color.x)", onIncrement: { movePixel(sample.color.x + 1, sample.color.y) },
                    onDecrement: { movePixel(sample.color.x - 1, sample.color.y) })
            Stepper("Y: \(sample.color.y)", onIncrement: { movePixel(sample.color.x, sample.color.y + 1) },
                    onDecrement: { movePixel(sample.color.x, sample.color.y - 1) })
        }
    }

    private func copy(_ text: String) {
#if os(macOS)
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
#else
        UIPasteboard.general.string = text
#endif
    }
}
