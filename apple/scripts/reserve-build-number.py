#!/usr/bin/env python3
import argparse
import fcntl
import os
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


def main() -> None:
    parser = argparse.ArgumentParser(description="Reserve one local Apple app build number.")
    parser.add_argument("--peek", action="store_true", help="Show the next number without reserving it.")
    args = parser.parse_args()

    baseline, marketing, prefix = read_project_version()
    LOCK.parent.mkdir(parents=True, exist_ok=True)
    with LOCK.open("a+") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        number = read_counter(baseline) + 1
        if not args.peek:
            write_counter(number)
        print(number, f"{prefix}{number}", marketing)


if __name__ == "__main__":
    main()
