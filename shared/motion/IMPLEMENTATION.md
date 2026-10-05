# Motion implementation

[简体中文](IMPLEMENTATION_ZH.md)

`tokens.json` is the source of duration, particle budget and geometry constants. Run `python3 scripts/generate-motion-tokens.py` from the repository root after changing it. Both platform build scripts check that their committed constants match. `particle-frames.tsv` is a shared numerical contract, exercised by Kotlin and Swift tests.

The Apple implementation is shared by iOS, iPadOS and macOS. Android reuses the same Compose components across destinations. Rendering stays native: SwiftUI Canvas and Compose GraphicsLayer use a single snapshot of a removed form row. The authoritative list changes immediately; retained rows are inert, bounded to three removals and expire even when scrolled away. Saving progress follows completed items, with a separate indication of ongoing work. Reduced motion removes travel and rotation; backgrounding or leaving a page releases animations.

Android settings use `sharedBounds` inside Navigation Compose, including gesture seeking and cancellation. Draft confirmation remains separate. Appearance changes persist before a settings-only snapshot is revealed; photo workspaces are never captured. Bottom navigation uses Material 3 items over an SDR photo material, RenderEffect blur on API 31+, and AGSL edge refraction on API 33+. The selected capsule moves with an interruptible spring. Older systems and the disabled glass preference retain a solid native bar. This photo-derived material does not sample the HDR viewport or claim to reproduce Apple's system Liquid Glass.

## Source references

These repositories were studied at the following revisions. This change includes independently implemented components and no copied upstream source files, assets, rendering engines or dependencies.

| Reference | Revision | Studied implementation |
| --- | --- | --- |
| [Nagram](https://github.com/NextAlone/Nagram) | `b8db62a65e1e4dee34d92bff412548ef628ddb06` | `ThanosEffect.java`, `thanos_vertex.glsl`, `RadialProgressView.java`, `RadialProgress2.java`: content fragments, sweeping onset, progress interpolation. |
| [Nagram iOS](https://github.com/NextAlone/Nagram-iOS) | `5b72e0fb7dcbc762568f567c823485e42d32e4d7` | `DustEffectLayer.swift`, `DustEffectShaders.metal`, `RadialProgressContentNode.swift`: textured particles, bounded lifetime, animation teardown. |
| [Telegram Desktop](https://github.com/telegramdesktop/tdesktop) | `d8594c011756265de4385408540bd9f7c787a003` | `thanos_effect_renderer.cpp`, `thanos_init.comp`, `thanos_update.comp`: grid particles and staggered removal. This is the Qt desktop client, including macOS. |
| [FlClash](https://github.com/chen08209/FlClash) | `4b59eca853778d4e7be3de26589252889c899bc5` | `lib/widgets/open_container.dart`: measured source/destination bounds, 300 ms container transform, reversible transition. |
| [AppMarket](https://github.com/YXBwbWFya2V0/AppMarket) | `af34ee9c3b3c8794b27536ae680f4dc3f335f9a9` | `FloatingBottomBar.kt`, `Lens.kt`, `AppNavigation.kt`, `ThemeSettingsScreen.kt`, `App.kt`: backdrop separation, moving selection, navigation and appearance preferences. Its inspected app root uses `ThemeController(System)`; Fuyao's explicit appearance choices and reveal are its own extension. |

AppMarket's private `ApplicationInfo` reflection is not used. Navigation uses the public [shared-element predictive-back API](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation). Snapshots use the public [Compose graphics-layer API](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers). None of the Android navigation or optical code is installed in the Apple client; Apple keeps native navigation and system glass.

Unit tests and builds verify code and numerical bounds. Actual gesture feel, snapshot fidelity, AGSL rendering, accessibility and frame pacing require device acceptance.
