# Changelog

## 27.0 — 2026-09-20

### Fixed

- Replace existing Motion Photo XMP directories safely with both Android snapshot node lists and desktop live node lists; add Android DOM regression checks to local and CI builds.
- Prevent the final JPEG background draw from clearing the HDR gainmap and causing a null-pointer export failure. Add native regression coverage for JPEG preparation and HDR still export.

### Photo editing

- Add an inset frosted information card with EXIF fields, editable credits, short-edge scaling, text wrapping and independent 80%–180% text sizing.
- Add original comparison, full-screen zoom, SF Mono loading and custom font import.
- Preserve supported JPEG Ultra HDR gainmaps and Motion Photo video/audio while editing the cover; prevent silent flattening of unsupported containers.

### Metadata and settings

- Resolve photo GPS to city/country with retry and manual override.
- Save a default photographer and editable lens profiles; use Camera2 to list visible hardware and match configured equivalent/physical focal ranges and zoom endpoints.

### Interface

- Use standard Material 3 app bars, typography and input shapes while retaining Fuyao dynamic color and grouping.
- Add Navigation Compose predictive back for settings, lens pages and full-screen preview; use a Material 3 export bottom sheet with segmented format selection.
- Improve edge-to-edge, keyboard handling, adaptive inspector layouts and large-text behavior.
- Add a dedicated lens editor, inline validation, deletion undo and saved editing state.
- Stabilize render feedback and add interruptible, bounded preview reset and accessible zoom controls.

### Packaging

- Adopt marketing version 27.0, build train 1A and shared per-build numbering across four variants and five ABI outputs.
- Standardize APK filenames and enable v1/v2 signing. Normal Release prefers a private key and otherwise uses the local debug key; Unsigned variants remain unsigned.
