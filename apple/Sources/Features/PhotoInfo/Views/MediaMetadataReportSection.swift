import SwiftUI

/// Renders the media metadata report as form/list sections. Row values are verbatim
/// technical strings or localized standard names; labels and section titles are keys.
struct MediaMetadataReportSection: View {
    let report: MediaMetadataReport?
    var prominentHeaders = false

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
            Text(LocalizedStringKey(key)).font(.title3).bold().textCase(nil)
        } else {
            Text(LocalizedStringKey(key))
        }
    }
}
