<p align="center">English | <a href="README_ZH.md">简体中文</a></p>
<h1 align="center">Fuyao Photo Info</h1>
<p align="center">A compact frosted camera-information card, inside the photograph.</p>
<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-AGPL--3.0--only-blue" alt="AGPL-3.0-only">
</p>

A single-photo Android editor with local image processing and optional system place-name lookup. It reads available EXIF metadata and overlays an editable information card without adding a border or changing the photo dimensions. Local build evidence is kept in the ignored `docs/` directory; device/runtime limits are summarized below.

## Features

- Save a default photographer in Settings; EXIF Artist takes priority, with an explicit action to apply your default to the current photo.
- Select a photo through the system picker or import an original from Files; normalize all eight EXIF orientations, including mirrored images.
- Edit device, photographer, location, lens, megapixels, equivalent focal length, exposure, aperture and ISO. Missing fields are omitted. Photo GPS resolves to city/country through the system service; saved user lens profiles and optional 1× calibration format lens magnification. Camera2 inventories visible hardware without capturing; editable profiles store device names, physical/equivalent ranges and zoom endpoints. All values remain editable.
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

See [metadata recognition](docs/METADATA.md) for GPS, default-photographer and lens-profile behavior. See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for data flow and restoration boundaries. The legacy `install-workspace.py` helper is for importing an extracted package into another workspace; it is unnecessary when already working in this project.

## Configuring lenses

In Settings → Lens profiles, scan visible lenses or add a profile manually. Use the device/model text from the photo (prefilled when a photo is open), then enter a name and equivalent focal range. Set equal endpoints for fixed lenses. Optional zoom endpoints are interpolated for variable lenses; optional physical ranges can recover a missing equivalent focal length when the match is unique. Save the profile page to persist the configuration. Camera IDs describe local hardware and are not used as EXIF identifiers.

For the user-supplied Xiaomi 17 Ultra specifications, editable profiles can use 23–23 mm / 1–1× for the main camera and 75–100 mm / 3.2–4.3× for the telephoto. The ultrawide uses 14–14 mm (approximately 0.6× relative to 23 mm). Lens display names are user-defined. No product mapping is hardcoded. Available physical focal metadata further disambiguates equivalent ranges; unresolved overlaps remain unmatched.

## HDR and Motion Photo preservation

- On Android 14+, recognized JPEG Ultra HDR images retain their gainmap and decoded color space. The card region receives corresponding gainmap edits; unrelated gainmap pixels remain unchanged before JPEG encoding. The encoded gainmap parameters and color space are checked before publication.
- Standard JPEG Motion Photos and compatible legacy Microvideo files retain the complete original MP4/MOV payload, including audio and video metadata, without transcoding. Export verifies the copied payload with SHA-256 and preserves its presentation timestamp.
- HDR Motion Photos retain the GainMap directory item before the video item. EXIF/XMP insertion updates MPF sizes and offsets. Gallery filenames end in `_MP.jpg`.
- PNG export is disabled for HDR/Motion inputs. Unknown auxiliary data, malformed containers, unsupported formats or failed verification stop export rather than silently discarding media.

Prefer importing the complete original through Files; HDR/video already stripped by an upstream provider cannot be recovered. The current preservation path supports JPEG-based containers. HEIC/AVIF preservation, separate-file Apple Live Photos, undocumented vendor motion formats, animated images and high-bit-depth PNG are not supported for export. Android encoding/display, vendor camera enumeration and gallery playback still require device testing. JPEG base and gainmap images are re-encoded; this is not a pixel-lossless workflow. Video bytes are preserved exactly. Sharing apps can subsequently change or flatten the file.

## Fonts, privacy and output limits

This local checkout contains SF Mono Regular copied from the macOS Terminal bundle. It is loaded automatically and embedded in APKs built from this checkout. The font binary and reference screenshots are excluded from Git and are not covered by the source license. A source checkout without the font remains buildable with Android monospace. Runtime imports (up to 10 MB) remain in app-private storage.

The app declares Internet and photo-metadata (`ACCESS_MEDIA_LOCATION`) access, plus optional camera permission for hardware enumeration, with no current-location or broad storage permission. Place lookup can send photo coordinates to the Android system geocoding provider; the photograph itself stays local. Disable lookup in Settings when not needed. Optional capture metadata uses an allowlist excluding GPS, serial numbers, MakerNote, XMP and thumbnails. Editing the visible card does not rewrite original capture tags. Visible names and places remain part of exported image pixels.

Ordinary still images can be exported as JPEG or 8-bit PNG. Ultra HDR and Motion Photo exports follow the preservation path above. Source images are never overwritten. Capture-tag retention excludes GPS from the still-image EXIF; an untouched video retains its own metadata, which can include location information.

Insufficient memory produces an error without silently lowering resolution. Input caps of 512 MB / 200 MP do not guarantee that every device can export those sizes. Batch processing, free dragging and background export services are outside this version.

## Validation

Use `bash scripts/build-macos.sh` for host checks and APKs; use `./gradlew :app:connectedDebugAndroidTest` with an authorized device for orientation, pixel-boundary, export-metadata, settings, font-reset and Android 14+ HDR/Motion tests. `scripts/test-core.sh` is an optional offline route requiring Kotlin CLI. See [VALIDATION.md](docs/VALIDATION.md) for actual coverage and remaining device checks. The GitHub workflow has not been run remotely.

## License

Original source is `AGPL-3.0-only`; see [LICENSE](LICENSE) and [NOTICE](NOTICE). Fonts, screenshots and photographs retain their own rights. This is not an Apple product.
