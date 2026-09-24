#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION=9.6.1
for tool in curl unzip java; do
    command -v "$tool" >/dev/null || { echo "Required tool not found: $tool" >&2; exit 1; }
done
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/fuyao-bootstrap"
mkdir -p "$CACHE"
TEMP="$(mktemp -d "${TMPDIR:-/tmp}/fuyao-gradle.XXXXXX")"
trap 'rm -rf "$TEMP"' EXIT
URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
echo "Bootstrapping Gradle $VERSION from the official distribution (first checkout only)."
curl --fail --location --proto '=https' --proto-redir '=https' --tlsv1.2 --connect-timeout 15 --max-time 60 \
    "$URL.sha256" -o "$TEMP/checksum"
CHECKSUM="$(tr -d '[:space:]' < "$TEMP/checksum")"
[[ "$CHECKSUM" =~ ^[[:xdigit:]]{64}$ ]] || { echo 'Invalid official SHA-256 response.' >&2; exit 1; }
ZIP="$CACHE/gradle-$VERSION-bin.zip"
if [[ ! -f "$ZIP" ]]; then
    curl --fail --location --proto '=https' --proto-redir '=https' --tlsv1.2 --connect-timeout 15 --max-time 600 \
        "$URL" -o "$TEMP/gradle.zip"
    mv "$TEMP/gradle.zip" "$ZIP"
fi
if command -v shasum >/dev/null; then
    ACTUAL="$(shasum -a 256 "$ZIP" | awk '{print $1}')"
else
    ACTUAL="$(sha256sum "$ZIP" | awk '{print $1}')"
fi
if [[ "$ACTUAL" != "$CHECKSUM" ]]; then
    echo "SHA-256 mismatch. Remove the invalid cached archive before retrying: $ZIP" >&2
    exit 1
fi
unzip -q "$ZIP" -d "$TEMP"
mkdir -p "$TEMP/project"
printf 'rootProject.name = "wrapper-bootstrap"\n' > "$TEMP/project/settings.gradle.kts"
"$TEMP/gradle-$VERSION/bin/gradle" --no-daemon -p "$TEMP/project" wrapper \
    --gradle-version "$VERSION" --distribution-type bin --gradle-distribution-sha256-sum "$CHECKSUM"
mkdir -p "$ROOT/gradle/wrapper"
cp "$TEMP/project/gradle/wrapper/gradle-wrapper.jar" "$ROOT/gradle/wrapper/gradle-wrapper.jar"
cp "$TEMP/project/gradle/wrapper/gradle-wrapper.properties" "$ROOT/gradle/wrapper/gradle-wrapper.properties"
cp "$TEMP/project/gradlew.bat" "$ROOT/gradlew.bat"
cp "$TEMP/project/gradlew" "$ROOT/gradlew"
chmod +x "$ROOT/gradlew"
echo 'Official Gradle Wrapper generated; distribution checksum is now pinned in wrapper properties.'
