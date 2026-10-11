#!/usr/bin/env python3
"""Send a reviewed source snapshot to Actions without staging or switching local Git."""
import argparse
import datetime as dt
import getpass
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
import uuid

BRANCH = 'codex/cloud-build'
WORKFLOW = 'build-debug.yml'
# Working-tree changes are limited to APK inputs and the cloud build tools.
INPUTS = ['android', 'www', 'third_party', 'LICENSE', 'THIRD_PARTY_LICENSES.md',
          '.github/workflows/build-debug.yml', 'scripts/cloud-build.sh',
          'scripts/cloud-build.py', 'docs/cloud-build.md']
PRIVATE_NAMES = {'AGENTS.md', 'GEMINI.md', 'MODIFICATIONS.md', 'GITHUB_RELEASE_GUIDE.md',
                 'EDIT_RIN_IMPLEMENTATION_SPEC.txt', 'local.properties',
                 'release-signing.properties', 'sync-gdrive.local.json'}
PRIVATE_PARTS = {'.git', '.agents', '.codex', '.idea', '.vscode', '.gradle', '.kotlin',
                 'build', 'node_modules', 'private-signing', '__pycache__'}
PRIVATE_SUFFIXES = {'.keystore', '.jks', '.pem', '.key', '.p12', '.pfx', '.apk', '.aab', '.log'}


def run(args, cwd=None, data=None, capture=True):
    return subprocess.run(args, cwd=cwd, input=data, check=True,
                          stdout=subprocess.PIPE if capture else None).stdout


def git(root, *args):
    return run(['git', *args], cwd=root)


def forbidden(path):
    p = Path(path)
    return (p.name in PRIVATE_NAMES or bool(set(p.parts) & PRIVATE_PARTS)
            or p.suffix.lower() in PRIVATE_SUFFIXES or p.name.startswith('.env'))


def audit(root):
    # Report only the path, never matched credentials or personal values.
    personal = [str(Path.home()).encode(), getpass.getuser().encode()]
    patterns = [rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
                rb'gh[pousr]_[A-Za-z0-9]{20,}', rb'github_pat_[A-Za-z0-9_]{20,}',
                rb'AIza[0-9A-Za-z_-]{30,}']
    for p in root.rglob('*'):
        if '.git' in p.relative_to(root).parts:
            continue
        relative = p.relative_to(root)
        if forbidden(relative) or p.is_symlink():
            raise RuntimeError(f'Private or symlink path excluded from upload: {relative}')
        if p.is_file():
            content = p.read_bytes()
            if any(value and value in content for value in personal) or any(
                    re.search(pattern, content) for pattern in patterns):
                raise RuntimeError(f'Potential private content; review before upload: {relative}')


def prepare_snapshot(root, destination):
    """HEAD for unrelated files; live contents/deletions/new files for build inputs."""
    with tarfile.open(fileobj=io.BytesIO(git(root, 'archive', 'HEAD'))) as archive:
        archive.extractall(destination, filter='data')
    paths = git(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard',
                '--', *INPUTS).decode().split('\0')
    for item in INPUTS:
        p = destination / item
        if p.is_dir():
            shutil.rmtree(p)
        elif p.exists() or p.is_symlink():
            p.unlink()
    for name in filter(None, paths):
        if forbidden(name):
            raise RuntimeError(f'Private build input: {name}')
        source = root / name
        if source.is_symlink():
            raise RuntimeError(f'Symlink build input: {name}')
        if not source.exists():
            continue
        if not source.is_file():
            raise RuntimeError(f'Unsupported build input: {name}')
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    audit(destination)
    return len([p for p in destination.rglob('*') if p.is_file()])


def receive(repo, run_id, source_sha, output):
    info = json.loads(run(['gh', 'run', 'view', str(run_id), '-R', repo,
                           '--json', 'conclusion,url']))
    if info['conclusion'] != 'success':
        raise RuntimeError(f"Build did not succeed: {info['url']}")
    with tempfile.TemporaryDirectory(prefix='edit-rin-apk-') as temporary:
        directory = Path(temporary)
        run(['gh', 'run', 'download', str(run_id), '-R', repo, '-n',
             f'Edit-RiN-Dev-{run_id}', '-D', str(directory)], capture=False)
        metadata = json.loads((directory / 'build.json').read_text())
        apk = directory / 'app-debug.apk'
        if (metadata['source_sha'] != source_sha or str(metadata['run_id']) != str(run_id)
                or hashlib.sha256(apk.read_bytes()).hexdigest() != metadata['sha256']
                or not re.fullmatch(r'[a-f0-9]{64}', metadata.get('signer_sha256', ''))):
            raise RuntimeError('Artifact source/run/checksum mismatch')
        output.mkdir(parents=True, exist_ok=True)
        stamp = dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).strftime('%Y%m%d-%H%M')
        target = output / f'Edit-RiN-debug-{stamp}-{run_id}.apk'
        # Preserve earlier artifacts even if the same run is downloaded again.
        with target.open('xb') as stream:
            stream.write(apk.read_bytes())
        target.with_suffix('.json').write_text(json.dumps(metadata, indent=2) + '\n')
        print(f'APK: {target}\nSHA256: {metadata["sha256"]}\nActions: {info["url"]}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('build_type', nargs='?', choices=['debug'], default='debug')
    parser.add_argument('--test', action='store_true', help='Run unit tests in the cloud')
    parser.add_argument('--dry-run', action='store_true', help='Inspect snapshot without GitHub changes')
    parser.add_argument('--download-run', type=int, help='Download a previously completed run')
    parser.add_argument('--output', type=Path, default=Path.home() / 'Documents' / 'EditRiNデータ' / 'デバッグ')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    if args.download_run:
        repo = json.loads(run(['gh', 'repo', 'view', '--json', 'nameWithOwner'], cwd=root))['nameWithOwner']
        metadata = json.loads(run(['gh', 'api', f'repos/{repo}/actions/runs/{args.download_run}']))
        # The workflow checks out the requested SHA; read it from the exact uploaded metadata.
        with tempfile.TemporaryDirectory() as temporary:
            run(['gh', 'run', 'download', str(args.download_run), '-R', repo, '-n',
                 f'Edit-RiN-Dev-{args.download_run}', '-D', temporary], capture=False)
            sha = json.loads((Path(temporary) / 'build.json').read_text())['source_sha']
        if metadata['path'] != f'.github/workflows/{WORKFLOW}':
            raise RuntimeError('This run is not the cloud debug workflow')
        receive(repo, args.download_run, sha, args.output)
        return
    with tempfile.TemporaryDirectory(prefix='edit-rin-source-') as temporary:
        snapshot = Path(temporary)
        count = prepare_snapshot(root, snapshot)
        print(f'Snapshot checked: {count} files. Local branch/index unchanged.', flush=True)
        if args.dry_run:
            return
        repo_info = json.loads(run(['gh', 'repo', 'view', '--json', 'nameWithOwner,url'], cwd=root))
        repo = repo_info['nameWithOwner']
        user = json.loads(run(['gh', 'api', 'user']))
        url = repo_info['url'] + '.git'
        git(snapshot, 'init', '-q')
        git(snapshot, 'config', 'user.name', user['login'])
        git(snapshot, 'config', 'user.email', f"{user['id']}+{user['login']}@users.noreply.github.com")
        git(snapshot, 'remote', 'add', 'origin', url)
        # Compare formatting against local HEAD, not an empty first-build tree.
        git(snapshot, 'fetch', '-q', '--depth=1', str(root), 'HEAD')
        baseline = git(snapshot, 'rev-parse', 'FETCH_HEAD').decode().strip()
        git(snapshot, 'update-ref', 'refs/heads/source-base', baseline)
        # Explicit ref only. A competing push is rejected; never force or retry blindly.
        remote = git(snapshot, 'ls-remote', 'origin', f'refs/heads/{BRANCH}').decode().strip()
        # Start from the current public history so Git transfers only new blobs.
        git(snapshot, 'fetch', '-q', '--depth=1', 'origin', 'refs/heads/main')
        public_main = git(snapshot, 'rev-parse', 'FETCH_HEAD').decode().strip()
        git(snapshot, 'update-ref', 'refs/remotes/origin/main', public_main)
        parent = ['-p', public_main]
        if remote:
            git(snapshot, 'fetch', '-q', '--depth=1', 'origin', f'refs/heads/{BRANCH}')
            parent = ['-p', git(snapshot, 'rev-parse', 'FETCH_HEAD').decode().strip()]
        git(snapshot, 'add', '--all')
        git(snapshot, 'update-index', '--chmod=+x', 'android/gradlew', 'scripts/cloud-build.sh')
        git(snapshot, 'diff', '--cached', '--check', baseline)
        tree = git(snapshot, 'write-tree').decode().strip()
        request_id = uuid.uuid4().hex
        sha = git(snapshot, 'commit-tree', tree, *parent, '-m', f'Cloud debug snapshot {request_id}').decode().strip()
        print(f'Uploading snapshot to {repo}:{BRANCH} ({sha[:12]})', flush=True)
        git(snapshot, 'push', 'origin', f'{sha}:refs/heads/{BRANCH}')
        payload = json.dumps(dict(ref=BRANCH, inputs=dict(request_id=request_id,
                             source_sha=sha, run_tests=str(args.test).lower()))).encode()
        run(['gh', 'api', '-X', 'POST', f'repos/{repo}/actions/workflows/{WORKFLOW}/dispatches',
             '--input', '-'], data=payload)
        deadline = time.monotonic() + 180
        run_id = None
        while time.monotonic() < deadline:
            runs = json.loads(run(['gh', 'run', 'list', '-R', repo, '--workflow', WORKFLOW,
                                  '--branch', BRANCH, '--event', 'workflow_dispatch', '--limit', '30',
                                  '--json', 'databaseId,displayTitle']))
            matches = [r for r in runs if r['displayTitle'] == f'Cloud debug {request_id}']
            if len(matches) == 1:
                run_id = matches[0]['databaseId']
                break
            time.sleep(5)
        if run_id is None:
            raise RuntimeError(f'Run not yet found. Look for request {request_id} in Actions; do not dispatch again blindly.')
        print(f'Run: {run_id} — https://github.com/{repo}/actions/runs/{run_id}', flush=True)
        print(f'Resume download: ./scripts/cloud-build.sh --download-run {run_id}', flush=True)
        run(['gh', 'run', 'watch', str(run_id), '-R', repo, '--exit-status', '--interval', '10'], capture=False)
        receive(repo, run_id, sha, args.output)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, OSError) as error:
        print(f'Cloud build stopped: {error}', file=sys.stderr)
        sys.exit(1)
