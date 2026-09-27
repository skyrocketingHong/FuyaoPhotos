#!/usr/bin/env python3
import argparse
import fcntl
import os
import plistlib
import re
import tempfile
from pathlib import Path


APPLE_ROOT = Path(__file__).resolve().parent.parent
PROJECT = APPLE_ROOT / "FuyaoPhotos.xcodeproj" / "project.pbxproj"
COUNTER = APPLE_ROOT / ".build-counter"
LOCK = APPLE_ROOT / ".build" / "build-counter.lock"


def single_project_value(pattern: str, source: str, name: str) -> str:
    values = set(re.findall(pattern, source))
    if len(values) != 1:
        raise ValueError(f"Expected one shared {name} value in the Xcode project; found {sorted(values)}")
    return values.pop()


def read_project_version() -> tuple[int, str, str]:
    source = PROJECT.read_text(encoding="utf-8")
    baseline = int(single_project_value(r"\bCURRENT_PROJECT_VERSION = ([0-9]+);", source, "CURRENT_PROJECT_VERSION"))
    marketing = single_project_value(r"\bMARKETING_VERSION = ([0-9]+\.[0-9]+\.[0-9]+);", source, "MARKETING_VERSION")
    major, minor, patch = map(int, marketing.split("."))
    if major != 27 or not 0 <= minor <= 25 or patch != 0:
        raise ValueError(f"No Build Train mapping for marketing version {marketing}")
    return baseline, marketing, f"1{chr(ord('A') + minor)}"


def read_counter(baseline: int) -> int:
    if not COUNTER.exists():
        return baseline
    value = COUNTER.read_text(encoding="ascii").strip()
    if not value.isdecimal():
        raise ValueError(f"Invalid local build counter at {COUNTER}")
    number = int(value)
    if number < baseline:
        raise ValueError(f"Local build counter {number} is below the Xcode project baseline {baseline}")
    return number


def write_counter(number: int) -> None:
    descriptor, temporary = tempfile.mkstemp(prefix="build-counter.", dir=LOCK.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="ascii") as output:
            output.write(f"{number}\n")
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, COUNTER)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def write_info_plist(path: Path, template: dict, number: int, train: str, marketing: str, binary: bool) -> None:
    template.update(CFBundleVersion=str(number), FuyaoBuildTrain=train, CFBundleShortVersionString=marketing)
    permissions = path.stat().st_mode & 0o777
    descriptor, temporary = tempfile.mkstemp(prefix="numbered-info.", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as output:
            os.fchmod(output.fileno(), permissions)
            plistlib.dump(template, output, fmt=plistlib.FMT_BINARY if binary else plistlib.FMT_XML)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main() -> None:
    parser = argparse.ArgumentParser(description="Reserve one local Apple app build number.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--peek", action="store_true", help="Show the next number without reserving it.")
    mode.add_argument("--info-plist", type=Path, help="Stamp the processed app Info.plist before Xcode signs the bundle.")
    parser.add_argument("--receipt", type=Path, help="Write the build phase's derived output receipt.")
    args = parser.parse_args()
    if args.receipt and not args.info_plist:
        parser.error("--receipt requires --info-plist")

    baseline, marketing, prefix = read_project_version()
    template = None
    binary = False
    if args.info_plist:
        data = args.info_plist.read_bytes()
        template = plistlib.loads(data)
        binary = data.startswith(b"bplist00")
        if not isinstance(template, dict):
            raise ValueError("The processed app Info.plist must contain a dictionary")
    LOCK.parent.mkdir(parents=True, exist_ok=True)
    with LOCK.open("a+") as lock:
        # ASVS 15.4.1/15.4.2: validate and reserve under the same cross-process lock.
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        current = read_counter(baseline)
        reserved = os.environ.get("FUYAO_RESERVED_BUILD_NUMBER", "").strip() if args.info_plist else ""
        if reserved:
            if not reserved.isascii() or not reserved.isdecimal() or not baseline < int(reserved) <= current:
                raise ValueError("FUYAO_RESERVED_BUILD_NUMBER must be a number already issued by this counter")
            number = int(reserved)
        elif args.info_plist and (os.environ.get("ACTION") == "indexbuild" or os.environ.get("XCODE_RUNNING_FOR_PREVIEWS") == "1"):
            number = current
        else:
            number = current + 1
            if not args.peek:
                write_counter(number)
        if args.info_plist:
            # Xcode supplies all platform/permission keys; only stamp versions before CodeSign.
            write_info_plist(args.info_plist, template, number, f"{prefix}{number}", marketing, binary)
        if args.receipt:
            args.receipt.parent.mkdir(parents=True, exist_ok=True)
            args.receipt.write_text(f"{number} {prefix}{number} {marketing}\n", encoding="ascii")
        print(number, f"{prefix}{number}", marketing)


if __name__ == "__main__":
    main()
