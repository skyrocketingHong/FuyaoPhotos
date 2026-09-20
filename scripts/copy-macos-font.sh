#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="/System/Applications/Utilities/Terminal.app/Contents/Resources/Fonts/SF-Mono-Regular.otf"
TARGET="$ROOT/app/src/main/assets/fonts/SF-Mono-Regular.otf"
[[ -f "$SOURCE" ]] || { echo 'SF Mono Regular was not found in the macOS Terminal bundle.' >&2; exit 1; }
mkdir -p "$(dirname "$TARGET")"
if [[ -e "$TARGET" ]]; then
    cmp -s "$SOURCE" "$TARGET" || { echo 'An existing, different font was preserved. Move it before copying.' >&2; exit 1; }
else
    # Copy bytes only; macOS system-file flags must not be propagated to the workspace.
    cat "$SOURCE" > "$TARGET"
    chmod 644 "$TARGET"
fi
cmp "$SOURCE" "$TARGET"
printf 'Local card font: %s\n' "$TARGET"
