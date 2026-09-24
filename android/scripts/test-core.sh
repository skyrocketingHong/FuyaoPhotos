#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v kotlinc >/dev/null || { echo 'Kotlin CLI (1.9+) is required for offline core checks.' >&2; exit 1; }
command -v java >/dev/null || { echo 'Java runtime is required.' >&2; exit 1; }
mkdir -p "$ROOT/.local"
SOURCES=()
while IFS= read -r -d '' source; do SOURCES+=("$source"); done < <(find "$ROOT/app/src/main/java/ing/fuyaoskyrocket/photoinfo/domain" -name '*.kt' -print0)
kotlinc "${SOURCES[@]}" "$ROOT/app/src/test/java/ing/fuyaoskyrocket/photoinfo/CoreChecks.kt" \
    "$ROOT/tests/OfflineMain.kt" -include-runtime -d "$ROOT/.local/core-checks.jar"
java -jar "$ROOT/.local/core-checks.jar"
