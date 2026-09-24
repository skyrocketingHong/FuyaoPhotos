//
//  AppSettings.swift
//  Photo Map
//
//  Created by skyrocketing Hong on 2025-12-31.
//

import Foundation
import SwiftUI

@Observable
class AppSettings {
    static let shared = AppSettings()

    var mapOptions: MapOptions = {
        guard let data = UserDefaults.standard.data(forKey: "mapOptions"),
              let value = try? JSONDecoder().decode(MapOptions.self, from: data) else {
            var defaults = MapOptions()
            if let raw = UserDefaults.standard.string(forKey: "defaultMapStyle"),
               let style = MapStyleMode(rawValue: raw) { defaults.style = style }
            return defaults
        }
        return value
    }() {
        didSet {
            if let data = try? JSONEncoder().encode(mapOptions) {
                UserDefaults.standard.set(data, forKey: "mapOptions")
            }
        }
    }

    // MARK: - Settings Properties

    /// 默认展示模式（缺省 photo）
    var defaultDisplayMode: MapDisplayMode {
        get {
            if let rawValue = UserDefaults.standard.string(forKey: "defaultDisplayMode"),
               let mode = MapDisplayMode(rawValue: rawValue) {
                return mode
            }
            return .photo
        }
        set {
            UserDefaults.standard.set(newValue.rawValue, forKey: "defaultDisplayMode")
        }
    }

    /// 默认地图样式（缺省 explore）
    var defaultMapStyle: MapStyleMode {
        get {
            if let rawValue = UserDefaults.standard.string(forKey: "defaultMapStyle"),
               let style = MapStyleMode(rawValue: rawValue) {
                return style
            }
            return .explore
        }
        set {
            UserDefaults.standard.set(newValue.rawValue, forKey: "defaultMapStyle")
        }
    }

    /// 自定义起始年份（用于年份筛选菜单的起点）
    var customStartYear: Int? {
        get {
            let year = UserDefaults.standard.integer(forKey: "customStartYear")
            return year == 0 ? nil : year
        }
        set {
            UserDefaults.standard.set(newValue ?? 0, forKey: "customStartYear")
        }
    }

    /// 默认选中的年份（应用启动时自动筛选的年份）
    var defaultSelectedYear: Int? {
        get {
            let year = UserDefaults.standard.integer(forKey: "defaultSelectedYear")
            return year == 0 ? nil : year
        }
        set {
            UserDefaults.standard.set(newValue ?? 0, forKey: "defaultSelectedYear")
        }
    }

    /// 获取年份范围（从自定义起始年份或照片最早年份到当前年份）
    func getYearRange(photosEarliestYear: Int?) -> [Int] {
        let currentYear = Calendar.current.component(.year, from: Date())

        let startYear: Int
        if let customYear = customStartYear {
            startYear = customYear
        } else if let photoYear = photosEarliestYear {
            startYear = photoYear
        } else {
            return []
        }

        guard startYear > 0, startYear <= currentYear else { return [] }
        return Array(startYear...currentYear).reversed()
    }

    private init() {}

    // MARK: - Convenience Methods

    func resetToDefaults() {
        mapOptions = MapOptions()
        defaultDisplayMode = .photo
        defaultMapStyle = .explore
        customStartYear = nil
        defaultSelectedYear = nil
    }
}
