# Native motion, navigation and appearance

[简体中文](IMPLEMENTATION_ZH.md)

## Upstream implementations

| Feature | Implementation and source |
| --- | --- |
| Android particles | Original `thanos_vertex.glsl` and `thanos_fragment.glsl` from [Nagram](https://github.com/NextAlone/Nagram/tree/b8db62a65e1e4dee34d92bff412548ef628ddb06), with a TextureView/EGL bridge ported from `ThanosEffect.java`. GPU transform feedback retains particle UV, position, velocity and lifetime in two 28-byte buffers. Original equations, density scaling, random lifetime and photo-mode settings are retained. |
| Apple particles | Original `DustEffectShaders.metal`, `loki.metal` and `loki_header.metal` from [Nagram-iOS](https://github.com/NextAlone/Nagram-iOS/tree/5b72e0fb7dcbc762568f567c823485e42d32e4d7). A shared iOS/iPadOS/macOS MTKView bridge replaces Telegram's app-specific MetalEngine. The original compute and instanced-quad rendering run unchanged; exact dispatch prevents initialization beyond the particle buffer. |
| Android liquid tabs | Actual Kyant Backdrop 1.0.6 and Shapes 1.2.0 libraries, with the official [AndroidLiquidGlass catalog](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog) component port. Lens refraction, blur and combined backdrop rendering call the upstream library. |
| MD3 blur and themes | The native Material 3 bar uses the same Backdrop blur independently of liquid tabs. Material Color Utilities 5.0.1 generates all HCT color roles. Appearance, wallpaper/custom seed, palette, specification, contrast, pure black, bar materials and back transitions have a dedicated settings category. |
| Settings and back | FlClash's [OpenContainer](https://github.com/chen08209/FlClash/blob/4b59eca853778d4e7be3de26589252889c899bc5/lib/widgets/open_container.dart) informs the measured container transition. [IntentX](https://github.com/wxxsfxyzm/IntentX/tree/5870f2f849c4b17518444518f9bc8a8f7e2972e7) and [AppMarket](https://github.com/YXBwbWFya2V0/AppMarket/tree/af34ee9c3b3c8794b27536ae680f4dc3f335f9a9) inform theme and material controls. Navigation Compose drives gesture progress and cancellation; no private ApplicationInfo reflection is used. |

Telegram Desktop's Qt particle renderer at `d8594c011756265de4385408540bd9f7c787a003` remains a reference. The Apple client uses the original Metal renderer across its platforms. The earlier coarse Canvas particle approximation and guessed color-space matching have been removed.

## Photo and state boundaries

Cards, Metadata and Colors offer a close action. Dirty sessions keep their discard confirmation. Closing clears only the owning private editing session; originals and exported files are preserved. Shared photo pages resolve the actual session owner. Replacing photos first decodes a usable new preview, then publishes the replacement and emits the old image's departure. Cancellation and failed preparation retain the old session and metadata drafts.

Particle frames are independent SDR copies. Native HDR presentation and export resources are not flattened. Current zoom, pan, depth presentation and the information-card overlay are retained. The previous image stays visible until the GPU's first frame. The data operation does not wait for the animation. Reduced motion, backgrounding, disposal and deadlines release visual resources. Android replacement events follow the current UI host after activity recreation; stale metadata saves cannot restore a discarded draft.

Each safe backdrop layer registers separately with priority and coordinates. Only the SDR ambient background and ordinary controls/lists are sampled. Metadata and color value lists can scroll behind the bottom bar, with content padding keeping their last actions reachable. Fixed photos, current-pixel summaries and selectors remain above the bar. Insets are consumed once.

## Shared parameters and verification

`tokens.json` and `scripts/generate-motion-tokens.py` retain common control/progress timing and snapshot limits. Both build scripts check the generated Kotlin/Swift constants. Particle physics come from the original platform shaders, not from the common UI tokens.

Source-hash tests verify the imported shaders. Apple tests exercise real offscreen Metal initialization, update bounds, lifetime, image orientation and alpha; hosts without a Metal device explicitly skip GPU tests. Android source tests cover shader identity and grid allocation, with device tests for source-pixel decoding and replacement safety. Source color readouts keep original floating-point components; explicit spaces convert the same sample, with only 8-bit RGB/HEX clipped.

Original particle licenses are in `LICENSES/`. Navigation versions, authors, adaptations and licenses are in `third_party/AndroidNavigation/`, with Android notices included in the APK. Device visual fidelity, Android GPU execution, gestures, accessibility and frame pacing remain separate acceptance checks.
