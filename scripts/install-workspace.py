#!/usr/bin/env python3
"""Copy this source handoff to the requested Mac folder without overwriting differing files."""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import sys

DEFAULT = Path('/Volumes/Thunderbolt 5 SSD (2TB)/Code/GitHub/FuyaoPhotos')
SKIP = {'.git', '.gradle', '.kotlin', '.local', 'build', '.build', '.swiftpm', 'DerivedData', 'xcuserdata', '__pycache__'}
PRIVATE = {'local.properties', 'signing.properties'}
PRIVATE_SUFFIXES = {'.ttf', '.otf', '.ttc', '.woff', '.woff2', '.jks', '.keystore', '.p12'}

def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open('rb') as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=Path, default=DEFAULT)
    parser.add_argument('--build', action='store_true', help='Run the Android checks/build after copying.')
    args = parser.parse_args()
    source = Path(__file__).resolve().parent.parent
    destination = args.destination.expanduser().resolve()
    if str(destination).startswith('/Volumes/'):
        volume = Path('/Volumes') / destination.parts[2]
        if not volume.is_mount():
            raise RuntimeError(f'Target volume is not mounted: {volume}')
    if source != destination:
        entries = []
        conflicts = []
        for file in sorted(source.rglob('*')):
            relative = file.relative_to(source)
            if any(part in SKIP for part in relative.parts) or file.name in PRIVATE or file.suffix.lower() in PRIVATE_SUFFIXES:
                continue
            if file.is_symlink():
                raise RuntimeError(f'Refusing source symlink: {relative}')
            if not file.is_file():
                continue
            target = destination / relative
            # Existing symlinked descendants could write outside the requested workspace.
            cursor = target
            while cursor != destination.parent:
                if cursor.is_symlink():
                    conflicts.append(str(relative) + ' (symlink)')
                    break
                if cursor != target and cursor.exists() and not cursor.is_dir():
                    conflicts.append(str(relative) + ' (parent is not a directory)')
                    break
                if cursor == destination:
                    break
                cursor = cursor.parent
            if target.exists() and (not target.is_file() or digest(file) != digest(target)):
                conflicts.append(str(relative))
            entries.append((file, target))
        if conflicts:
            print('No files copied. Existing differing files must be reviewed first:', file=sys.stderr)
            print('\n'.join(sorted(set(conflicts))), file=sys.stderr)
            return 2
        destination.mkdir(parents=True, exist_ok=True)
        copied = 0
        for file, target in entries:
            if target.exists():
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            # Exclusive creation protects against accidental concurrent file replacement.
            with file.open('rb') as src, target.open('xb') as dst:
                shutil.copyfileobj(src, dst)
            shutil.copystat(file, target)
            copied += 1
        print(f'Copied {copied} new files to {destination}; existing references and .git were preserved.')
    else:
        print(f'Already in target workspace: {destination}')
    if args.build:
        return subprocess.call(['bash', str(destination / 'android/scripts/build-macos.sh')], cwd=destination)
    return 0

if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        raise SystemExit(1)
