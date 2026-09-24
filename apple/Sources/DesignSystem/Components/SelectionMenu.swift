//
//  SelectionMenu.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-31.
//

import SwiftUI

struct SelectionMenu<T: Identifiable & RawRepresentable & CaseIterable & Hashable>: View where T.RawValue == String {
    @Binding var selection: T
    let items: [T]
    let iconProvider: (T) -> String
    let labelProvider: (T) -> LocalizedStringKey

    init(
        selection: Binding<T>,
        items: [T] = Array(T.allCases),
        iconProvider: @escaping (T) -> String,
        labelProvider: @escaping (T) -> LocalizedStringKey = { LocalizedStringKey($0.rawValue) }
    ) {
        self._selection = selection
        self.items = items
        self.iconProvider = iconProvider
        self.labelProvider = labelProvider
    }

    var body: some View {
        Menu {
            Picker(selection: $selection) {
                ForEach(items) { item in
                    Label(labelProvider(item), systemImage: iconProvider(item))
                        .tag(item)
                }
            } label: {
                EmptyView()
            }
            .pickerStyle(.inline)
            .labelsHidden()
        } label: {
            Image(systemName: iconProvider(selection))
                .font(DesignConstants.FontSize.body)
                .symbolRenderingMode(.hierarchical)
        }
    }
}

struct SidebarSelectionButton<T: Identifiable & RawRepresentable>: View where T.RawValue == String {
    let item: T
    let isSelected: Bool
    let icon: String
    let label: LocalizedStringKey
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: DesignConstants.Spacing.small) {
                Label {
                    Text(label)
                        .font(DesignConstants.FontSize.body)
                } icon: {
                    Image(systemName: icon)
                        .font(DesignConstants.FontSize.body)
                        .symbolRenderingMode(.hierarchical)
                }
                .foregroundStyle(isSelected ? Color.accentColor : Color.primary)

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(DesignConstants.FontSize.body)
                        .foregroundStyle(.tint)
                        .symbolRenderingMode(.hierarchical)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct SidebarSection<T: Identifiable & RawRepresentable & CaseIterable>: View where T.RawValue == String {
    let title: LocalizedStringKey
    let items: [T]
    @Binding var selection: T
    let iconProvider: (T) -> String
    let labelProvider: (T) -> LocalizedStringKey

    init(
        title: LocalizedStringKey,
        items: [T] = Array(T.allCases),
        selection: Binding<T>,
        iconProvider: @escaping (T) -> String,
        labelProvider: @escaping (T) -> LocalizedStringKey = { LocalizedStringKey($0.rawValue) }
    ) {
        self.title = title
        self.items = items
        self._selection = selection
        self.iconProvider = iconProvider
        self.labelProvider = labelProvider
    }

    var body: some View {
        Section {
            ForEach(items) { item in
                SidebarSelectionButton(
                    item: item,
                    isSelected: selection.id == item.id,
                    icon: iconProvider(item),
                    label: labelProvider(item)
                ) {
                    withAnimation(DesignConstants.Animation.bouncy) {
                        selection = item
                    }
                }
            }
        } header: {
            Text(title)
                .font(DesignConstants.FontSize.subheadline)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)
        }
        .listRowBackground(Color.clear)
    }
}
