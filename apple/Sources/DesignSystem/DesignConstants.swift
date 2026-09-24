//
//  DesignConstants.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-31.
//

import SwiftUI
import MapKit

/// Liquid Glass 设计系统常量
enum DesignConstants {
    // MARK: - Spacing
    enum Spacing {
        static let xxSmall: CGFloat = 4
        static let xSmall: CGFloat = 8
        static let small: CGFloat = 12
        static let medium: CGFloat = 16
        static let large: CGFloat = 20
        static let xLarge: CGFloat = 24
        static let xxLarge: CGFloat = 32
    }

    // MARK: - Corner Radius
    enum CornerRadius {
        static let small: CGFloat = 12
        static let medium: CGFloat = 16
        static let large: CGFloat = 18
        static let xLarge: CGFloat = 20
        static let xxLarge: CGFloat = 24
    }

    // MARK: - Font Sizes
    enum FontSize {
        static let caption: Font = .caption
        static let caption2: Font = .caption2
        static let footnote: Font = .footnote
        static let subheadline: Font = .subheadline
        static let body: Font = .body
        static let headline: Font = .headline
        static let title3: Font = .title3
        static let title2: Font = .title2
        static let title: Font = .title
        static let largeTitle: Font = .largeTitle
    }

    // MARK: - Icon Sizes
    enum IconSize {
        static let small: CGFloat = 16
        static let medium: CGFloat = 20
        static let large: CGFloat = 24
        static let xLarge: CGFloat = 28
    }

    // MARK: - Shadow
    enum Shadow {
        static let subtle = (color: Color.black.opacity(0.05), radius: CGFloat(8), x: CGFloat(0), y: CGFloat(4))
        static let medium = (color: Color.black.opacity(0.1), radius: CGFloat(12), x: CGFloat(0), y: CGFloat(6))
        static let strong = (color: Color.black.opacity(0.15), radius: CGFloat(16), x: CGFloat(0), y: CGFloat(8))
        static let photo = (color: Color.black.opacity(0.2), radius: CGFloat(16), x: CGFloat(0), y: CGFloat(8))
    }

    // MARK: - Animation
    enum Animation {
        static let bouncy: SwiftUI.Animation = .bouncy
        static let smooth: SwiftUI.Animation = .smooth
        static let spring: SwiftUI.Animation = .spring(response: 0.3, dampingFraction: 0.7)
    }

    // MARK: - Image Sizes
    enum ImageSize {
        static let thumbnail: CGFloat = 60
        static let small: CGFloat = 80
        static let medium: CGFloat = 120
        static let large: CGFloat = 200
        static let fullHeight: CGFloat = 320
    }

    // MARK: - Map Clustering
    enum MapClustering {
        static let maxConcurrentThumbnailLoads = 5
        static let maxThumbnailCacheSize = 100
        /// 缩略图尺寸（减小以节省内存）
        static let thumbnailSize = CGSize(width: 40, height: 40)
        static let maxVisiblePhotos = 500
        /// 白色计数文字需要 ≥4.5:1 对比度，系统 .blue 只有 3.6–4.0:1
        static let countBadgeColor = Color(red: 0.04, green: 0.35, blue: 0.71)
    }

    // MARK: - Map Region
    enum MapRegion {
        static let singlePhotoSpan = MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
        static let multiPhotoMarginFactor = 1.3
        static let minimumSpan = 0.01
    }
}
