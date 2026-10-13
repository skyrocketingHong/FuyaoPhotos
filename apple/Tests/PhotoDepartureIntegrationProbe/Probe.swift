import AppKit
import SwiftUI
import MetalKit
import QuartzCore
import Observation

@MainActor @Observable private final class ProbeAppearance {
    var active: ControlActiveState = .inactive
    var locale = "en_US"
}

@MainActor private final class Trace {
    let start = ContinuousClock.now
    var events: [String] = []
    func record(_ stage: String, _ values: [Double]) {
        events.append(stage)
        print("STAGE \(stage) elapsed=\(start.duration(to: .now)) values=\(values)")
        fflush(stdout)
    }
    func has(_ stage: String) -> Bool { events.contains(stage) }
}

private struct ProbeRoot: View {
    let session: CardSession
    let workspace: PhotoWorkspace
    let owner: UUID
    let image: CGImage
    let strings: [String: String]
    let appearance: ProbeAppearance
    let scale: CGFloat
    private func text(_ key: String) -> LocalizedStringKey { LocalizedStringKey(strings[key] ?? key) }
    private var title: String {
        switch workspace.selectedTab { case .cards: "tab.cards"; case .metadata: "metadata.intro.title"; case .colors: "tab.colors" }
    }
    private var description: String {
        switch workspace.selectedTab { case .cards: "card.empty.description"; case .metadata: "metadata.intro.description"; case .colors: "colors.description" }
    }
    var body: some View {
        NavigationStack {
        Group {
            if session.current != nil {
                Image(decorative: image, scale: 1).resizable().scaledToFit()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frame = $0 }
                    .preference(key: PhotoViewportFrameKey.self, value: frame)
            } else {
                PhotoImportPage(title: text(title), description: text(description), symbol: "photo.badge.plus", busy: false) {
                    PhotoImportAction(title: text("card.open"), symbol: "photo.badge.plus", prominence: .primary) {}
                    if workspace.selectedTab == .cards {
                        PhotoImportAction(title: text("package.import.action"), symbol: "square.and.arrow.down") {}
                    }
                    if workspace.selectedTab == .colors {
                        PhotoImportAction(title: text("colors.files"), symbol: "folder") {}
                        PhotoImportAction(title: text("colors.camera"), symbol: "camera") {}
                    }
                }
            }
        }
        .modifier(PhotoDepartureOverlay(session: session, page: workspace.selectedTab, captureOwner: owner,
            viewportCapture: { image }))
        .navigationTitle("Photo Import Integration Probe")
        .toolbar {
            if session.current != nil {
                ToolbarItem { Button("Close Synthetic Photo") { try? session.close(owner: owner) } }
            }
        }
        }
        .environment(workspace)
        .environment(\.scenePhase, .active)
        .environment(\.colorScheme, .light)
        .environment(\.displayScale, scale)
        .environment(\.locale, Locale(identifier: appearance.locale))
        .environment(\.controlActiveState, appearance.active)
    }
    @State private var frame = CGRect.zero
}

// This verifies production stage transitions, not automatic frame scheduling or visual appearance.
@main struct IntegratedPhotoDepartureProbe {
    @MainActor private static func sourceImage() throws -> CGImage {
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(data: nil, width: 800, height: 600, bitsPerComponent: 8,
                bytesPerRow: 0, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { throw ProbeError.check("cannot make synthetic image") }
        context.setFillColor(CGColor(red: 0.15, green: 0.35, blue: 0.70, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: 800, height: 600))
        guard let image = context.makeImage() else { throw ProbeError.check("cannot freeze synthetic image") }
        return image
    }
    @MainActor private static func metalViews(in view: NSView) -> [MTKView] {
        (view as? MTKView).map { [$0] } ?? view.subviews.flatMap { metalViews(in: $0) }
    }
    @MainActor private static func pump(_ host: NSView, manual: Bool = false) async {
        host.layoutSubtreeIfNeeded()
        host.displayIfNeeded()
        if manual {
            for view in metalViews(in: host) where !view.isPaused { view.draw() }
        }
        CATransaction.flush()
        try? await Task.sleep(for: .milliseconds(8))
    }
    @MainActor static func main() async {
        NSApplication.shared.setActivationPolicy(.prohibited)
        guard !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion else {
            print("SKIP integration probe: system Reduce Motion is enabled")
            return
        }
        guard MTLCreateSystemDefaultDevice() != nil else {
            print("SKIP integration probe: Metal is unavailable")
            return
        }
        let arguments = Array(CommandLine.arguments.dropFirst())
        let page = arguments.dropFirst().first.flatMap(PhotoWorkspace.Tab.init(rawValue:)) ?? .cards
        let width = arguments.count > 2 ? Double(arguments[2]) ?? 700 : 700
        let scale = CGFloat(Double(ProcessInfo.processInfo.environment["PROBE_DISPLAY_SCALE"] ?? "2") ?? 2)
        let trace = Trace()
        let appearance = ProbeAppearance()
        PhotoDepartureDiagnostics.observer = { stage, values in
            trace.record(stage, values)
            if stage == "target.request", arguments.contains("--change-active") {
                Task { @MainActor in
                    await Task.yield()
                    trace.record("probe.active-change", [])
                    appearance.active = .key
                }
            }
            if stage == "target.request", arguments.contains("--change-locale") {
                Task { @MainActor in
                    await Task.yield()
                    trace.record("probe.locale-change", [])
                    appearance.locale = "zh_CN"
                }
            }
        }
        print("PROBE page=\(page.rawValue) width=\(width) scale=\(scale) Metal=\(MTLCreateSystemDefaultDevice() != nil)")
        do {
            guard let resources = arguments.first else { throw ProbeError.check("missing resource directory") }
            let stringsURL = URL(fileURLWithPath: resources).appendingPathComponent("en.lproj/Localizable.strings")
            let strings = try PropertyListSerialization.propertyList(from: Data(contentsOf: stringsURL), format: nil) as? [String: String] ?? [:]
            let image = try sourceImage()
            let session = CardSession(image: image)
            let workspace = PhotoWorkspace(page)
            let owner = UUID()
            let root = ProbeRoot(session: session, workspace: workspace, owner: owner, image: image, strings: strings,
                appearance: appearance, scale: scale)
            let bounds = CGRect(x: 0, y: 0, width: width, height: 700)
            let host = NSHostingView(rootView: root)
            host.frame = bounds
            let window = NSWindow(contentRect: bounds, styleMask: [.titled, .closable, .resizable], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.appearance = NSAppearance(named: .aqua)
            window.contentView = host
            defer { window.contentView = nil; window.close() }
            let sourceDeadline = ContinuousClock.now.advanced(by: .seconds(2))
            while (session.lastRegisteredFrame.isEmpty || !trace.has("source.registered")), .now < sourceDeadline {
                await pump(host)
            }
            try session.close(owner: owner)
            let deadline = ContinuousClock.now.advanced(by: .seconds(3))
            var firstMetal: ContinuousClock.Instant?
            var manualStarted = false
            while .now < deadline, !trace.has("departure.finished") {
                if let view = metalViews(in: host).first, firstMetal == nil {
                    firstMetal = .now
                    trace.record("probe.surface-state", [view.alphaValue, Double(view.layer?.opacity ?? -1),
                        view.isHiddenOrHasHiddenAncestor ? 1 : 0, window.occlusionState.contains(.visible) ? 1 : 0,
                        view.bounds.width, view.bounds.height])
                }
                let manual = !arguments.contains("--automatic-only") && firstMetal.map {
                    $0.duration(to: .now) >= .milliseconds(350)
                } == true
                if manual, !manualStarted {
                    manualStarted = true
                    trace.record("probe.manual-draw-start", [trace.has("renderer.ready") ? 1 : 0])
                }
                await pump(host, manual: manual)
            }
            guard trace.has("target.ready") else {
                throw ProbeError.check("live page did not submit a target; last stage=\(trace.events.last ?? "none")")
            }
            guard trace.has("renderer.requested") else { throw ProbeError.check("overlay did not receive target") }
            guard trace.has("renderer.finished") else {
                throw ProbeError.check("GPU did not complete in hidden host; stages=\(trace.events.joined(separator: ","))")
            }
            print("PASS real PhotoImportPage + PhotoDepartureOverlay reached target ready and GPU terminal; manual-driver=\(manualStarted)")
        } catch {
            print("FAIL \(error)")
            exit(1)
        }
    }
}
