# Card typography

Local default typography uses two independent faces:

- `SF-Pro-Rounded.ttf`: copied byte-for-byte from `/System/Library/Fonts/SFNSRounded.ttf`, loaded at `wght=500`; `cv05` enables the seriffed capital I.
- `SF-Mono-Medium.otf`: copied byte-for-byte from the macOS Terminal font bundle; used for ASCII digits 0–9.

Spaces, punctuation, parentheses and decimal separators use the proportional
letter face. Android measurement, wrapping, color rendering and HDR text coverage
share the same styled text. The 10.5 px font-size and 12.5 px line-height reference
remain unchanged and scale with the photo short edge and text-size control.

The combination approximates the reference screenshots; it is not a verified
identification of the fonts used in Apple materials. Without local font binaries,
builds fall back to system sans-serif letters and monospace digits. A custom
TTF/OTF/TTC replaces both faces and does not receive the reference OpenType features.

Font binaries stay Git-ignored. `scripts/copy-macos-font.sh` copies bytes without
macOS system flags and preserves any different existing destination file.

## Apple font licensing

Apple fonts remain subject to their applicable licenses. Availability on macOS,
Git exclusion and this project's source license do not grant Android embedding
or redistribution rights. Before distribution, obtain a license covering that
use or omit the Apple fonts and use appropriately licensed alternatives.

See [Apple font information and license terms](https://developer.apple.com/fonts/)
and the license supplied with each font.
