#!/usr/bin/env bash
# Native offscreen Form capture; never launches or installs the Fuyao Photos app.
set -euo pipefail
if [[ "$(uname -s)" != Darwin ]]; then
    printf '%s\n' 'SKIP Native Form capture requires macOS and a Metal device.'
    exit 0
fi
PROBE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROBE_BUILD_DIR="$PROBE_ROOT/.build/native-photo-import-capture"
PROBE_ARCH="$(uname -m)"
mkdir -p "$PROBE_BUILD_DIR"
xcrun swiftc -O -parse-as-library -swift-version 5 -default-isolation MainActor \
    -sdk "$(xcrun --sdk macosx --show-sdk-path)" \
    -target "${PROBE_ARCH}-apple-macos26.0" \
    "$PROBE_ROOT/Sources/DesignSystem/Motion/PhotoMotionTokens.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Components/PhotoImportTransitionCapture.swift" \
    "$PROBE_ROOT/Tests/NativePhotoImportCapture/Probe.swift" \
    -o "$PROBE_BUILD_DIR/NativePhotoImportCaptureProbe"
"$PROBE_BUILD_DIR/NativePhotoImportCaptureProbe"
