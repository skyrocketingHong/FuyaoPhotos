#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ $# -eq 0 || ${1:-} == --help ]]; then
    cat <<'USAGE'
Usage: bash apple/scripts/xcodebuild-numbered.sh [xcodebuild options] build|archive

Reserves one ignored local build number, then forwards the supplied Xcode build
with matching CFBundleVersion and FuyaoBuildTrain values. Use this wrapper for
one-platform command-line builds. For the standard macOS + iOS pair, use
apple/scripts/build.sh so both outputs share one number.

The Xcode Build menu bypasses this wrapper and retains the project's fallback
version. Use this script from Terminal when a new numbered build is required.
USAGE
    exit 0
fi

BUILD_ACTION=false
for ARGUMENT in "$@"; do
    case "$ARGUMENT" in
        build|archive) BUILD_ACTION=true ;;
        CURRENT_PROJECT_VERSION=*|FUYAO_BUILD_TRAIN=*|MARKETING_VERSION=*)
            printf 'Version build settings are supplied by this wrapper: %s\n' "$ARGUMENT" >&2
            exit 2
            ;;
    esac
done
if [[ "$BUILD_ACTION" != true ]]; then
    printf 'Pass an explicit build or archive action. Use xcodebuild directly for read-only queries.\n' >&2
    exit 2
fi

cd "$ROOT"
SDK_VERSION="$(xcrun --sdk macosx --show-sdk-version)"
if [[ "$SDK_VERSION" != 27.* ]]; then
    printf 'Select Xcode with SDK 27 via DEVELOPER_DIR (current SDK: %s).\n' "$SDK_VERSION" >&2
    exit 2
fi

read -r BUILD_NUMBER BUILD_TRAIN MARKETING_VERSION < <(python3 scripts/reserve-build-number.py)
printf 'Building Fuyao Photos %s (%s, build %s).\n' \
    "$MARKETING_VERSION" "$BUILD_TRAIN" "$BUILD_NUMBER"
xcodebuild "$@" CURRENT_PROJECT_VERSION="$BUILD_NUMBER" \
    FUYAO_BUILD_TRAIN="$BUILD_TRAIN" MARKETING_VERSION="$MARKETING_VERSION"
