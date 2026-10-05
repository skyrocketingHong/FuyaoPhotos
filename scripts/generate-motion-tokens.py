#!/usr/bin/env python3
"""Generate native constants from the shared motion contract, or check for drift."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--check", action="store_true")
args = parser.parse_args()
tokens = json.loads((ROOT / "shared/motion/tokens.json").read_text())
header = "// Generated from shared/motion/tokens.json by scripts/generate-motion-tokens.py.\n"
kotlin = header + "package ing.fuyaoskyrocket.photoinfo.domain.motion\n\nobject PhotoMotionTokens {\n"
swift = header + "nonisolated public enum PhotoMotionTokens {\n"
for key, value in tokens.items():
    if not isinstance(value, (int, float)) or not 0 < value <= 3_000_000:
        raise ValueError(f"Invalid motion token: {key}")
    kotlin += f"    const val {key} = {value}{'f' if isinstance(value, float) else ''}\n"
    swift += f"    public static let {key}: {'Double' if isinstance(value, float) else 'Int'} = {value}\n"
outputs = {
    ROOT / "android/app/src/main/java/ing/fuyaoskyrocket/photoinfo/domain/motion/PhotoMotionTokens.kt": kotlin + "}\n",
    ROOT / "apple/Sources/DesignSystem/Motion/PhotoMotionTokens.swift": swift + "}\n",
}
stale = []
for path, content in outputs.items():
    if args.check:
        if not path.exists() or path.read_text() != content:
            stale.append(str(path.relative_to(ROOT)))
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
if stale:
    raise SystemExit("Motion tokens need regeneration: " + ", ".join(stale))
print(f"Motion tokens {'checked' if args.check else 'generated'}: {len(outputs)} platforms, {len(tokens)} values")
