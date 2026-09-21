# Changelog

## 27.0 — 2026-09-20

### Fixed

- Match fixed-lens digital crops separately from native optical calibration, with physical lens metadata taking priority.
- Separate product display names from EXIF model identifiers, migrate existing profiles and link camera scans to configured hardware IDs.
- Ask before returning only when edits remain unsaved; reverting fields or successfully saving clears the warning.
- Replace existing Motion Photo XMP directories safely with both Android snapshot node lists and desktop live node lists; add Android DOM regression checks to local and CI builds.
- Prevent the final JPEG background draw from clearing the HDR gainmap and causing a null-pointer export failure. Add native regression coverage for JPEG preparation and HDR still export.

### Photo editing

- Add persistent save defaults with temporary per-save overrides for format, JPEG quality, EXIF details, location and capture time. Apply privacy choices to both photos and supported motion-video metadata without transcoding media.
- Receive single/multiple images from the Android share menu; add Open alongside Share to export completion notices.
- Apply a 3% optical size correction to the narrow proportional digit 1 across all card fields, using the same measurement and rendering path.
- Match visible capital heights across mixed font faces; load original-resolution full-screen previews with cancellation, memory guards and retained zoom.
- Refine reference typography with SF Compact Rounded Medium, a proportional digit 1, centered colons and a legible capital I; use SF Mono Medium for 0 and 2–9. Share shaping between layout, output and HDR coverage; imported fonts remain uniform.
- Add up to 50-photo sessions with horizontal paging, independent edits, sequential save-all, progress and partial-failure reporting.
- Add discard confirmation to editor exit and save pages; clear private drafts on confirmed exit.
- Use matching import buttons, a 4:3 preview with compact status, auto-dismiss save notices and a JPEG quality slider defaulting to 100.
- Add an inset frosted information card with EXIF fields, editable credits, short-edge scaling, text wrapping and independent 80%–180% text sizing.
- Add original comparison, full-screen zoom, SF Mono loading and custom font import.
- Preserve supported JPEG Ultra HDR gainmaps and Motion Photo video/audio while editing the cover; prevent silent flattening of unsupported containers.

### Metadata and settings

- Resolve photo GPS to city/country with retry and manual override.
- Save a default photographer and editable lens profiles; use Camera2 to list visible hardware and match configured equivalent/physical focal ranges and zoom endpoints.

### Interface

- Keep one editing destination with direct Open, Save and Settings actions; move About into Settings and distinguish applying lens drafts from saving profiles.
- Preserve Android 16 system back-to-home with a non-consuming observer; guard repeated navigation, photo replacement and destructive resets.
- Adapt preview and controls to keyboard, font size and separating hinges using stable WindowManager; correct scrolling and full-screen system-bar insets.
- Add actionable error messages, deferred save notices after error dialogs, localized value formats and accessible photo-switching actions.
- Match FuyaoColorPicker’s 48dp compact app bars, semibold titles and 28dp action icons, preserving 48dp touch targets and system-bar insets. Retain Material 3 input shapes, Fuyao dynamic color and grouping.
- Add Navigation Compose predictive back for settings, lens pages and full-screen preview; use a Material 3 export bottom sheet with segmented format selection.
- Improve edge-to-edge, keyboard handling, adaptive inspector layouts and large-text behavior.
- Add a dedicated lens editor, inline validation, deletion undo and saved editing state.
- Stabilize render feedback and add interruptible, bounded preview reset and accessible zoom controls.

### Packaging

- Adopt marketing version 27.0, build train 1A and shared per-build numbering across four variants and five ABI outputs.
- Standardize APK filenames and enable v1/v2 signing. Normal Release prefers a private key and otherwise uses the local debug key; Unsigned variants remain unsigned.
