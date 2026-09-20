# Changelog

## 1.0.0 — 2026-09-20

### Editable lenses and media preservation

- Replace device-specific hardcoding with saved lens profiles and Camera2 inventory of visible physical/standalone lenses. Match original EXIF device names and user-defined equivalent/physical ranges, with editable zoom endpoints.
- Retain Ultra HDR JPEG gainmaps and color information on Android 14+, edit gains under the card, and validate encoded output before publishing.
- Preserve standard JPEG Motion Photo video/audio byte-for-byte, retain timestamps, reconstruct XMP directories and adjust MPF offsets. Stop unsupported or unverifiable exports without publishing a flattened image.
- Add container, gainmap-math and Android HDR/Motion regression tests. HEIC/AVIF and undocumented vendor formats remain unsupported for preservation export; native device checks are pending.

### Photo metadata and settings

- Resolve EXIF GPS into city/country using the Android system geocoder, with timeout, retry, cancellation and manual editing. Add photo-metadata permission handling and original-file import.
- Add a Settings page for a persistent default photographer, optional GPS lookup and unknown-device 1× focal calibration. Single-photo edits no longer modify the global default.
- Recognize Xiaomi 17 Ultra / Xiaomi 14 lens profiles, including `75 MM (3.2X)`, while preserving explicit lens metadata and avoiding unsupported-device guesses.

### Added

- Single-photo Android editor with EXIF fields, manual overrides, an inset frosted card, short-edge scaling, wrapping, original comparison, zoom preview and original-resolution JPEG/PNG export.
- Independent text-size control from 80% to 180%, retaining the reference 100% default. Text keeps scaling with photo resolution; increasing it preserves card width and adds height only as needed.
- Local SF Mono Regular loading, custom font import/reset, and a macOS font-copy helper. Font binaries remain outside source control.
- Official Gradle Wrapper with a pinned distribution checksum, four build variants and bilingual project documentation following the existing Fuyao projects.

### Fixed

- Keep the reference 168 px card height when a credit wraps onto one additional line.
- Recognize the installed `android-37.0` SDK directory in the build script.
- Remove API 27-only navigation-bar theme attributes from API 26 resources; the existing edge-to-edge implementation configures appearance at runtime.
- Replace deprecated Material 3 tabs with PrimaryTabRow.

### Validation

70 core checks and 9 media-container tests passed through JUnit. Device tests are authored and compiled but have not run on a connected device. See [build status](docs/BUILD_STATUS.md) for APK, Lint, signing and environment evidence.
