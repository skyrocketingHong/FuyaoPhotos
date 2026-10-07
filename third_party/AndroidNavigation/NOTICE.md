# Android navigation and themes

The Android navigation renderer links `io.github.kyant0:backdrop:1.0.6` and
`io.github.kyant0:shapes:1.2.0`, by Kyant, under Apache-2.0. The liquid tabs,
interactive highlight and damped drag code in `ui/components/liquid/` are
adapted from the official AndroidLiquidGlass catalog at commit
`896a94a3ade1cc1a940b92365f942a34971fecda` (tag `1.0.6`).

Source: https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog
Shapes: https://github.com/Kyant0/Shapes

Changes to the catalog components include Material 3 theme colors and semantic
typography, reduced-motion handling, selected-tab accessibility, disabled actions,
selection cancellation and synchronization with Navigation Compose. Refraction,
blur, chromatic dispersion, highlights and combined backdrop rendering use the
upstream Backdrop library. Each registered safe page layer has its own source;
the HDR photo viewport is not registered or flattened into a backdrop.

The selected Android-only version uses the existing AndroidX Compose stack. The
2.x multiplatform library would introduce a newer Compose dependency family;
this integration does not require a Compose-wide upgrade.

Material theme generation links `com.materialkolor:material-color-utilities:5.0.1`,
by Jordon de Hoog, under MIT. Its underlying Google Material Color Utilities
algorithms, Copyright 2021-2022 Google LLC, are Apache-2.0. All color roles are generated
from HCT palettes, contrast and the selected 2021 or 2025 specification. The
Compose-free artifact avoids a second Compose runtime.

Source: https://github.com/jordond/MaterialKolor
Original algorithms: https://github.com/material-foundation/material-color-utilities

Theme choices and the separation of navigation materials from back transitions
were informed by IntentX at commit `5870f2f849c4b17518444518f9bc8a8f7e2972e7`
(GPL-3.0) and AppMarket. IntentX source code is not copied. Fuyao Photos uses
Navigation Compose's seekable transitions rather than replacing its navigator
with IntentX's Miuix navigation implementation.

References:
- https://github.com/wxxsfxyzm/IntentX/tree/5870f2f849c4b17518444518f9bc8a8f7e2972e7
- https://github.com/YXBwbWFya2V0/AppMarket

License texts are included here and in the Android APK's `assets/licenses/`.
