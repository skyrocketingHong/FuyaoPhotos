<p align="center">English | <a href="README_ZH.md">简体中文</a></p>
<h1 align="center">Fuyao Photo Info</h1>
<p align="center">A compact frosted camera-information card, inside the photograph.</p>
<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-AGPL--3.0--only-blue" alt="AGPL-3.0-only">
</p>

An on-device, single-photo Android editor. It reads available EXIF metadata and overlays an editable information card without adding a border or changing the photo dimensions. Current build results and device-validation limits are recorded in [BUILD_STATUS.md](docs/BUILD_STATUS.md).

## Features

- Select a photo through the system picker; normalize all eight EXIF orientations, including mirrored images.
- Edit device, photographer, location, lens, megapixels, equivalent focal length, exposure, aperture and ISO. Missing fields are omitted; location and lens magnification are entered manually.
- Render device and credits in warm yellow and capture parameters in white. Monospaced text stays opaque over the blurred, translucent neutral-gray background.
- Scale card geometry with the photo's short edge. Long text wraps along the same left edge; ordinary one-line credit wrapping preserves the reference card size. Longer content expands upward without shrinking or ellipsizing text.
- Adjust card scale and independent text size (80–180%), opacity, blur, corner radius and right/bottom insets. Default text size remains at the reference 100%; increasing text size preserves card width and expands height only as needed. Compare the original and zoom into a full-screen preview.
- Use locally bundled SF Mono Regular when present, or Android monospace otherwise. Import a custom TTF/OTF/TTC and reset to the default font.
- Export a new original-resolution JPEG (quality parameter 97) or PNG through the same renderer as the preview. Android 10+ saves to `Pictures/FuyaoPhotoInfo`; Android 8/9 uses the system Save As dialog. Share completed exports through the system share sheet.

The 1527 × 859 reference uses a 215 × 168 card, 77 px right / 35 px bottom inset, 20 px corners, 19 px horizontal padding, 10.5 px text, 12.5 px leading and a 7 px group gap. The backdrop starts at `#5A5A5A` / 60% opacity with 25 px approximate blur. These are reproduction settings from the supplied screenshots, not an Apple specification. See [STYLE_SPEC.md](docs/STYLE_SPEC.md).

## Requirements and quick start

- Android 8.0 / API 26 or later.
- JDK 17 or a compatible newer JDK, Android SDK Platform 37, and network access for uncached dependencies.
- Gradle 9.6.1 is provided through the official Wrapper with a pinned distribution checksum.

From the project root:

```bash
# Optional on macOS: copy the local font used by this checkout.
bash scripts/copy-macos-font.sh

# Unit tests, Debug/Release Lint, and Debug/Release APKs.
bash scripts/build-macos.sh
```

The build script recognizes SDK directories named `android-37` or `android-37.0`. Set `JAVA_HOME` and `ANDROID_HOME` when needed. It does not install SDK packages or accept licenses. A failed task exits nonzero; inspect its first error. Extra Gradle arguments can be passed to the script.

The installable Debug artifact is `app/build/outputs/apk/debug/app-debug.apk`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A successful installation prints `Success`; `adb devices` must list an authorized device. To remove this Debug app and its private drafts/settings, use `adb uninstall ing.fuyaoskyrocket.photoinfo.debug`. Exported gallery photos remain. Generated build outputs can be removed with `./gradlew clean`.

## Variants and signing

| Variant | Application ID suffix | Signing |
| --- | --- | --- |
| Debug | `.debug` | Local debug key; installable |
| Debug Unsigned | `.debug.unsigned` | None |
| Release | None | Private key when configured; otherwise unsigned |
| Release Unsigned | `.unsigned` | None |

Tasks are `assembleDebug`, `assembleDebugUnsigned`, `assembleRelease` and `assembleReleaseUnsigned`. Release uses R8 and resource shrinking. Configure a private key through the ignored `signing.properties`, using `signing.properties.example`. Release does not silently fall back to a debug key; unsigned APKs cannot be installed. No private signing key is included.

## Technology and project structure

The package prefix, four variants, shared run configurations and bilingual documentation follow [FuyaoColorPicker](https://github.com/skyrocketingHong/FuyaoColorPicker); the README structure also follows [FuyaoLocale](https://github.com/skyrocketingHong/FuyaoLocale). Their application code and image assets were not copied.

AGP 9.2.1 · Gradle 9.6.1 · Compose compiler 2.4.10 · Compose BOM 2026.06.01 · compile/target SDK 37 · ExifInterface 1.4.2. Application ID: `ing.fuyaoskyrocket.photoinfo`.

| Directory | Responsibility |
| --- | --- |
| `data/photo`, `data/export` | Private drafts, EXIF, decoding, encoding and publication |
| `domain/model`, `metadata`, `layout`, `render` | Fields, formatting, geometry and blur |
| `platform` | Android bitmap compositing and font loading |
| `presentation`, `ui` | Saved editor state, operation sequencing and Material 3 controls |
| `scripts`, `.run`, `.github/workflows` | Local checks, builds and CI definition |
| `references` | Local reference screenshots, excluded from source control |

See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for data flow and restoration boundaries. The legacy `install-workspace.py` helper is for importing an extracted package into another workspace; it is unnecessary when already working in this project.

## Fonts, privacy and output limits

This local checkout contains SF Mono Regular copied from the macOS Terminal bundle. It is loaded automatically and embedded in APKs built from this checkout. The font binary and reference screenshots are excluded from Git and are not covered by the source license. A source checkout without the font remains buildable with Android monospace. Runtime imports (up to 10 MB) remain in app-private storage.

No Internet, location, camera or broad storage permissions are declared. Optional capture metadata uses an allowlist excluding GPS, serial numbers, MakerNote, XMP and thumbnails. Editing the visible card does not rewrite original capture tags. Visible names and places remain part of exported image pixels.

Output is **8-bit sRGB / SDR still imagery**. HDR gain maps, Display P3, high-bit-depth data, Live Photos, RAW processing and animation are not preserved. HEIC decoding depends on the device. JPEG is re-encoded; PNG is lossless only relative to the rendered SDR bitmap. Preview rasterization may differ slightly from full-resolution export.

Insufficient memory produces an error without silently lowering resolution. Input caps of 512 MB / 200 MP do not guarantee that every device can export those sizes. Batch processing, free dragging and background export services are outside this version.

## Validation

Use `bash scripts/build-macos.sh` for host checks and APKs; use `./gradlew :app:connectedDebugAndroidTest` with an authorized device for orientation, pixel-boundary, export-metadata and font-reset tests. `scripts/test-core.sh` is an optional offline route requiring Kotlin CLI. See [VALIDATION.md](docs/VALIDATION.md) for actual coverage and remaining device checks. The GitHub workflow has not been run remotely.

## License

Original source is `AGPL-3.0-only`; see [LICENSE](LICENSE) and [NOTICE](NOTICE). Fonts, screenshots and photographs retain their own rights. This is not an Apple product.
