"""Installer checks execute only inside temporary directories, never on the requested Mac volume."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
INSTALLER = ROOT / 'scripts/install-workspace.py'

class InstallerChecks(unittest.TestCase):
    def invoke(self, destination):
        return subprocess.run([sys.executable, str(INSTALLER), '--destination', str(destination)], capture_output=True, text=True)

    def test_preserves_references_and_git_and_idempotent(self):
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp) / 'Workspace With Spaces'
            (destination / '.git').mkdir(parents=True)
            (destination / '.git/config').write_text('original repository configuration')
            (destination / 'reference.png').write_bytes(b'original-user-reference')
            self.assertEqual(0, self.invoke(destination).returncode)
            self.assertEqual('original repository configuration', (destination / '.git/config').read_text())
            self.assertEqual(b'original-user-reference', (destination / 'reference.png').read_bytes())
            self.assertTrue((destination / 'app/build.gradle.kts').is_file())
            again = self.invoke(destination)
            self.assertEqual(0, again.returncode)
            self.assertIn('Copied 0 new files', again.stdout)

    def test_conflict_aborts_before_any_missing_file_is_copied(self):
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp)
            (destination / 'README.md').write_text('existing code must not be overwritten')
            result = self.invoke(destination)
            self.assertEqual(2, result.returncode)
            self.assertEqual('existing code must not be overwritten', (destination / 'README.md').read_text())
            self.assertFalse((destination / 'app').exists())

    def test_symlink_cannot_redirect_source_writes(self):
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp) / 'project'; destination.mkdir()
            outside = Path(temp) / 'outside'; outside.mkdir()
            (destination / 'app').symlink_to(outside, target_is_directory=True)
            self.assertEqual(2, self.invoke(destination).returncode)
            self.assertEqual([], list(outside.iterdir()))
            self.assertFalse((destination / 'README.md').exists())

    def test_parent_file_is_reported_before_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp)
            (destination / 'app').write_text('not a directory')
            self.assertEqual(2, self.invoke(destination).returncode)
            self.assertFalse((destination / 'README.md').exists())

if __name__ == '__main__':
    unittest.main(verbosity=2)
