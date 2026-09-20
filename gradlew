#!/usr/bin/env bash
# First-checkout bootstrap. Replaced by the official Gradle Wrapper after a verified download.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$ROOT/scripts/bootstrap-gradle.sh"
exec "$ROOT/gradlew" "$@"
