//
//  DisplayModeMenu.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-30.
//

import SwiftUI

struct DisplayModeMenu: View {
    @Binding var displayMode: MapDisplayMode
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Menu {
            ForEach(MapDisplayMode.allCases) { mode in
                Button {
                    displayMode = mode
                } label: {
                    Label(mode.localizedName, systemImage: mode.icon)
                    if displayMode == mode {
                        Image(systemName: "checkmark")
                    }
                }
            }
        } label: {
            Label(displayMode.localizedName, systemImage: displayMode.icon)
                .contentTransition(reduceMotion ? .identity : .symbolEffect(.replace))
                .animation(reduceMotion ? nil : .smooth(duration: 0.2), value: displayMode)
        }
    }
}
