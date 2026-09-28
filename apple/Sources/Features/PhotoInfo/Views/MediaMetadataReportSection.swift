import SwiftUI

/// Renders the media metadata report as form/list sections. Row values are verbatim
/// technical strings or localized standard names; labels and section titles are keys.
struct MediaMetadataReportSection: View {
    let report: MediaMetadataReport?
    var prominentHeaders = false
    var showsDescriptions = false

    var body: some View {
        if let report {
            ForEach(report.sections, id: \.titleKey) { section in
                Section {
                    ForEach(section.rows, id: \.labelKey) { row in
                        LabeledContent {
                            Text(row.value ?? String.localized(row.valueKey ?? ""))
                                .multilineTextAlignment(.trailing)
                                .textSelection(.enabled)
                        } label: {
                            Text(LocalizedStringKey(row.labelKey))
                        }
                    }
                } header: {
                    sectionHeader(section.titleKey)
                } footer: {
                    if showsDescriptions {
                        MetadataReportFooter(titleKey: section.titleKey)
                    }
                }
            }
        } else {
            Section {
                HStack(spacing: 12) {
                    ProgressView()
                    Text("metadata.report.loading")
                }
            }
        }
    }

    @ViewBuilder private func sectionHeader(_ key: String) -> some View {
        if prominentHeaders {
            Text(LocalizedStringKey(key)).textCase(nil)
        } else {
            Text(LocalizedStringKey(key))
        }
    }
}

private struct MetadataReportFooter: View {
    let titleKey: String

    var body: some View {
        switch titleKey {
        case "metadata.report.section.container": Text("metadata.report.section.container.description")
        case "metadata.report.section.color": Text("metadata.report.section.color.description")
        case "metadata.report.section.hdr": Text("metadata.report.section.hdr.description")
        case "metadata.report.section.motion": Text("metadata.report.section.motion.description")
        case "metadata.report.section.styles": Text("metadata.report.section.styles.description")
        case "metadata.report.section.vendor": Text("metadata.report.section.vendor.description")
        default: EmptyView()
        }
    }
}
