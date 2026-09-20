#!/usr/bin/env python3
"""Verify resources and keep keys and unexpected font binaries out of the project."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parent.parent
xml_files = list((ROOT/'app/src').rglob('*.xml')) + list((ROOT/'.run').glob('*.xml'))
for path in xml_files:
    ET.parse(path)
strings = {}
for language in ('values', 'values-zh-rCN'):
    strings[language] = {item.attrib['name'] for item in ET.parse(ROOT/f'app/src/main/res/{language}/strings.xml').getroot()}
assert strings['values'] == strings['values-zh-rCN'], 'Translation keys differ'
for path in (ROOT/'app/src/main/java').rglob('*.kt'):
    missing = set(re.findall(r'R\.string\.(\w+)', path.read_text())) - strings['values']
    assert not missing, f'{path}: missing resources {missing}'
manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
assert not manifest.findall('uses-permission'), 'Unexpected permission added'
for path in ROOT.rglob('*'):
    if path.is_file() and not any(x in {'.local','build','.gradle','.git'} for x in path.relative_to(ROOT).parts):
        if path.relative_to(ROOT).as_posix() == 'app/src/main/assets/fonts/SF-Mono-Regular.otf':
            assert path.read_bytes()[:4] == b'OTTO', 'Invalid bundled OpenType font'
            continue
        assert path.suffix.lower() not in {'.ttf','.otf','.ttc','.woff','.woff2','.jks','.keystore','.p12'}, f'Unexpected private/binary asset: {path}'
print(f'PASS: {len(xml_files)} XML files; {len(strings["values"])} matching localized string keys; no declared permissions or unexpected fonts/keys.')
