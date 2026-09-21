# Card typography

Local default typography uses two independent faces:

- `SF-Compact-Rounded.ttf`: copied byte-for-byte from `/System/Library/Fonts/SFCompactRounded.ttf`, loaded at `wght=500`; `cv04` centers colons vertically, `cv05` enables the seriffed capital I, and `pnum` preserves the proportional digit `1` (without `cv09`).
- `SF-Mono-Medium.otf`: copied byte-for-byte from the macOS Terminal font bundle; used only for ASCII digits `0` and `2`–`9`; `1` stays in Compact Rounded.

Spaces, punctuation, parentheses and decimal separators use the proportional
base face. Android measurement, wrapping, color rendering and HDR text coverage
share the same styled text. The 10.5 px reference uses the monospace face as its visual-size anchor. The
proportional face is calibrated against actual H outlines at a large probe size,
then scaled as a whole to match capital height; monospace digits keep the reference em size.
Standalone proportional `1` receives a further 3% optical size correction at the
same baseline in every field. Combining-mark and keycap sequences stay intact.
The shared baseline and 12.5 px line-height reference scale with the photo short
edge and text-size control. Imported uniform fonts do not receive this calibration.

The combination approximates the reference screenshots; it is not a verified
identification of the fonts used in Apple materials. Without local font binaries,
builds fall back to system proportional glyphs (including `1`) and the selected monospace digits. A custom
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
