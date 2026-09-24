# Fuyao Photos icon SVG set

Canvas: `108 × 108`, matching the Android adaptive icon coordinate system used by the project.

Layer order, bottom to top:

1. `00-background.svg`: dark green launcher background (`#14382F`)
2. `10-photo-panel.svg`: photo surface
3. `20-photo-sun.svg`: warm accent / highlight
4. `30-photo-mountains-back.svg`: secondary landscape silhouette
5. `31-photo-mountains-front.svg`: primary landscape silhouette (`#3FB797`)
6. `40-info-card.svg`: metadata card overlay
7. `50-info-accent.svg`: yellow device/credit lines
8. `60-info-metadata.svg`: white EXIF-style metadata lines

Exports:

- `FuyaoPhotos-icon.svg`: full composed icon
- `FuyaoPhotos-foreground.svg`: transparent foreground for Android adaptive icon use
- `layers/*.svg`: one editable component per SVG; every file shares the same 108×108 viewBox and can be stacked without repositioning

The Android foreground keeps the photo and information-card composition from the Icon Composer source. Its enlarged sun, tightened mountains, and left-shifted card follow the current `FuyaoPhoto.icon` default export while staying inside the Android adaptive-icon safe area. The monochrome launcher resource uses the same geometry.
