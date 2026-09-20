#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLIN_BIN="$(command -v kotlinc)"
KOTLIN_DIR="${KOTLIN_HOME:-$(cd "$(dirname "$KOTLIN_BIN")/.." && pwd)}"
COMPILER="$KOTLIN_DIR/lib/kotlin-compiler.jar"
[[ -f "$COMPILER" ]] || { echo 'Set KOTLIN_HOME to the Kotlin compiler installation.' >&2; exit 1; }
mkdir -p "$ROOT/.local"
kotlinc "$ROOT/tests/SyntaxCheck.kt" -cp "$COMPILER" -d "$ROOT/.local/syntax.jar"
java -cp "$ROOT/.local/syntax.jar:$KOTLIN_DIR/lib/*" SyntaxCheckKt "$ROOT"
python3 "$ROOT/scripts/check-resources.py"
