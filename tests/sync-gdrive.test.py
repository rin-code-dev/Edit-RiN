#!/usr/bin/env python3
"""No Drive access: isolated local transfers and failure-path tests."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('drive_sync', ROOT / 'scripts/sync-gdrive.py')
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class SyncTests(unittest.TestCase):
    def test_legacy_account_is_not_used_as_remote(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(sync, 'SCRIPT_DIR', Path(directory)), patch.dict(os.environ, {}, clear=True):
            Path(directory, 'sync-gdrive.local.json').write_text('{"account":"legacy"}')
            with self.assertRaises(ValueError):
                sync.load_remote()

    def test_destination_cannot_be_injected(self):
        for value in ['drive:path', ':local:/tmp', '-flag', '../remote', 'drive\nother']:
            with patch.dict(os.environ, {'EDIT_RIN_DRIVE_REMOTE': value}):
                with self.assertRaises(ValueError):
                    sync.load_remote()

    def test_environment_cannot_disable_verification_or_follow_links(self):
        with patch.dict(os.environ, {'RCLONE_SIZE_ONLY':'true', 'RCLONE_COPY_LINKS':'true', 'RCLONE_CONFIG':'/test/config'}):
            env = sync.rclone_environment()
            self.assertNotIn('RCLONE_SIZE_ONLY', env)
            self.assertNotIn('RCLONE_COPY_LINKS', env)
            self.assertEqual('/test/config', env['RCLONE_CONFIG'])

    def test_copy_failure_skips_check(self):
        with patch.object(sync.subprocess, 'run', side_effect=subprocess.CalledProcessError(5, ['rclone'])) as run:
            with self.assertRaises(subprocess.CalledProcessError):
                sync.transfer('rclone', '/source', 'drive:EditRiNデータ', '/filters', '/log')
            self.assertEqual(1, run.call_count)

    def test_check_failure_is_propagated(self):
        with patch.object(sync.subprocess, 'run', side_effect=[None, subprocess.CalledProcessError(1, ['rclone'])]):
            with self.assertRaises(subprocess.CalledProcessError):
                sync.transfer('rclone', '/source', 'drive:EditRiNデータ', '/filters', '/log')

    def test_main_never_reports_success_on_transfer_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            for code in [1, 5]:
                out, err = io.StringIO(), io.StringIO()
                with patch.object(sync, 'LOCAL_ROOT', Path(directory)), patch.object(sync, 'rclone_binary', return_value='rclone'), patch.object(sync, 'load_remote', return_value='test'), patch.object(sync.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, 'test: drive\n')), patch.object(sync, 'transfer', side_effect=subprocess.CalledProcessError(code, [])), patch.dict(os.environ, {'XDG_STATE_HOME':directory}), contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                    self.assertEqual(code, sync.main([]))
                    self.assertNotIn('verification complete', out.getvalue())
                    self.assertIn('NOT confirmed', err.getvalue())

    def test_real_local_transfer(self):
        try:
            binary = sync.rclone_binary()
        except ValueError:
            self.skipTest('Install rclone to run local integration verification')
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            source, dest = base/'source', base/'dest'
            source.mkdir(); dest.mkdir()
            (source/'same-size.txt').write_text('NEW!')
            (dest/'same-size.txt').write_text('OLD!')
            stamp = (source/'same-size.txt').stat().st_mtime
            os.utime(dest/'same-size.txt', (stamp, stamp))
            (dest/'remote-only.txt').write_text('keep')
            for name in ['GEMINI.md', '.env', 'key.JKS', 'nested/AGENTS.md', 'nested/.git/config', 'private-audit.md', 'nested/private-signing/key', 'notes/引き継ぎ.md']:
                file=source/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text('private')
            (source/'release.apk').write_bytes(b'public artifact fixture')
            (source/'link.txt').symlink_to(source/'GEMINI.md')
            env=sync.rclone_environment();env['RCLONE_CONFIG']=str(base/'empty.conf')
            sync.transfer(binary, source, str(dest), sync.FILTERS, base/'dry.log', True, env)
            self.assertEqual('OLD!', (dest/'same-size.txt').read_text())
            self.assertFalse((dest/'release.apk').exists())
            sync.transfer(binary, source, str(dest), sync.FILTERS, base/'copy.log', False, env)
            self.assertEqual('NEW!', (dest/'same-size.txt').read_text())
            self.assertEqual({'same-size.txt', 'remote-only.txt', 'release.apk'}, {str(p.relative_to(dest)) for p in dest.rglob('*') if p.is_file()})
            # Verification detects content corruption even with identical size.
            (dest/'same-size.txt').write_text('BAD!')
            checked=subprocess.run([binary,'check',str(source),str(dest),'--one-way','--exclude-from',str(sync.FILTERS),'--ignore-case','--skip-links'],env=env,capture_output=True)
            self.assertNotEqual(0, checked.returncode)


if __name__ == '__main__':
    unittest.main()
