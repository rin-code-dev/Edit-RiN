"""Exercise source selection and protection of the user's local Git state."""
import importlib.util
import hashlib
import json
from unittest.mock import patch
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('cloud_build', Path(__file__).resolve().parents[1] / 'scripts/cloud-build.py')
cloud = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cloud)


class SnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'source'
        self.output = Path(self.temp.name) / 'snapshot'
        self.root.mkdir()
        self.output.mkdir()
        cloud.git(self.root, 'init', '-q')
        cloud.git(self.root, 'config', 'user.name', 'test')
        cloud.git(self.root, 'config', 'user.email', 'test@example.invalid')
        for name in ['android/keep.kt', 'android/delete.kt', 'www/host.js', 'website/index.html']:
            self.write(name, 'original')
        cloud.git(self.root, 'add', '.')
        cloud.git(self.root, 'commit', '-qm', 'baseline')

    def write(self, name, content):
        p = self.root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content)

    def test_live_inputs_and_deletions_preserve_unrelated_content_and_index(self):
        self.write('android/keep.kt', 'staged')
        cloud.git(self.root, 'add', 'android/keep.kt')
        self.write('android/keep.kt', 'unstaged')
        (self.root / 'android/delete.kt').unlink()
        self.write('www/new.js', 'new input')
        self.write('website/index.html', 'unrelated edit')
        self.write('website-astra/new.html', 'unrelated new file')
        self.write('.gitignore', '*.keystore\n')
        self.write('android/private.keystore', 'ignored key')
        status = cloud.git(self.root, 'status', '--porcelain=v1')
        staged = cloud.git(self.root, 'diff', '--cached')
        head = cloud.git(self.root, 'rev-parse', 'HEAD')
        cloud.prepare_snapshot(self.root, self.output)
        self.assertEqual('unstaged', (self.output / 'android/keep.kt').read_text())
        self.assertFalse((self.output / 'android/delete.kt').exists())
        self.assertEqual('new input', (self.output / 'www/new.js').read_text())
        self.assertEqual('original', (self.output / 'website/index.html').read_text())
        self.assertFalse((self.output / 'website-astra').exists())
        self.assertFalse((self.output / 'android/private.keystore').exists())
        self.assertEqual(status, cloud.git(self.root, 'status', '--porcelain=v1'))
        self.assertEqual(staged, cloud.git(self.root, 'diff', '--cached'))
        self.assertEqual(head, cloud.git(self.root, 'rev-parse', 'HEAD'))

    def test_initial_format_check_ignores_existing_whitespace_but_rejects_new_whitespace(self):
        self.write('android/existing.kt', 'old whitespace   \n')
        cloud.git(self.root, 'add', '.')
        cloud.git(self.root, 'commit', '-qm', 'existing formatting')
        cloud.prepare_snapshot(self.root, self.output)
        cloud.git(self.output, 'init', '-q')
        cloud.git(self.output, 'fetch', '-q', '--depth=1', str(self.root), 'HEAD')
        baseline = cloud.git(self.output, 'rev-parse', 'FETCH_HEAD').decode().strip()
        cloud.git(self.output, 'add', '.')
        cloud.git(self.output, 'diff', '--cached', '--check', baseline)
        (self.output / 'www/new.js').write_text('new whitespace   \n')
        cloud.git(self.output, 'add', '.')
        with self.assertRaises(subprocess.CalledProcessError):
            cloud.git(self.output, 'diff', '--cached', '--check', baseline)

    def test_tracked_private_file_is_rejected(self):
        self.write('android/local.properties', 'private')
        cloud.git(self.root, 'add', 'android/local.properties')
        with self.assertRaises(RuntimeError):
            cloud.prepare_snapshot(self.root, self.output)

    def test_symlink_is_rejected(self):
        (self.root / 'www/link.js').symlink_to('../android/keep.kt')
        with self.assertRaises(RuntimeError):
            cloud.prepare_snapshot(self.root, self.output)

    def test_credentials_are_rejected_without_printing_value(self):
        secret = 'gh' + 'p_' + 'a' * 36
        self.write('www/new.js', secret)
        with self.assertRaises(RuntimeError) as result:
            cloud.prepare_snapshot(self.root, self.output)
        self.assertNotIn(secret, str(result.exception))


class ArtifactTest(unittest.TestCase):
    def test_wrong_run_source_checksum_or_missing_signature_check_never_saves_apk(self):
        payload = b'APK test payload'
        good = dict(source_sha='a' * 40, run_id='42',
                    sha256=hashlib.sha256(payload).hexdigest(), signer_sha256='b' * 64)
        for changed in [dict(run_id='43'), dict(source_sha='c' * 40),
                        dict(sha256='0' * 64), dict(signer_sha256='')]:
            with self.subTest(changed=changed), tempfile.TemporaryDirectory() as t:
                output = Path(t) / 'output'
                def fake_run(args, **kwargs):
                    if 'view' in args:
                        return json.dumps(dict(conclusion='success', url='https://example.invalid/run/42')).encode()
                    directory = Path(args[args.index('-D') + 1])
                    (directory / 'app-debug.apk').write_bytes(payload)
                    (directory / 'build.json').write_text(json.dumps(good | changed))
                with patch.object(cloud, 'run', side_effect=fake_run):
                    with self.assertRaises(RuntimeError):
                        cloud.receive('example/repo', 42, 'a' * 40, output)
                self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
