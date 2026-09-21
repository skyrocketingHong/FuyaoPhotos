<p align="center">English | <a href="README_ZH.md">简体中文</a></p>
<p align="center"><img src="assets/readme/app-icon.svg" width="112" height="112" alt="Fuyao Photo Info app icon"></p>
<h1 align="center">Fuyao Photo Info</h1>
<p align="center">A compact frosted camera-information card, inside the photograph.</p>
<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-AGPL--3.0--only-blue" alt="AGPL-3.0-only">
</p>

## Download

Download the [latest release](https://github.com/skyrocketingHong/FuyaoPhotoInfo/releases/latest). Choose the **universal** APK if unsure of your device architecture. Requires Android 8.0 or later; HDR display requires Android 14+ and compatible hardware.

## Showcase

### Exported sample

Hong Kong waterfront · Xiaomi 17 Ultra · Leica 75–100mm telephoto. Click to view the **4080 × 3072** original-size export.

<p align="center">
  <a href="assets/readme/sample-hong-kong.jpg"><img src="assets/readme/sample-hong-kong.jpg" width="960" alt="Hong Kong waterfront photo with a rounded information card in the lower-right corner, showing Xiaomi 17 Ultra, Leica 75–100mm telephoto and capture details"></a>
</p>

### Apple keynote references

Apple keynote screenshots show the visual reference for the information card. Each reference is **2560 × 1440**; click a screenshot to view it at full size.

<table>
  <tr>
    <td align="center"><a href="assets/readme/apple-keynote-portrait.png"><img src="assets/readme/apple-keynote-portrait.png" width="300" alt="Apple keynote reference: portrait with a lower-right information card"></a><br>Portrait</td>
    <td align="center"><a href="assets/readme/apple-keynote-night-sky.png"><img src="assets/readme/apple-keynote-night-sky.png" width="300" alt="Apple keynote reference: night sky with a lower-right information card"></a><br>Night sky</td>
    <td align="center"><a href="assets/readme/apple-keynote-stairs.png"><img src="assets/readme/apple-keynote-stairs.png" width="300" alt="Apple keynote reference: staircase scene with a lower-right information card"></a><br>Staircase</td>
  </tr>
</table>

Apple and the credited photographers retain their respective image rights. See [media sources and rights](assets/readme/README.md) and the [Apple-related notice](#apple-related-notice).

## Features

- **Photo information cards** — Display device, photographer, location, lens and capture parameters in a rounded, frosted card.
- **EXIF metadata** — Read available photo metadata, resolve locations from photo GPS and edit every displayed field.
- **Batch editing** — Select up to 50 photos, swipe between them and save the full selection. Each photo keeps its own information and style.
- **Style controls** — Adjust card size, text size, opacity, blur, corners and margins; import TTF/OTF/TTC fonts.
- **Save preferences** — Set default format, JPEG quality, EXIF details, location and capture-time retention. Override them for one save without changing defaults.
- **Gallery integration** — Share one or multiple photos into the editor; open or share completed exports.
- **Photographer and lens profiles** — Save a default credit and configure lens names, focal ranges and zoom values.
- **Media preview** — Play embedded Motion Photo video and switch HDR display from compact icon controls.
- **Preview and export** — Compare with the original, inspect at full resolution and export JPEG/PNG at the original dimensions. JPEG quality defaults to 100 and is adjustable from 0 to 100.
- **HDR and Motion Photos** — Preserve supported JPEG Ultra HDR gainmaps and Motion Photo video/audio when editing the cover.

## Usage

1. Open one photo, select multiple photos, or share images from your gallery to **Fuyao Photo Info**. Use **Import from Files (original)** for HDR/Motion Photos.
2. Edit the information and card style. Swipe horizontally to switch photos in a batch.
3. Choose **Save**, select the format, JPEG quality and metadata options, then save the full selection.

On Android 10+, exports are saved to `Pictures/FuyaoPhotoInfo`. Android 8/9 uses a file picker for one photo or a folder picker for a batch. Original files remain unchanged; completed exports can be shared from the app.

Save global defaults in **Settings → Default save options**. Each save starts with these defaults; temporary choices apply to that save only. The completion notice offers **Open** (the last successful export) and **Share** (all successful exports).

Set a default photographer in **Settings**. The photo's EXIF Artist takes priority; the saved default can also be applied to the current photo.

### Lens profiles

Open **Settings → Lens profiles**. Enter a product name for display and the original EXIF model for matching. Configure native equivalent focal lengths, native zoom and the optional maximum digital zoom separately.

Example for Xiaomi 17 Ultra:

| Lens | Native equivalent range | Native zoom | Optional digital maximum |
| --- | --- | --- | --- |
| Main | 23–23 mm | 1–1× | 3.1× |
| Ultra-wide | 14–14 mm | 0.6–0.6× | 0.9× |
| Telephoto | 75–100 mm | 3.2–4.3× | Set the confirmed total maximum if needed |

Physical focal lengths must use actual Camera2/EXIF values, not the equivalent values in this table. Leave unknown values blank. Lens matching prioritizes physical focal metadata, then the configured equivalent/digital ranges; ambiguous matches remain unset.

Camera scanning lists unconfigured hardware for linking to profiles. Apply each lens edit, save the profile list, then re-import photos to use the updated configuration.

## Supported exports

| Input | Output | Preservation |
| --- | --- | --- |
| Ordinary still image | JPEG / 8-bit PNG | Original dimensions; optional capture metadata |
| JPEG Ultra HDR (Android 14+) | JPEG | Gainmap and decoded color space |
| Supported JPEG Motion Photo / Microvideo | JPEG | Original encoded video/audio; selected metadata |
| Supported JPEG Ultra HDR Motion Photo (Android 14+) | JPEG | Both HDR and motion data |

JPEG images and gainmaps are re-encoded; video/audio are not transcoded. Location and capture-time switches apply to both photo and video metadata. For supported MP4/MOV files, metadata is cleared without moving encoded samples or changing playback timing; unchanged ranges are verified. All-retained video is copied byte for byte. HDR/Motion Photos cannot be exported as PNG. Import the complete original file: media removed by a messaging app or provider cannot be recovered.

HEIC/AVIF preservation, separate-file Apple Live Photos, undocumented vendor motion formats, animated images and high-bit-depth PNG export are not supported. Unrecognized or damaged media stops export. HDR display and motion playback depend on the device and gallery app; compatibility is not verified across all devices.

Input limits are 512 MB / 200 MP per photo. Available device memory may impose a lower limit. Export does not automatically reduce resolution to fit memory. Background export and free card dragging are not supported.

Motion playback uses the original video. The HDR preview switch does not remove the gainmap or change export settings. Both features depend on device codec/display support.

## Fonts

The reference style combines SF Compact Rounded Medium with selected SF Mono Medium digits when those fonts are available. This is an approximation of the screenshots, not a confirmed identification of Apple's original fonts. Builds without the local font files use Android system fonts. Imported TTF/OTF/TTC fonts apply to the entire card and can be up to 10 MB.

Font binaries are excluded from the repository. See [font setup and licensing](app/src/main/assets/fonts/README.md) before building with Apple fonts.

## Privacy

- Photos are processed on the device. Optional place lookup sends photo coordinates to the Android system geocoding provider; it can be disabled in Settings.
- Camera permission is optional and used to scan lens information. The app does not request the device's current location or broad storage access.
- EXIF capture details, location and capture time have separate controls. Location retention defaults to off. Video metadata follows the same controls; unsupported containers stop export when removal cannot be verified. Serial numbers, MakerNote, arbitrary source XMP and thumbnails are not copied. Names and places displayed on the card remain visible.
- Turning off capture time removes the original photo/video dates; a newly saved file still has its own filesystem creation time, and galleries may display the save time.

## Build

Requires **JDK 17** or a compatible newer JDK and **Android SDK Platform 37**. Gradle 9.6.1 is included through the Wrapper. Configure `JAVA_HOME` and `ANDROID_HOME` as needed; uncached dependencies require network access.

```bash
# Optional on macOS, subject to the font licenses.
bash scripts/copy-macos-font.sh

# Unit tests, Debug/Release Lint and APK builds.
bash scripts/build-macos.sh
```

APKs are written to `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`. Build failures return a nonzero exit code; check the first reported error. Run `./gradlew clean` to remove build outputs.

To install the latest universal Release on an authorized ADB device:

```bash
python3 - <<'PYAPK'
from pathlib import Path
import json, subprocess
metadata = Path('app/build/outputs/apk/release/output-metadata.json')
artifacts = json.loads(metadata.read_text())['elements']
apk = metadata.parent / next(item['outputFile'] for item in artifacts if not item['filters'])
subprocess.run(['adb', 'install', '-r', str(apk)], check=True)
PYAPK
```

A successful installation prints `Success`. If signatures differ, rebuild with the original signing key to retain app data.

### Variants and signing

| Variant | Application ID suffix | Signing |
| --- | --- | --- |
| Debug | `.debug` | Local debug key |
| Debug Unsigned | `.debug.unsigned` | None |
| Release | None | Configured private key, or local debug key |
| Release Unsigned | `.unsigned` | None |

Configure private signing using [signing.properties.example](signing.properties.example). Signed variants enable APK v1/v2; unsigned variants require signing before installation. No private key is included. Release builds use R8 and resource shrinking.

### Versions and APK names

Marketing version **27.0** uses build train **1A**. Each build increments the local sequence; all variants and ABIs in that invocation share it. APKs are available for arm64-v8a, armeabi-v7a, x86, x86_64 and universal:

```text
FuyaoPhotoInfo-applicationId-27.0(1Asequence)-ABI-variant.apk
```

### Tests

The build script runs JVM unit tests and Android Harmony DOM compatibility tests. With an authorized device, run `./gradlew :app:connectedDebugAndroidTest` for Android rendering and media tests, including HDR cases on Android 14+. Host tests do not replace device validation.

## Technology and structure

Kotlin · Jetpack Compose / Material 3 · Camera2 · ExifInterface 1.4.2 · AGP 9.2.1 · compile/target SDK 37.

Application ID: `ing.fuyaoskyrocket.photoinfo`. Application sources are under `app/src/main/java/ing/fuyaoskyrocket/photoinfo/`:

| Directory | Purpose |
| --- | --- |
| `data/` | Photos, exports, geocoding, settings and camera inventory |
| `domain/` | Metadata, lens matching, card layout, typography and media formats |
| `platform/` | Android rendering and font loading |
| `presentation/`, `ui/` | Editing state and application screens |

Build helpers are in `scripts/`; tests are in `app/src/test/` and `app/src/androidTest/`.

## License

Original source is `AGPL-3.0-only`; see [LICENSE](LICENSE) and [NOTICE](NOTICE). Fonts, screenshots and photographs retain their own rights. This is not an Apple product.

## Apple-related notice

Fuyao Photo Info is an independent third-party project. It is not developed, sponsored or endorsed by, or affiliated with, Apple Inc. Apple, iPhone and macOS are trademarks of Apple Inc.; other names and materials remain the property of their respective owners.

References to Apple products, fonts and visual styles describe the project’s references and implementation only. The information-card measurements are estimates from reference images, not official Apple specifications, design resources or a claim of certification.

Apple fonts, including SF Mono, remain subject to their applicable licenses and are not licensed under this project’s AGPL-3.0-only terms. Their availability on macOS, the local copying script and Git exclusion do not grant permission to embed or redistribute them. Before distributing an Android APK containing an Apple font, obtain a license covering that use or build with Android monospace or another appropriately licensed font. This notice itself grants no rights to Apple materials.

See Apple’s [trademark guidelines](https://www.apple.com/legal/intellectual-property/guidelinesfor3rdparties.html), [trademark list](https://www.apple.com/legal/intellectual-property/trademark/appletmlist.html) and [font information and license terms](https://developer.apple.com/fonts/). The license supplied with the particular font or material remains applicable.

## AI-Assisted Development

Generative AI was used to assist with coding during the development of this project.

[![Vibe PR](https://raw.githubusercontent.com/fenxer/llm-things/main/stickers/vibe-pr.svg)](https://github.com/fenxer/llm-things/blob/main/stickers/vibe-pr.svg)
