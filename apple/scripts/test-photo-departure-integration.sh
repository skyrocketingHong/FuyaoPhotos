#!/usr/bin/env bash
# Mounts real import and departure views in a hidden host; never launches Fuyao Photos.
set -euo pipefail
if [[ "$(uname -s)" != Darwin ]]; then
    printf '%s\n' 'SKIP Photo departure integration requires macOS and Metal.'
    exit 0
fi
PROBE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROBE_BUILD="$PROBE_ROOT/.build/photo-departure-integration"
PROBE_SWIFT_OPT="${PROBE_SWIFT_OPT:--Onone}"
case "$PROBE_SWIFT_OPT" in
    -Onone|-O) ;;
    *) printf '%s\n' 'PROBE_SWIFT_OPT must be -Onone or -O.' >&2; exit 2 ;;
esac
mkdir -p "$PROBE_BUILD"
if [[ -n "${PHOTO_DEPARTURE_METALLIB:-}" ]]; then
    cp "$PHOTO_DEPARTURE_METALLIB" "$PROBE_BUILD/default.metallib"
else
    xcrun -sdk macosx metal -c "$PROBE_ROOT/Sources/DesignSystem/Motion/Dust/PhotoReassemblyShaders.metal" -o "$PROBE_BUILD/reassembly.air"
    xcrun -sdk macosx metallib "$PROBE_BUILD/reassembly.air" -o "$PROBE_BUILD/default.metallib"
fi
xcrun swiftc "$PROBE_SWIFT_OPT" -D DEBUG -parse-as-library -swift-version 5 -default-isolation MainActor \
    -module-cache-path "$PROBE_BUILD/ModuleCache" \
    -sdk "$(xcrun --sdk macosx --show-sdk-path)" -target "$(uname -m)-apple-macos26.0" \
    "$PROBE_ROOT/Sources/DesignSystem/Motion/PhotoMotionTokens.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Motion/PhotoReassemblyGeometry.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Motion/PhotoImportTargetLifecycle.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Motion/PhotoDeparturePlayback.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Motion/PhotoDepartureCaptureRegistry.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Components/PhotoImportTransitionCapture.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Components/PhotoDepartureOverlay.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Components/PhotoPreviewSnapshot.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Motion/Dust/PhotoReassemblyView.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Motion/Dust/TelegramDustView.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/Components/NativeFormToggleStyle.swift" \
    "$PROBE_ROOT/Sources/Features/PhotoInfo/Rendering/PhotoPreviewMetrics.swift" \
    "$PROBE_ROOT/Sources/App/PhotoPageStyle.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/PhotoImportForm.swift" \
    "$PROBE_ROOT/Sources/DesignSystem/PhotoImportPage.swift" \
    "$PROBE_ROOT/Tests/PhotoDepartureIntegrationProbe/BusinessStubs.swift" \
    "$PROBE_ROOT/Tests/PhotoDepartureIntegrationProbe/Probe.swift" \
    -o "$PROBE_BUILD/PhotoDepartureIntegrationProbe"

if [[ $# -gt 0 ]]; then
    "$PROBE_BUILD/PhotoDepartureIntegrationProbe" "$PROBE_ROOT/Sources/Resources" "$@"
    exit $?
fi
PROBE_FAILURES=0
PROBE_DISPLAY_SCALE=3 "$PROBE_BUILD/PhotoDepartureIntegrationProbe" "$PROBE_ROOT/Sources/Resources" metadata 390 || PROBE_FAILURES=$((PROBE_FAILURES + 1))
"$PROBE_BUILD/PhotoDepartureIntegrationProbe" "$PROBE_ROOT/Sources/Resources" cards 700 || PROBE_FAILURES=$((PROBE_FAILURES + 1))
"$PROBE_BUILD/PhotoDepartureIntegrationProbe" "$PROBE_ROOT/Sources/Resources" cards 700 --change-active || PROBE_FAILURES=$((PROBE_FAILURES + 1))
PROBE_DISPLAY_SCALE=3 "$PROBE_BUILD/PhotoDepartureIntegrationProbe" "$PROBE_ROOT/Sources/Resources" colors 390 --change-locale || PROBE_FAILURES=$((PROBE_FAILURES + 1))
printf 'RESULT photo departure integration: failures=%s/4, Swift=%s, hidden host with explicit manual GPU driving\n' "$PROBE_FAILURES" "$PROBE_SWIFT_OPT"
[[ "$PROBE_FAILURES" == 0 ]]
