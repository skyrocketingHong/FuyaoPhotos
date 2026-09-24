import SwiftUI

/// Virtual slots repeat a fixed set of choices; idle recentering never changes the selected item.
struct CameraItemSelector<Item: Hashable & Identifiable>: View {
    let items: [Item]
    @Binding var selection: Item
    let title: (Item) -> LocalizedStringKey
    @State private var position: Int?
    @ScaledMetric(relativeTo:.body) private var rowHeight=56.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let motionIsReduced = reduceMotion
        return GeometryReader { geometry in
            let edgeHeight = max(0, min(56, (geometry.size.height-rowHeight)/2-6))
            ZStack {
                Color.clear
                    .frame(maxWidth: .infinity)
                    .frame(height: rowHeight)
                    .glassEffect(.clear, in: .capsule)
                    .opacity(0.55)
                    .padding(.horizontal, 6)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)

                ScrollView(.vertical) {
                    LazyVStack(spacing:0) {
                        ForEach(0..<(items.count*101),id:\.self) { slot in
                            let item=items[slot%items.count]
                            Button {
                                withAnimation(motionIsReduced ? nil : .snappy) { position=slot }
                            } label: {
                                Text(title(item))
                                    .font(.body.weight(.semibold))
                                    .foregroundStyle(position == slot ? Color.yellow : Color.white.opacity(0.8))
                                    .multilineTextAlignment(.center)
                                    .frame(maxWidth:.infinity).frame(height:rowHeight)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .scrollTransition(.interactive,axis:.vertical) { content,phase in
                                content.opacity(motionIsReduced || phase.isIdentity ? 1 : 0.25)
                            }
                            .id(slot)
                        }
                    }
                    .scrollTargetLayout()
                }
                .safeAreaPadding(.vertical,max(0,(geometry.size.height-rowHeight)/2))
                .scrollTargetBehavior(.viewAligned(limitBehavior:.always,anchor:.center))
                .scrollPosition(id:$position,anchor:.center)
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.immediately)
                .overlay {
                    VStack(spacing: 0) {
                        ProgressiveBackdropEdge(edge: .top, material: .thinMaterial,
                                                reducedTransparencyColor: .black.opacity(0.85))
                            .frame(height: edgeHeight)
                            .environment(\.colorScheme, .dark)
                        Spacer(minLength: 0)
                        ProgressiveBackdropEdge(edge: .bottom, material: .thinMaterial,
                                                reducedTransparencyColor: .black.opacity(0.85))
                            .frame(height: edgeHeight)
                            .environment(\.colorScheme, .dark)
                    }
                    .allowsHitTesting(false)
                }
                .onScrollPhaseChange { _,phase in
                    guard phase == .idle,let position,
                          !(items.count*49..<items.count*52).contains(position) else { return }
                    var transaction=Transaction();transaction.disablesAnimations=true
                    withTransaction(transaction) { self.position=items.count*50+position%items.count }
                }
            }
        }
        .onAppear { if position==nil { position=items.count*50+(items.firstIndex(of:selection) ?? 0) } }
        .onChange(of:position) { _,value in
            if let value { selection=items[value%items.count] }
        }
        .onChange(of:selection) { _,value in
            guard let index=items.firstIndex(of:value),position.map({ $0%items.count }) != index else { return }
            position=items.count*50+index
        }
        .accessibilityElement(children:.ignore)
        .accessibilityLabel(Text("card.edit.field"))
        .accessibilityValue(Text(title(selection)))
        .accessibilityAdjustableAction { direction in
            let current=items.firstIndex(of:selection) ?? 0
            let next=(current+(direction == .increment ? 1 : items.count-1))%items.count
            position=items.count*50+next
        }
        .sensoryFeedback(.selection,trigger:selection)
    }
}
