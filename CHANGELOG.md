# Changelog

## 1.0.0 — 2026-09-20

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

58 core checks passed through JUnit. Device tests are authored and compiled but have not run on a connected device. See [build status](docs/BUILD_STATUS.md) for APK, Lint, signing and environment evidence.
