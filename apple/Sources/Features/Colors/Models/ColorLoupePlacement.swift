import Foundation

nonisolated struct ColorLoupePlacement {
    let center: CGPoint
    let diameter: CGFloat
    let belowFinger: Bool

    init(finger: CGPoint, panel: CGSize) {
        let inset = min(8, max(0, min(panel.width, panel.height) / 4))
        diameter = max(0, min(100, min(panel.width, panel.height) - inset * 2))
        let radius = diameter / 2
        belowFinger = finger.y - 78 - radius < inset
        center = CGPoint(
            x: min(max(radius + inset, finger.x), max(radius + inset, panel.width - radius - inset)),
            y: min(max(radius + inset, finger.y + (belowFinger ? 78 : -78)),
                   max(radius + inset, panel.height - radius - inset)))
    }
}
