//
//  PhotoCountView.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-31.
//

import SwiftUI

struct PhotoCountView: View {
    let photoCount: Int
    let videoCount: Int

    var body: some View {
        HStack(spacing: DesignConstants.Spacing.xxSmall) {
            if photoCount > 0 {
                Text(formatCount(photoCount, label: L10n.PhotoCount.photos))
            }
            if photoCount > 0 && videoCount > 0 {
                Text(L10n.PhotoCount.separator)
            }
            if videoCount > 0 {
                Text(formatCount(videoCount, label: L10n.PhotoCount.videos))
            }
        }
        .font(DesignConstants.FontSize.caption)
        .foregroundStyle(.secondary)
    }

    private func formatCount(_ count: Int, label: String) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        let formattedCount = formatter.string(from: NSNumber(value: count)) ?? "\(count)"
        return "\(formattedCount) \(label)"
    }
}

#Preview {
    VStack(spacing: 20) {
        PhotoCountView(photoCount: 24667, videoCount: 207)
        PhotoCountView(photoCount: 1234, videoCount: 0)
        PhotoCountView(photoCount: 0, videoCount: 56)
    }
}
