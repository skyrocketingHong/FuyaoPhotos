<p align="center">English | <a href="README_ZH.md">简体中文</a></p>
<h1 align="center">Fuyao Photo Info</h1>
<p align="center">A compact frosted camera-information card, inside the photograph.</p>
<p align="center">Android 8.0+ · Kotlin · Jetpack Compose · Material 3 · 1.0.0 source preview</p>

**Status: source authored; 53 pure-Kotlin checks passed. Android compilation, Android Lint and device tests have not run successfully. No APK is included.** The build environment was separate from the user's Mac: the requested `/Volumes/.../FuyaoPhotoInfo` directory and its reference images were not accessed or modified. See [build evidence](docs/BUILD_STATUS.md).

## Features implemented in source

Select one image with the system photo picker, read available EXIF, normalize mirrored/rotated orientation, and edit nine camera/credit fields. Empty fields disappear. Device, author and location use yellow; capture parameters use white. Location is entered manually; camera type, equivalent focal length and zoom are never guessed from missing tags.

The card is drawn into the image, not into an added border. Defaults use a 1527×859 reference: 215×168 card, 77 right / 35 bottom inset, radius 20, padding 19, 10.5px monospaced text, 12.5px leading, 7px group gap, neutral gray at 60% and 25px approximate blur. Geometry scales with the short edge in both landscape and portrait. Long text wraps; the card expands upward when necessary, and reports overflow instead of truncating text or reducing its font size.

Adjust scale, opacity, blur, insets and radius. Compare the original, zoom into a full-screen preview, and import an appropriately licensed TTF/OTF/TTC into app-private storage. Preview and original-resolution export share one layout engine and renderer; exports are not screen captures.

Save a new JPEG (quality parameter 97) or PNG. Android 10+ writes to `Pictures/FuyaoPhotoInfo`; Android 8/9 uses system Save As. Share a completed export through Android's share sheet. Source images are not overwritten.

## Build in the requested Mac workspace

From the root of this extracted source package:

```bash
python3 scripts/install-workspace.py --build
```

The installer targets `/Volumes/Thunderbolt 5 SSD (2TB)/Code/GitHub/FuyaoPhotoInfo`, preserves references and `.git`, and aborts before copying when an existing file differs. It never overwrites such files or copies private signing/font assets. A different path can be passed with `--destination`.

Prerequisites: Python 3, JDK 17 and Android SDK Platform 37. The build script does not install SDK packages or accept SDK licenses. Configure `JAVA_HOME` and `ANDROID_HOME` as necessary. Once the source is in place:

```bash
./scripts/build-macos.sh
```

This runs unit tests, Android Lint and `assembleDebug`. First execution of `gradlew` verifies an official Gradle 9.6.1 distribution and generates the standard Wrapper; the bootstrap is then replaced by those standard files. No Wrapper JAR was available in the offline handoff environment. Run the bootstrap before initially importing into Android Studio. See [bootstrap notes](gradle/wrapper/README.md).

Expected APK location after a successful build: `app/build/outputs/apk/debug/app-debug.apk`.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project conventions

Package prefix, focused `data/domain/platform/presentation/ui` responsibilities, bilingual documentation, signing configuration and four build variants follow [FuyaoColorPicker](https://github.com/skyrocketingHong/FuyaoColorPicker). Its color-sampling implementation, historic color tables and image assets were not copied. Application ID: `ing.fuyaoskyrocket.photoinfo`.

| Area | Responsibility |
| --- | --- |
| `data/photo` | Private input copy, EXIF, decoding and orientation |
| `data/export` | Original-size encoding, metadata allowlist and publication |
| `domain/model`, `metadata`, `layout`, `render` | Framework-independent fields, formatting, card geometry and blur |
| `platform` | Bitmap compositing and private runtime font imports |
| `presentation` | Editable state, restoration and operation sequencing |
| `ui` | Material 3 editor, controls and zoomable preview |
| `scripts`, `.run`, `.github/workflows` | Build, validation, safe installation and workflow definitions |

Versions follow the existing Android template: AGP 9.2.1, Gradle 9.6.1, Compose compiler plugin 2.4.10, BOM 2026.06.01, compile/target SDK 37, plus ExifInterface 1.4.2. Pinning versions is not a claim that Android dependency resolution or compatibility was verified in this handoff.

## Variants and signing

Debug uses `.debug` and a locally generated debug key. Debug Unsigned uses `.debug.unsigned`. Release keeps the application ID and is signed only when all four properties in `signing.properties` are provided. Release Unsigned uses `.unsigned`. Corresponding tasks are `assembleDebug`, `assembleDebugUnsigned`, `assembleRelease` and `assembleReleaseUnsigned`.

Copy `signing.properties.example` to the ignored `signing.properties` to configure your own release key. **Release never falls back silently to a debug key.** Unsigned artifacts cannot be installed. No signing key is shipped.

## Fonts, privacy and output limits

No macOS or other font binaries are bundled. Android `Typeface.MONOSPACE` is the default. Runtime font import accepts the user's independently licensed TTF/OTF/TTC up to 10 MB; it does not establish permission to use or redistribute Apple fonts. Check the relevant license, including [Apple's published terms](https://developer.apple.com/fonts/).

The implementation produces **8-bit sRGB / SDR still images**. It does not preserve HDR gain maps, Display P3, high-bit-depth images, Live Photos, RAW workflows or animation. Format decoding, including HEIC, depends on Android's decoder. JPEG is re-encoded; PNG is lossless only with respect to the rendered SDR bitmap. Preview sampling and blur rasterization can differ slightly from original-resolution output.

No Internet, location, camera or broad storage permissions are declared. Optional metadata retention uses a capture-tag allowlist and excludes GPS, serial numbers, MakerNote, XMP and thumbnails. Card edits do not modify retained original capture tags. Visible author/location text still appears in the exported pixels.

There is no silent export downscaling. Insufficient memory causes an explicit failure. Input limits of 512 MB / 200 MP are safety caps, not guaranteed export capacities. This is a single-photo editor; batch processing, free dragging and background export services are outside the first version.

The three original reference images were unavailable. All styling is derived from the user's measurement brief, not a pixel-comparison or an official Apple specification.

## Validation

`./scripts/test-core.sh` runs the shared offline/JUnit core checks with Kotlin CLI 1.9+. `./scripts/check-syntax.sh` parses Kotlin syntax and checks XML/string resources. Neither substitutes for Android compilation. `./scripts/build-macos.sh` performs Android build checks; `./gradlew :app:connectedDebugAndroidTest` runs authored device tests.

See [BUILD_STATUS.md](docs/BUILD_STATUS.md), [VALIDATION.md](docs/VALIDATION.md) and the captured logs. The supplied GitHub workflow is a definition only; it has not been pushed or executed.

## License

Original source is declared `AGPL-3.0-only`; [LICENSE](LICENSE) identifies the grant and links the complete official terms. [NOTICE](NOTICE) records template and third-party boundaries. This source license does not license imported fonts, reference screenshots or photographs. This is not an Apple product.
