//
//  Localization.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-31.
//

import SwiftUI

/// Type-safe localization keys
enum L10n {
    // MARK: - App General
    static let appTitle = LocalizedStringKey("app.title")
    static let done = LocalizedStringKey("done")
    static let loading = LocalizedStringKey("loading")
    static let all = LocalizedStringKey("all")

    // MARK: - Map Display Mode
    enum DisplayMode {
        static let photo = LocalizedStringKey("display.mode.photo")
        static let heatmap = LocalizedStringKey("display.mode.heatmap")
        static let cluster = LocalizedStringKey("display.mode.cluster")
    }

    // MARK: - Map Style
    enum MapStyle {
        static let explore = LocalizedStringKey("map.style.explore")
        static let satellite = LocalizedStringKey("map.style.satellite")
        static let hybrid = LocalizedStringKey("map.style.hybrid")
    }

    // MARK: - Year Filter
    enum YearFilter {
        static let title = LocalizedStringKey("year.filter.title")
        static let all = LocalizedStringKey("year.filter.all")
    }

    // MARK: - Sidebar
    enum Sidebar {
        static let displayMode = LocalizedStringKey("sidebar.display.mode")
        static let mapStyle = LocalizedStringKey("sidebar.map.style")
    }

    // MARK: - Photo Detail
    enum PhotoDetail {
        static let title = LocalizedStringKey("photo.detail.title")
        static let locationInfo = LocalizedStringKey("photo.detail.location.info")
        static let photoProperties = LocalizedStringKey("photo.detail.photo.properties")
        static let mapLocation = LocalizedStringKey("photo.detail.map.location")

        static let latitude = LocalizedStringKey("photo.detail.latitude")
        static let longitude = LocalizedStringKey("photo.detail.longitude")
        static let creationTime = LocalizedStringKey("photo.detail.creation.time")
        static let width = LocalizedStringKey("photo.detail.width")
        static let height = LocalizedStringKey("photo.detail.height")
        static let mediaType = LocalizedStringKey("photo.detail.media.type")
    }

    // MARK: - Media Types
    enum MediaType {
        static let image = LocalizedStringKey("media.type.image")
        static let video = LocalizedStringKey("media.type.video")
        static let audio = LocalizedStringKey("media.type.audio")
    }

    // MARK: - Photo Count
    enum PhotoCount {
        static let photos = String.localized("photo.count.photos")
        static let videos = String.localized("photo.count.videos")
        static let separator = String.localized("photo.count.separator")
    }

    // MARK: - Loading States
    enum Loading {
        static let photos = LocalizedStringKey("loading.photos")
        static let locations = LocalizedStringKey("loading.locations")
    }

    // MARK: - Settings
    enum Settings {
        static let title = LocalizedStringKey("settings.title")
        static let defaultsHeader = LocalizedStringKey("settings.defaults.header")
        static let defaultsFooter = LocalizedStringKey("settings.defaults.footer")
        static let defaultDisplayMode = LocalizedStringKey("settings.default.display.mode")
        static let defaultMapStyle = LocalizedStringKey("settings.default.map.style")
        static let startYearHeader = LocalizedStringKey("settings.start.year.header")
        static let startYearFooter = LocalizedStringKey("settings.start.year.footer")
        static let enableStartYear = LocalizedStringKey("settings.enable.start.year")
        static let startYear = LocalizedStringKey("settings.start.year")
        static let defaultYearHeader = LocalizedStringKey("settings.default.year.header")
        static let defaultYearFooter = LocalizedStringKey("settings.default.year.footer")
        static let enableDefaultYear = LocalizedStringKey("settings.enable.default.year")
        static let defaultYear = LocalizedStringKey("settings.default.year")
        static let aboutHeader = LocalizedStringKey("settings.about.header")
        static let version = LocalizedStringKey("settings.version")
        static let resetDefaults = LocalizedStringKey("settings.reset.defaults")
    }
}

/// String helper for non-View contexts
extension String {
    static func localized(_ key: String) -> String {
        NSLocalizedString(key, comment: "")
    }
}
