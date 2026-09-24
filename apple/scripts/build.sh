#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# DEVELOPER_DIR can select Xcode 27.1 or 27.2 without changing xcode-select globally.
SDK_VERSION="$(xcrun --sdk macosx --show-sdk-version)"
if [[ "$SDK_VERSION" != 27.* ]]; then
    printf 'Select Xcode with SDK 27 via DEVELOPER_DIR (current SDK: %s).\n' "$SDK_VERSION" >&2
    exit 2
fi
python3 scripts/check-localizations.py
swift test --package-path Packages/PhotoMapCore -c release
swift test --package-path .
bash scripts/test-cards.sh
read -r BUILD_NUMBER BUILD_TRAIN MARKETING_VERSION < <(python3 scripts/reserve-build-number.py)
printf 'Building Fuyao Photos %s (%s, build %s) for macOS and iOS Simulator.\n' \
    "$MARKETING_VERSION" "$BUILD_TRAIN" "$BUILD_NUMBER"
xcodebuild -project FuyaoPhotos.xcodeproj -scheme FuyaoPhotos -configuration Release \
    -destination 'generic/platform=macOS' -derivedDataPath .build/macos CODE_SIGNING_ALLOWED=NO \
    CURRENT_PROJECT_VERSION="$BUILD_NUMBER" FUYAO_BUILD_TRAIN="$BUILD_TRAIN" MARKETING_VERSION="$MARKETING_VERSION" build
xcodebuild -project FuyaoPhotos.xcodeproj -scheme FuyaoPhotos -configuration Release \
    -destination 'generic/platform=iOS Simulator' -derivedDataPath .build/ios CODE_SIGNING_ALLOWED=NO \
    CURRENT_PROJECT_VERSION="$BUILD_NUMBER" FUYAO_BUILD_TRAIN="$BUILD_TRAIN" MARKETING_VERSION="$MARKETING_VERSION" build
