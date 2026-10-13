# Native motion, navigation and appearance

[简体中文](IMPLEMENTATION_ZH.md)

## Upstream implementations

| Feature | Implementation and source |
| --- | --- |
| Android departure dust | Photo replacement and lens-row deletion retain the original `thanos_vertex.glsl` and `thanos_fragment.glsl` from [Nagram](https://github.com/NextAlone/Nagram/tree/b8db62a65e1e4dee34d92bff412548ef628ddb06), with a TextureView/EGL bridge ported from `ThanosEffect.java`. GPU transform feedback retains particle UV, position, velocity and lifetime in two 28-byte buffers. Original equations, density scaling, random lifetime and photo-mode settings are retained. |
| Apple departure dust | Photo replacement and lens-row deletion retain the original `DustEffectShaders.metal`, `loki.metal` and `loki_header.metal` from [Nagram-iOS](https://github.com/NextAlone/Nagram-iOS/tree/5b72e0fb7dcbc762568f567c823485e42d32e4d7). A shared iOS/iPadOS/macOS MTKView bridge replaces Telegram's app-specific MetalEngine. The original compute and instanced-quad rendering run unchanged; exact dispatch prevents initialization beyond the particle buffer. |
| Photo close reassembly | A separate Fuyao implementation disperses the visible photo and gathers it into a capture of the native import card. Android uses OpenGL ES 3 instanced quads; Apple uses a separate Metal quad pipeline. These analytic close shaders do not replace or modify the pinned upstream dust shaders. |
| Android liquid tabs | Actual Kyant Backdrop 1.0.6 and Shapes 1.2.0 libraries, with the official [AndroidLiquidGlass catalog](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog) component port. Lens refraction, blur and combined backdrop rendering call the upstream library. |
| MD3 blur and themes | The native Material 3 bar uses the same Backdrop blur independently of liquid tabs. Material Color Utilities 5.0.1 generates all HCT color roles. Appearance, wallpaper/custom seed, palette, specification, contrast, pure black, bar materials and back transitions have a dedicated settings category. |
| Settings and back | FlClash's [OpenContainer](https://github.com/chen08209/FlClash/blob/4b59eca853778d4e7be3de26589252889c899bc5/lib/widgets/open_container.dart) informs the measured container transition. [IntentX](https://github.com/wxxsfxyzm/IntentX/tree/5870f2f849c4b17518444518f9bc8a8f7e2972e7) and [AppMarket](https://github.com/YXBwbWFya2V0/AppMarket/tree/af34ee9c3b3c8794b27536ae680f4dc3f335f9a9) inform theme and material controls. Navigation Compose drives gesture progress and cancellation; no private ApplicationInfo reflection is used. |

Telegram Desktop's Qt particle renderer at `d8594c011756265de4385408540bd9f7c787a003` remains a reference. The Apple client shares the original dust renderer and the separate Fuyao close renderer across its platforms. The earlier coarse Canvas particle approximation and guessed color-space matching have been removed.

## Photo and state boundaries

Cards, Metadata and Colors offer a close action. Dirty sessions keep their discard confirmation. Closing clears only the owning private editing session; originals and exported files are preserved. Shared photo pages resolve the actual session owner. Replacing photos first decodes a usable new preview, then publishes the replacement and emits the old image's departure. Cancellation and failed preparation retain the old session and metadata drafts.

Particle frames are independent SDR copies. Native HDR presentation and export resources are not flattened. Current zoom, pan, depth presentation and the information-card overlay are retained. The previous image stays visible until the GPU's first frame. The data operation does not wait for the animation. Reduced motion, backgrounding, disposal and deadlines release visual resources. Android replacement events follow the current UI host after activity recreation; stale metadata saves cannot restore a discarded draft.

On Apple platforms, photo departures carry an owner, a single consumption ticket and an absolute monotonic deadline. A close action passes its capture owner explicitly; replacement events retain eligible-capture selection. Only the selected, visible photo page registers a capture or consumes its event. A root-tab change cancels visual events synchronously, and returning to a retained tab cannot start an old event again. A native renderer can be created only once for the copy. Multiple-window interaction remains a separate device acceptance check.

Each safe backdrop layer registers separately with priority and coordinates. Only the SDR ambient background and ordinary controls/lists are sampled. Metadata and color value lists can scroll behind the bottom bar, with content padding keeping their last actions reachable. Fixed photos, current-pixel summaries and selectors remain above the bar. Insets are consumed once.

## Close-to-placeholder reassembly

Closing a photo creates a visual request, then immediately continues the real session cleanup. The source copy stays visible while the destination is prepared. The target is the actual Cards, Metadata or Colors import card for the initiating page, independently of which shared session owns its data.

- Android records the normal `PhotoImportCard` drawing into a `GraphicsLayer` and captures its measured root bounds after a ready frame. It waits for `busy == false`, so cleanup's temporary loading state is not used as the destination. The card is recorded before its final drawing is withheld; it is not captured with zero alpha or disabled controls. Metadata registers the visible bitmap on the stable viewport and updates its geometry directly from layout callbacks. Hardware bitmaps are copied before software snapshot composition without modifying the original.
- Apple renders the same native `Form` in an attached native capture host, using the measured size, color scheme, locale and Dynamic Type environment. Comparing against the empty native Form identifies the section surface around the measured content, including native insets and corners. It does not substitute a hand-drawn rectangle. The request and capture owner must still match when the result arrives.

Both paths use an 880 ms analytic transition, a 650 ms target-capture deadline and a 2400 ms total request lifetime. A grid starts from 3 logical-unit cells and is capped at 24,000 particles. Travel uses `smoothstep(0.22, 1, t)`, stable two-dimensional scatter uses `sin(pi * t)`, and source/target premultiplied textures mix over `smoothstep(0.35, 0.88, t)`. Cell size follows the source/target geometry and contracts by `1 - 0.55 * sin(pi * t)` during flight. Endpoints explicitly have zero scatter and full cell size, reproducing the source at `t = 0` and target at `t = 1`.

The native destination's input and accessibility are hidden until handoff. Android places `focusProperties` before `focusGroup`, so `onEnter` blocks entry at the group's focus target. Android waits for the terminal buffer's EGL presentation timestamp to be observed by `SurfaceTexture`; Apple hands off after the terminal GPU frame completes. The same state change removes the visual copy and reveals the real card, with no second fade. A canvas-height change during keyboard dismissal updates the viewport instead of blindly ending playback; actual target relayout, cancellation, a new import, backgrounding, reduced motion, capture failure, renderer failure or expiry falls back to the native placeholder. Stale generations cannot replace or finish a newer request.

## Shared parameters and verification

`tokens.json` and `scripts/generate-motion-tokens.py` retain common control/progress timing, snapshot limits and the Fuyao reassembly contract. Both build scripts check the generated Kotlin/Swift constants. The preserved dust paths still use their original platform shader physics.

Source-hash tests verify the imported shaders. Reassembly tests cover endpoint coefficients, grid limits, stale requests and deadlines. Apple tests include offscreen Metal initialization, update bounds, lifetime, image orientation and alpha; hosts without a Metal device explicitly skip GPU tests. Android device tests include ES 3 endpoint readback for rectangle placement, texture orientation and premultiplied alpha, plus hardware-bitmap snapshot handling. Compiling those tests does not execute Android GPU rendering. Existing source-pixel decoding and replacement-safety regressions remain. Source color readouts keep original floating-point components; explicit spaces convert the same sample, with only 8-bit RGB/HEX clipped.

Original particle licenses are in `LICENSES/`. Navigation versions, authors, adaptations and licenses are in `third_party/AndroidNavigation/`, with Android notices included in the APK. Device visual fidelity, Android GPU execution, gestures, accessibility and frame pacing remain separate acceptance checks.
