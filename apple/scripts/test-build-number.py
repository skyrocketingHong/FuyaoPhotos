#!/usr/bin/env python3
import concurrent.futures
import os
import plistlib
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


APPLE_ROOT = Path(__file__).resolve().parent.parent


class BuildNumberTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="Fuyao counter tests ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for relative in ("scripts/reserve-build-number.py", "FuyaoPhotos.xcodeproj/project.pbxproj", "Sources/App/Info.plist"):
            destination = self.root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(APPLE_ROOT / relative, destination)
        project = (self.root / "FuyaoPhotos.xcodeproj/project.pbxproj").read_text()
        self.baseline = int(re.search(r"CURRENT_PROJECT_VERSION = ([0-9]+);", project)[1])
        self.counter = self.root / ".build-counter"
        self.script = self.root / "scripts/reserve-build-number.py"
        self.output = self.root / "Derived Files/Numbered-Info.plist"

    def run_script(self, *arguments, environment=None, check=True):
        env = os.environ.copy()
        for key in ("FUYAO_RESERVED_BUILD_NUMBER", "ACTION", "XCODE_RUNNING_FOR_PREVIEWS"):
            env.pop(key, None)
        env.update(environment or {})
        return subprocess.run([sys.executable, str(self.script), *map(str, arguments)],
                              env=env, capture_output=True, text=True, check=check)

    def read_output(self):
        return plistlib.loads(self.output.read_bytes())

    def prepare_processed_plist(self, binary=False):
        self.output.parent.mkdir(parents=True, exist_ok=True)
        contents = {"CFBundleVersion": str(self.baseline), "CFBundleIdentifier": "test.counter",
                    "NSPhotoLibraryUsageDescription": "Library access",
                    "UIApplicationSceneManifest": {"UIApplicationSupportsMultipleScenes": True}}
        self.output.write_bytes(plistlib.dumps(contents, fmt=plistlib.FMT_BINARY if binary else plistlib.FMT_XML))
        self.output.chmod(0o644)
        return contents

    def test_direct_build_stamps_increasing_bundle_versions(self):
        template = self.root / "Sources/App/Info.plist"
        original = template.read_bytes()
        receipt = self.output.parent / "BuildNumber.txt"
        for expected in (self.baseline + 1, self.baseline + 2):
            processed = self.prepare_processed_plist(binary=True)
            self.run_script("--info-plist", self.output, "--receipt", receipt, environment={"ACTION": "build"})
            info = self.read_output()
            self.assertEqual(info["CFBundleVersion"], str(expected))
            self.assertEqual(info["FuyaoBuildTrain"], f"1B{expected}")
            self.assertEqual(info["CFBundleShortVersionString"], "27.1.0")
            self.assertEqual(self.counter.read_text().strip(), str(expected))
            self.assertEqual(receipt.read_text().strip(), f"{expected} 1B{expected} 27.1.0")
            self.assertTrue(self.output.read_bytes().startswith(b"bplist00"))
            self.assertEqual(self.output.stat().st_mode & 0o777, 0o644)
            for key in ("CFBundleIdentifier", "NSPhotoLibraryUsageDescription", "UIApplicationSceneManifest"):
                self.assertEqual(info[key], processed[key])
        self.assertEqual(template.read_bytes(), original)

    def test_pair_reuses_reservation_without_incrementing_twice(self):
        number, train, _ = self.run_script().stdout.split()
        for action in ("build", "install"):
            self.prepare_processed_plist()
            self.run_script("--info-plist", self.output,
                            environment={"FUYAO_RESERVED_BUILD_NUMBER": number, "ACTION": action})
            info = self.read_output()
            self.assertEqual((info["CFBundleVersion"], info["FuyaoBuildTrain"]), (number, train))
            self.assertEqual(self.counter.read_text().strip(), number)

    def test_parallel_reservations_are_unique_and_monotonic(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            numbers = list(pool.map(lambda _: int(self.run_script().stdout.split()[0]), range(8)))
        self.assertEqual(sorted(numbers), list(range(self.baseline + 1, self.baseline + 9)))
        self.assertEqual(int(self.counter.read_text()), self.baseline + 8)

    def test_peek_and_index_build_do_not_reserve_numbers(self):
        self.assertEqual(int(self.run_script("--peek").stdout.split()[0]), self.baseline + 1)
        self.assertFalse(self.counter.exists())
        self.prepare_processed_plist()
        self.run_script("--info-plist", self.output, environment={"ACTION": "indexbuild"})
        self.assertEqual(self.read_output()["CFBundleVersion"], str(self.baseline))
        self.assertFalse(self.counter.exists())

    def test_invalid_counter_is_not_silently_reset(self):
        for value in ("invalid", str(self.baseline - 1)):
            self.prepare_processed_plist()
            original = self.output.read_bytes()
            self.counter.write_text(value)
            result = self.run_script("--info-plist", self.output, check=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(self.counter.read_text(), value)
            self.assertEqual(self.output.read_bytes(), original)

    def test_unissued_reservation_is_rejected(self):
        self.prepare_processed_plist()
        original = self.output.read_bytes()
        result = self.run_script("--info-plist", self.output,
                                 environment={"FUYAO_RESERVED_BUILD_NUMBER": str(self.baseline + 1)}, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.counter.exists())
        self.assertEqual(self.output.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
