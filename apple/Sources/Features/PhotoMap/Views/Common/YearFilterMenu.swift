//
//  YearFilterMenu.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-30.
//

import SwiftUI

struct YearFilterMenu: View {
    @Binding var selectedYear: Int?
    let availableYears: [Int]

    var body: some View {
        Menu {
            Picker(selection: $selectedYear) {
                Label(L10n.YearFilter.all, systemImage: "calendar")
                    .tag(nil as Int?)

                ForEach(availableYears, id: \.self) { year in
                    Label(String(year), systemImage: "calendar.badge.clock")
                        .tag(year as Int?)
                }
            } label: {
                EmptyView()
            }
            .pickerStyle(.inline)
            .labelsHidden()
        } label: {
            Image(systemName: "calendar")
                .font(DesignConstants.FontSize.body)
                .symbolRenderingMode(.hierarchical)
        }
    }
}
