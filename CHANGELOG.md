# Changelog

## 27.0 — 2026-09-20

### Photo editing

- Add an inset frosted information card with EXIF fields, editable credits, short-edge scaling, text wrapping and independent 80%–180% text sizing.
- Add original comparison, full-screen zoom, SF Mono loading and custom font import.
- Preserve supported JPEG Ultra HDR gainmaps and Motion Photo video/audio while editing the cover; prevent silent flattening of unsupported containers.

### Metadata and settings

- Resolve photo GPS to city/country with retry and manual override.
- Save a default photographer and editable lens profiles; use Camera2 to list visible hardware and match configured equivalent/physical focal ranges and zoom endpoints.

### Interface

- Align compact app bars, semantic colors, typography, controls and spacing with the other Fuyao Android apps.
- Improve edge-to-edge, keyboard handling, adaptive inspector layouts and large-text behavior.
- Add a dedicated lens editor, inline validation, deletion undo and saved editing state.
- Stabilize render feedback and add interruptible, bounded preview reset and accessible zoom controls.

### Packaging

- Adopt marketing version 27.0, build train 1A and shared per-build numbering across four variants and five ABI outputs.
- Standardize APK filenames and enable v1/v2 signing. Normal Release prefers a private key and otherwise uses the local debug key; Unsigned variants remain unsigned.
