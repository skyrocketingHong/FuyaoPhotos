# Icon sources

- `sf-symbols/livephoto.svg`: SF Symbols SVG export used for Motion Photo playback.
- `sf-symbols/hdr.viewfinder.rectangular.svg`: SF Symbols template export; the Android drawable uses its `Regular-S` group.
- `material/`: Google Material Symbols Outlined, 24px, from the revision recorded in `sources.json`. The upstream Apache-2.0 license is included.

SF source paths are preserved by `scripts/convert-sf-icons.py`, with a uniform transform into a 24dp viewport. These Apple assets retain their separate terms and are not relicensed under the project source license. See [NOTICE](../../NOTICE).

Google icon names, source URLs and checksums are recorded in [sources.json](material/sources.json). Android drawables retain upstream path data and use theme tint at runtime. App branding is separate from these action icons.
