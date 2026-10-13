#!/usr/bin/env python3
"""Copy Edit:RiN artifacts to Google Drive without deleting destination files."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

SCRIPT_DIR = Path(__file__).resolve().parent
LOCAL_ROOT = Path.home() / 'Documents' / 'EditRiNデータ'
FILTERS = SCRIPT_DIR / 'sync-gdrive.exclude'


def load_remote():
    remote = os.environ.get('EDIT_RIN_DRIVE_REMOTE')
    if not remote:
        config = SCRIPT_DIR / 'sync-gdrive.local.json'
        if config.is_file():
            settings = json.loads(config.read_text(encoding='utf-8'))
            if not isinstance(settings, dict):
                raise ValueError('sync-gdrive.local.json must contain a JSON object')
            remote = settings.get('remote')
    if not isinstance(remote, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]*', remote):
        raise ValueError('Set EDIT_RIN_DRIVE_REMOTE or the local JSON "remote" field to an rclone remote name (without colon/path). The old "account" setting is not an rclone credential. See docs/google-drive-sync.md.')
    return remote


def rclone_binary():
    found = shutil.which('rclone')
    if found:
        return found
    local = Path.home() / '.local' / 'bin' / 'rclone'
    if local.is_file() and os.access(local, os.X_OK):
        return str(local)
    raise ValueError('rclone is not installed. See docs/google-drive-sync.md.')


def rclone_environment():
    # Do not let ambient flags disable checking, follow links, or change filters.
    return {key: value for key, value in os.environ.items()
            if not key.startswith('RCLONE_') or key in {'RCLONE_CONFIG', 'RCLONE_CONFIG_PASS'}}


def transfer(binary, source, destination, filters, logfile, dry_run=False, env=None):
    common = ['--exclude-from', str(filters), '--ignore-case', '--skip-links',
              '--checkers', '4', '--retries', '3', '--low-level-retries', '10',
              '--contimeout', '30s', '--timeout', '5m',
              '--log-file', str(logfile), '--log-level', 'INFO', '--stats', '30s']
    command = [binary, 'copy', str(source), destination, '--checksum', '--transfers', '2', *common]
    if dry_run:
        command.append('--dry-run')
    subprocess.run(command, check=True, env=env)
    if not dry_run:
        # Extra remote files are intentionally retained and ignored by this check.
        subprocess.run([binary, 'check', str(source), destination, '--one-way', *common],
                       check=True, env=env)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true', help='Preview transfers without modifying Drive')
    args = parser.parse_args(argv)
    logfile = None
    try:
        binary = rclone_binary()
        remote = load_remote()
        if not LOCAL_ROOT.is_dir() or LOCAL_ROOT.is_symlink():
            raise ValueError('The local artifact directory must exist and must not be a symlink.')
        if not FILTERS.is_file():
            raise ValueError('Required exclusion file is missing.')
        env = rclone_environment()
        # listremotes does not expose OAuth tokens or contact Drive.
        result = subprocess.run([binary, 'listremotes', '--long'], check=True,
                                capture_output=True, text=True, env=env)
        remotes = dict(re.findall(r'^([^:\r\n]+):\s+(\S+)\s*$', result.stdout, re.M))
        if remotes.get(remote) != 'drive':
            raise ValueError('The selected remote must be a configured Google Drive (drive) remote. See docs/google-drive-sync.md.')
        state = Path(os.environ.get('XDG_STATE_HOME') or Path.home() / '.local/state') / 'edit-rin/drive-sync'
        state.mkdir(parents=True, exist_ok=True, mode=0o700)
        fd, name = tempfile.mkstemp(prefix=dt.datetime.now().strftime('%Y%m%d-%H%M%S-'),
                                    suffix='.log', dir=state)
        os.close(fd)
        logfile = Path(name)
        destination = f'{remote}:EditRiNデータ'
        print(f'Local: {LOCAL_ROOT}\nDestination: {destination}\nLog: {logfile}', flush=True)
        transfer(binary, LOCAL_ROOT, destination, FILTERS, logfile, args.dry_run, env)
        print('Dry run complete; Drive was not modified.' if args.dry_run
              else 'Copy and checksum verification complete. No destination files deleted.', flush=True)
        return 0
    except subprocess.CalledProcessError as error:
        print(f'Drive operation failed (exit {error.returncode}); completion is NOT confirmed.', file=sys.stderr)
        if logfile:
            print(f'Inspect log: {logfile}', file=sys.stderr)
        return error.returncode if 0 < error.returncode < 126 else 1
    except (OSError, ValueError) as error:
        print(f'Drive operation stopped: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
