import SwiftUI

struct ProgressiveBackdropEdge: View {
    let edge: Edge
    var material: Material = .ultraThinMaterial
    var reducedTransparencyColor: Color = .primary.opacity(0.12)

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        Group {
            if reduceTransparency {
                Rectangle().fill(reducedTransparencyColor)
            } else {
                Rectangle().fill(material)
            }
        }
        .mask {
            LinearGradient(stops: [
                .init(color: .white, location: 0),
                .init(color: .white, location: 0.12),
                .init(color: .clear, location: 1)
            ], startPoint: edge.startPoint, endPoint: edge.endPoint)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct ProgressiveBackdropEdges: View {
    var top: CGFloat = 96
    var bottom: CGFloat = 88
    var sides: CGFloat = 32

    var body: some View {
        GeometryReader { geometry in
            let topHeight = min(top, geometry.size.height * 0.25)
            let bottomHeight = min(bottom, geometry.size.height * 0.25)
            let sideWidth = min(sides, geometry.size.width * 0.12)

            VStack(spacing: 0) {
                ProgressiveBackdropEdge(edge: .top)
                    .frame(height: topHeight)
                HStack(spacing: 0) {
                    ProgressiveBackdropEdge(edge: .leading)
                        .frame(width: sideWidth)
                    Spacer(minLength: 0)
                    ProgressiveBackdropEdge(edge: .trailing)
                        .frame(width: sideWidth)
                }
                .frame(maxHeight: .infinity)
                ProgressiveBackdropEdge(edge: .bottom)
                    .frame(height: bottomHeight)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

private extension Edge {
    var startPoint: UnitPoint {
        switch self {
        case .top: .top
        case .bottom: .bottom
        case .leading: .leading
        case .trailing: .trailing
        }
    }

    var endPoint: UnitPoint {
        switch self {
        case .top: .bottom
        case .bottom: .top
        case .leading: .trailing
        case .trailing: .leading
        }
    }
}
