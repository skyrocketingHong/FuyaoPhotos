#!/usr/bin/env python3
"""Convert the supplied SF SVG exports into Android vectors; do not rasterize paths."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NUMBER = r'[-+]?(?:\d*\.)?\d+(?:[eE][-+]?\d+)?'
for name, drawable in [('livephoto.svg', 'ic_motion'), ('hdr.viewfinder.rectangular.svg', 'ic_hdr')]:
    source = ROOT.parent / 'assets/icons/sf-symbols' / name
    root = ET.parse(source).getroot()
    group = next((e for e in root.iter() if e.get('id') == 'Regular-S'), root)
    paths = [e for e in group.iter() if e.tag.split('}')[-1] == 'path']
    coordinates = []
    for path in paths:
        data = path.attrib['d']
        assert not (set(re.findall('[A-Za-z]', re.sub(NUMBER, '', data))) - set('MCLZ'))
        values = [float(v) for v in re.findall(NUMBER, data)]
        assert len(values) % 2 == 0
        coordinates.extend(zip(values[::2], values[1::2]))
    left = min(x for x, y in coordinates); right = max(x for x, y in coordinates)
    top = min(y for x, y in coordinates); bottom = max(y for x, y in coordinates)
    scale = 20 / max(right-left, bottom-top)
    dx = 12-(left+right)/2*scale; dy = 12-(top+bottom)/2*scale
    lines = [f'<!-- Source: assets/icons/sf-symbols/{name}; original path data retained. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">',
        f'    <group android:scaleX="{scale:.8f}" android:scaleY="{scale:.8f}" android:translateX="{dx:.8f}" android:translateY="{dy:.8f}">']
    for path in paths:
        lines.append(f'        <path android:fillColor="#FF000000" android:fillAlpha="{path.get("fill-opacity", "1")}" android:pathData="{path.attrib["d"]}" />')
    lines += ['    </group>', '</vector>']
    (ROOT / 'app/src/main/res/drawable' / f'{drawable}.xml').write_text('\n'.join(lines)+'\n')
    print(name, len(paths), 'vector paths')
