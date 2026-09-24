#!/usr/bin/env python3
"""Check Apple translations and SwiftUI localization lookup keys."""

import json
from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "Sources"
RESOURCE = SOURCE / "Resources"
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-+#0-9. ]*(?:ll|l|h)?[@diufFeEgGsc]")
INTERPOLATED_LOOKUP = re.compile(r'LocalizedStringKey\(\s*"[^"\n]*\\\(')
STATIC_LOOKUP = re.compile(r'LocalizedStringKey\(\s*"([a-z][a-z0-9_.]+)"\s*\)')


def strings(language: str) -> dict[str, str]:
    path = RESOURCE / f"{language}.lproj" / "Localizable.strings"
    result = subprocess.run(
        ["plutil", "-convert", "json", "-o", "-", str(path)],
        capture_output=True,
        text=True,
        check=True,
    )
    return json.loads(result.stdout)


english = strings("en")
chinese = strings("zh-Hans")
missing_english = sorted(chinese.keys() - english.keys())
missing_chinese = sorted(english.keys() - chinese.keys())
assert not missing_english and not missing_chinese, (
    f"Translation keys differ: en missing {missing_english}; zh-Hans missing {missing_chinese}"
)

for key in english:
    en_args = PLACEHOLDER.findall(english[key].replace("%%", ""))
    zh_args = PLACEHOLDER.findall(chinese[key].replace("%%", ""))
    assert en_args == zh_args, f"Format placeholders differ for {key}: {en_args} vs {zh_args}"

for path in SOURCE.rglob("*.swift"):
    source = path.read_text()
    assert not INTERPOLATED_LOOKUP.search(source), (
        f"{path}: build dynamic resource keys as String before LocalizedStringKey"
    )
    missing = set(STATIC_LOOKUP.findall(source)) - english.keys()
    assert not missing, f"{path}: missing localized keys {sorted(missing)}"

print(f"PASS: {len(english)} English/Simplified Chinese Apple keys and SwiftUI lookups")
