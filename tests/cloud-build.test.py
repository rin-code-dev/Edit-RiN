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


runner_spec = importlib.util.spec_from_file_location('release_runner', Path(__file__).resolve().parents[1] / 'scripts/cloud-release-runner.py')
runner = importlib.util.module_from_spec(runner_spec)
runner_spec.loader.exec_module(runner)
setup_spec = importlib.util.spec_from_file_location('release_setup', Path(__file__).resolve().parents[1] / 'scripts/setup-cloud-release-signing.py')
setup = importlib.util.module_from_spec(setup_spec)
setup_spec.loader.exec_module(setup)


class ReleaseTest(unittest.TestCase):
    def test_properties_roundtrip_preserves_password_characters(self):
        with tempfile.TemporaryDirectory() as t:
            config = Path(t) / 'properties'
            value = ' leading =:#!\\\t\n日本語😀'
            config.write_text('password=' + runner.properties_escape(value), encoding='ascii')
            self.assertEqual(setup.read_properties(config)['password'], value)

    def test_release_validation_rejects_wrong_signer_debug_package_and_version(self):
        with tempfile.TemporaryDirectory() as t:
            apk = Path(t) / 'apk'; apk.write_bytes(b'apk')
            cert = 'certificate SHA-256 digest: ' + 'b' * 64
            badging = "package: name='com.hikariatelier.app' versionCode='33' versionName='2.4.0'"
            source = 'versionCode 33\nversionName "2.4.0"'
            for c, b in [(cert.replace('b', 'c'), badging), (cert, badging + '\napplication-debuggable'),
                         (cert, badging.replace('com.hikariatelier.app', 'com.hikariatelier.app.debug')),
                         (cert, badging.replace("'33'", "'34'")), (cert, badging.replace('2.4.0', '2.5.0'))]:
                with self.subTest(badging=b), self.assertRaises(RuntimeError):
                    runner.validate_release(apk, c, b, source, 'b' * 64)
            with patch.dict('os.environ', SOURCE_SHA='a' * 40, GITHUB_RUN_ID='42'):
                self.assertFalse(runner.validate_release(apk, cert, badging, source, 'b' * 64)['debuggable'])

    def test_release_download_checks_variant_and_preserves_existing_apk(self):
        payload = b'apk'
        good = dict(source_sha='a'*40, run_id='42', sha256=hashlib.sha256(payload).hexdigest(),
                    signer_sha256='b'*64, build_type='release', package_id='com.hikariatelier.app',
                    version_name='2.4.0', version_code=33, debuggable=False)
        for change in [dict(build_type='debug'), dict(package_id='com.hikariatelier.app.debug'),
                       dict(debuggable=True), dict(version_name='../escape'), dict(version_code='33'), {}]:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as t:
                downloaded=Path(t)/'download'; downloaded.mkdir()
                output=Path(t)/'output'
                (downloaded/'app-release.apk').write_bytes(payload)
                (downloaded/'build.json').write_text(json.dumps(good | change))
                with patch.object(cloud, 'run', return_value=b'{"conclusion":"success","url":"https://example.invalid"}'):
                    if change:
                        with self.assertRaises(RuntimeError):
                            cloud.receive('repo', 42, 'a'*40, output, 'release', downloaded)
                        self.assertFalse(output.exists())
                    else:
                        cloud.receive('repo', 42, 'a'*40, output, 'release', downloaded)
                        target=output/'Edit-RiN-v2.4.0-release-42.apk'
                        self.assertEqual(target.read_bytes(), payload)
                        self.assertIn(target.name, target.with_suffix('.sha256').read_text())
                        with self.assertRaises(FileExistsError):
                            cloud.receive('repo', 42, 'a'*40, output, 'release', downloaded)
                        self.assertEqual(target.read_bytes(), payload)

    def test_missing_secrets_fail_read_only_preflight(self):
        with patch.object(cloud, 'run', side_effect=[b'{}', b'[]']) as run:
            with self.assertRaises(RuntimeError):
                cloud.preflight_release('repo')
            self.assertEqual(len(run.call_args_list), 2)
            self.assertTrue(all('push' not in c.args[0] for c in run.call_args_list))


if __name__ == '__main__':
    unittest.main()
