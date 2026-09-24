#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$ROOT/.build/card-checks"
mkdir -p "$BUILD_DIR"
xcrun swiftc -parse-as-library -swift-version 6 \
  -sdk "$(xcrun --sdk macosx --show-sdk-path)" \
  "$ROOT/Sources/Features/PhotoInfo/Models/PhotoCard.swift" \
  "$ROOT/Sources/Features/PhotoInfo/Media/PhotoMediaInspector.swift" \
  "$ROOT/Sources/Features/PhotoInfo/Media/PhotoAuxiliaryData.swift" \
  "$ROOT/Sources/Features/PhotoInfo/Media/LivePhotoMovie.swift" \
  "$ROOT/Sources/Features/PhotoInfo/Media/LivePhotoRemuxSession.swift" \
  "$ROOT/Sources/Features/PhotoInfo/Media/LivePhotoStream.swift" \
  "$ROOT/Sources/Features/PhotoInfo/Rendering/CardTypography.swift" \
  "$ROOT/Sources/Features/PhotoInfo/Rendering/CardRenderer.swift" \
  "$ROOT/Sources/Features/PhotoInfo/Services/CardImageProcessor.swift" \
  "$ROOT/Sources/Features/PhotoInfo/Services/MovieMetadataCleaner.swift" \
  "$ROOT/Tests/CardRenderingChecks.swift" -o "$BUILD_DIR/CardRenderingChecks"
"$BUILD_DIR/CardRenderingChecks"
