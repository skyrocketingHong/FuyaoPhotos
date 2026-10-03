#if os(macOS)
import SwiftUI

struct SettingsSidebarAbout: View {
    let version: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("settings.about.header").font(.headline).accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: "Fuyao Photos").fontWeight(.semibold)
                Text(verbatim: version).foregroundStyle(.secondary)
            }
            Text("about.features").foregroundStyle(.secondary)
            DisclosureGroup("settings.about.credits") {
                VStack(alignment: .leading, spacing: 12) {
                    Text("about.colors.credit")
                    Text("settings.apple.notice")
                }
                .foregroundStyle(.secondary)
                .padding(.top, 4)
            }
            VStack(alignment: .leading, spacing: 6) {
                Link("AGPL-3.0-only", destination: URL(string: "https://github.com/skyrocketingHong/FuyaoPhotos/blob/main/LICENSE")!)
                Link("GitHub", destination: URL(string: "https://github.com/skyrocketingHong/FuyaoPhotos")!)
            }
        }
        .font(.footnote)
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .leading)
        .textSelection(.enabled)
    }
}
#endif
