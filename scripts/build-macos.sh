#!/usr/bin/env bash
# Works on macOS and Linux. Does not install SDK packages or accept licenses implicitly.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "$JAVA_HOME" ]]; then export JAVA_HOME; export PATH="$JAVA_HOME/bin:$PATH"; fi
fi
command -v java >/dev/null || { echo 'Install JDK 17 or set JAVA_HOME before building.' >&2; exit 1; }
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" ]] && [[ -f "$ROOT/local.properties" ]]; then
    SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -n 1)"
fi
if [[ -z "$SDK" ]]; then
    if [[ "$(uname -s)" == Darwin ]]; then SDK="$HOME/Library/Android/sdk"; else SDK="$HOME/Android/Sdk"; fi
fi
if [[ ! -f "$SDK/platforms/android-37/android.jar" && ! -f "$SDK/platforms/android-37.0/android.jar" ]]; then
    printf 'Android SDK Platform 37 was not found at: %s\nInstall it with Android Studio SDK Manager, or set ANDROID_HOME to your existing SDK.\nNo APK was built.\n' "$SDK" >&2
    exit 2
fi
export ANDROID_HOME="$SDK"
cd "$ROOT"
# Unit tests, Debug/Release Lint and APKs; release signing is never assumed.
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease "$@"
printf '\nDebug APK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$ROOT"
