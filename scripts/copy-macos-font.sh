#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FONT_DEST="$ROOT/app/src/main/assets/fonts"
mkdir -p "$FONT_DEST"
copy_font() {
    local source="$1" target="$FONT_DEST/$2"
    [[ -f "$source" ]] || { printf 'Font not found: %s\n' "$source" >&2; exit 1; }
    if [[ -e "$target" ]]; then
        cmp -s "$source" "$target" || { printf 'A different existing font was preserved: %s\n' "$target" >&2; exit 1; }
    else
        # Copy bytes only; never propagate macOS system-file flags.
        cat "$source" > "$target"
        chmod 644 "$target"
    fi
    cmp "$source" "$target"
    printf 'Local card font: %s\n' "$target"
}
copy_font '/System/Library/Fonts/SFNSRounded.ttf' 'SF-Pro-Rounded.ttf'
copy_font '/System/Applications/Utilities/Terminal.app/Contents/Resources/Fonts/SF-Mono-Medium.otf' 'SF-Mono-Medium.otf'
