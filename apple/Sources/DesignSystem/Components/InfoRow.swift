//
//  InfoRow.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-31.
//

import SwiftUI

/// 统一的信息行组件（用于PhotoDetailSheet）
struct InfoRow: View {
    let icon: String
    let label: LocalizedStringKey
    let value: String
    let useMonospacedDigit: Bool

    init(icon: String, label: LocalizedStringKey, value: String, useMonospacedDigit: Bool = false) {
        self.icon = icon
        self.label = label
        self.value = value
        self.useMonospacedDigit = useMonospacedDigit
    }

    var body: some View {
        HStack {
            Label(label, systemImage: icon)
                .symbolRenderingMode(.hierarchical)
                .font(DesignConstants.FontSize.subheadline)
                .foregroundStyle(.secondary)

            Spacer()

            Group {
                if useMonospacedDigit {
                    Text(value)
                        .monospacedDigit()
                } else {
                    Text(value)
                }
            }
            .font(DesignConstants.FontSize.body)
            .fontWeight(.medium)
            .foregroundStyle(.primary)
        }
        .padding(.horizontal, DesignConstants.Spacing.large)
    }
}

struct InfoCard<Content: View>: View {
    let title: LocalizedStringKey
    let icon: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: DesignConstants.Spacing.small) {
            Label(title, systemImage: icon)
                .font(DesignConstants.FontSize.headline)
                .fontWeight(.semibold)
                .foregroundStyle(.primary)
                .symbolRenderingMode(.hierarchical)
                .padding(.horizontal, DesignConstants.Spacing.large)

            VStack(spacing: DesignConstants.Spacing.xSmall) {
                content
            }
            .padding(.vertical, DesignConstants.Spacing.small)
            .background(
                .regularMaterial,
                in: RoundedRectangle(
                    cornerRadius: DesignConstants.CornerRadius.large,
                    style: .continuous
                )
            )
            .shadow(
                color: DesignConstants.Shadow.subtle.color,
                radius: DesignConstants.Shadow.subtle.radius,
                x: DesignConstants.Shadow.subtle.x,
                y: DesignConstants.Shadow.subtle.y
            )
        }
    }
}
