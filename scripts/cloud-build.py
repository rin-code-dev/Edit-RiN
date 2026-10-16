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

BRANCH = 'build/cloud'
WORKFLOW = 'build-debug.yml'
WORKFLOWS = {'debug': WORKFLOW, 'release': 'build-release.yml'}
RELEASE_SECRETS = {'CLOUD_RELEASE_KEYSTORE_BASE64', 'CLOUD_RELEASE_STORE_PASSWORD',
                   'CLOUD_RELEASE_KEY_ALIAS', 'CLOUD_RELEASE_KEY_PASSWORD', 'CLOUD_RELEASE_CERT_SHA256'}
# Working-tree changes are limited to APK inputs and the cloud build tools.
INPUTS = ['android', 'www', 'third_party', 'LICENSE', 'THIRD_PARTY_LICENSES.md',
          '.github/workflows/build-debug.yml', '.github/workflows/build-release.yml',
          'scripts/cloud-release-runner.py', 'scripts/setup-cloud-release-signing.py', 'scripts/cloud-build.sh',
          'scripts/cloud-build.py', 'scripts/verify-kotlin-sources.py', 'docs/cloud-build.md']
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


def verify_kotlin_snapshot(root):
    """Detect broken symbols from patches or missing drawables before uploading to cloud."""
    kotlin_dir = root / 'android' / 'app' / 'src' / 'main' / 'java' / 'com' / 'hikariatelier' / 'app'
    drawable_dir = root / 'android' / 'app' / 'src' / 'main' / 'res' / 'drawable'
    if not kotlin_dir.exists():
        return
    problems = []
    if drawable_dir.exists():
        drawables = {p.stem for p in drawable_dir.glob('*')}
        pattern = re.compile(r'R\.drawable\.([a-zA-Z0-9_]+)')
        for kt in kotlin_dir.glob('*.kt'):
            text = kt.read_text(encoding='utf-8')
            for lineno, line in enumerate(text.splitlines(), start=1):
                code = line.split('//')[0]
                for m in pattern.finditer(code):
                    if m.group(1) not in drawables:
                        problems.append(f"{kt.name}:{lineno}: R.drawable.{m.group(1)} does not exist")
    raw_text = re.compile(r'\btarget\.rawText\b')
    broken_helper = re.compile(r'fun\s+formatColorReplacement\s*\(')
    broken_range = re.compile(r'\btarget\.range\.(?:first|last)\b')
    for kt in kotlin_dir.glob('*.kt'):
        text = kt.read_text(encoding='utf-8')
        for lineno, line in enumerate(text.splitlines(), start=1):
            code = line.split('//')[0].strip()
            if not code or code.startswith('*'):
                continue
            if raw_text.search(code):
                problems.append(f"{kt.name}:{lineno}: target.rawText found (EditorColorTarget uses originalText)")
            if broken_helper.search(code):
                problems.append(f"{kt.name}:{lineno}: formatColorReplacement found (use replaceColorTarget)")
            if broken_range.search(code):
                problems.append(f"{kt.name}:{lineno}: target.range.first/last found on TextRange")
    if problems:
        raise RuntimeError("Kotlin source verification failed before upload:\n  " + "\n  ".join(problems))


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
    verify_kotlin_snapshot(destination)
    return len([p for p in destination.rglob('*') if p.is_file()])


def receive(repo, run_id, source_sha, output, build_type="debug", downloaded=None):
    info = json.loads(run(['gh', 'run', 'view', str(run_id), '-R', repo,
                           '--json', 'conclusion,url']))
    if info['conclusion'] != 'success':
        raise RuntimeError(f"Build did not succeed: {info['url']}")
    with tempfile.TemporaryDirectory(prefix='edit-rin-apk-') as temporary:
        directory = Path(temporary)
        if downloaded is None:
            run(['gh', 'run', 'download', str(run_id), '-R', repo, '-n',
                 artifact_name(build_type, run_id), '-D', str(directory)], capture=False)
        else:
            directory = downloaded
        metadata = json.loads((directory / 'build.json').read_text())
        apk = directory / ('app-release.apk' if build_type == 'release' else 'app-debug.apk')
        if ((source_sha is not None and metadata['source_sha'] != source_sha)
                or not re.fullmatch(r'[a-f0-9]{40}', metadata['source_sha'])
                or str(metadata['run_id']) != str(run_id)
                or hashlib.sha256(apk.read_bytes()).hexdigest() != metadata['sha256']
                or not re.fullmatch(r'[a-f0-9]{64}', metadata.get('signer_sha256', ''))):
            raise RuntimeError('Artifact source/run/checksum mismatch')
        if build_type == 'release':
            if (metadata.get('build_type') != 'release'
                    or metadata.get('package_id') != 'com.hikariatelier.app'
                    or metadata.get('debuggable') is not False
                    or not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._-]*', metadata.get('version_name', ''))
                    or not isinstance(metadata.get('version_code'), int)):
                raise RuntimeError('Release artifact variant/package/version mismatch')
            version = metadata['version_name']
            if output is None:
                output = Path.home() / 'Documents' / 'EditRiNデータ' / 'リリース' / f'v{version}'
        elif output is None:
            output = Path.home() / 'Documents' / 'EditRiNデータ' / 'デバッグ'
        output.mkdir(parents=True, exist_ok=True)
        stamp = dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).strftime('%Y%m%d-%H%M')
        target = output / (f'Edit-RiN-v{version}-release-{run_id}.apk' if build_type == 'release'
                           else f'Edit-RiN-debug-{stamp}-{run_id}.apk')
        # Preserve earlier artifacts even if the same run is downloaded again.
        with target.open('xb') as stream:
            stream.write(apk.read_bytes())
        target.with_suffix('.json').write_text(json.dumps(metadata, indent=2) + '\n')
        if build_type == 'release':
            target.with_suffix('.sha256').write_text(f"{metadata['sha256']}  {target.name}\n")
        print(f'APK: {target}\nSHA256: {metadata["sha256"]}\nActions: {info["url"]}', flush=True)


def artifact_name(build_type, run_id):
    return f"Edit-RiN-{'Release' if build_type == 'release' else 'Dev'}-{run_id}"


def preflight_release(repo):
    # Read-only checks before sending any source. Never register keys implicitly.
    try:
        run(['gh', 'api', f'repos/{repo}/actions/workflows/{WORKFLOWS["release"]}'])
    except subprocess.CalledProcessError:
        raise RuntimeError('Release workflow is not registered on GitHub; deploy it when authorized') from None
    names = {s['name'] for s in json.loads(run(['gh', 'secret', 'list', '-R', repo, '--json', 'name']))}
    missing = RELEASE_SECRETS - names
    if missing:
        raise RuntimeError('Release signing Secrets are missing: ' + ', '.join(sorted(missing)))


def run_local_preflight(root):
    """Run fast local preflight tests before creating/uploading snapshot."""
    verify_script = root / 'scripts' / 'verify-kotlin-sources.py'
    if verify_script.exists():
        res = subprocess.run([sys.executable, str(verify_script)], cwd=root)
        if res.returncode != 0:
            raise RuntimeError("Kotlin source verification failed before build.")
    package_json = root / 'package.json'
    if package_json.exists():
        res = subprocess.run(['npm', 'test'], cwd=root)
        if res.returncode != 0:
            raise RuntimeError("Local preflight tests (npm test) failed. Fix issues before cloud build.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('build_type', nargs='?', choices=['debug', 'release'], default='debug')
    parser.add_argument('--test', action='store_true', help='Run unit tests in the cloud')
    parser.add_argument('--dry-run', action='store_true', help='Inspect snapshot without GitHub changes')
    parser.add_argument('--download-run', type=int, help='Download a previously completed run')
    parser.add_argument('--output', type=Path, default=None)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    workflow = WORKFLOWS[args.build_type]
    title = f'Cloud {args.build_type}'
    if args.download_run:
        repo = json.loads(run(['gh', 'repo', 'view', '--json', 'nameWithOwner'], cwd=root))['nameWithOwner']
        metadata = json.loads(run(['gh', 'api', f'repos/{repo}/actions/runs/{args.download_run}']))
        if metadata['path'] != f'.github/workflows/{workflow}':
            raise RuntimeError('This run is not the selected cloud build workflow')
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            run(['gh', 'run', 'download', str(args.download_run), '-R', repo, '-n',
                 artifact_name(args.build_type, args.download_run), '-D', temporary], capture=False)
            receive(repo, args.download_run, None, args.output, args.build_type, directory)
        return
    run_local_preflight(root)
    with tempfile.TemporaryDirectory(prefix='edit-rin-source-') as temporary:
        snapshot = Path(temporary)
        count = prepare_snapshot(root, snapshot)
        print(f'Snapshot checked: {count} files. Local branch/index unchanged.', flush=True)
        if args.dry_run:
            return
        repo_info = json.loads(run(['gh', 'repo', 'view', '--json', 'nameWithOwner,url'], cwd=root))
        repo = repo_info['nameWithOwner']
        if args.build_type == 'release':
            preflight_release(repo)
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
        sha = git(snapshot, 'commit-tree', tree, *parent, '-m', f'{title} snapshot {request_id}').decode().strip()
        print(f'Uploading snapshot to {repo}:{BRANCH} ({sha[:12]})', flush=True)
        git(snapshot, 'push', 'origin', f'{sha}:refs/heads/{BRANCH}')
        payload = json.dumps(dict(ref=BRANCH, inputs=dict(request_id=request_id,
                             source_sha=sha, run_tests=str(args.test).lower()))).encode()
        run(['gh', 'api', '-X', 'POST', f'repos/{repo}/actions/workflows/{workflow}/dispatches',
             '--input', '-'], data=payload)
        deadline = time.monotonic() + 180
        run_id = None
        while time.monotonic() < deadline:
            runs = json.loads(run(['gh', 'run', 'list', '-R', repo, '--workflow', workflow,
                                  '--branch', BRANCH, '--event', 'workflow_dispatch', '--limit', '30',
                                  '--json', 'databaseId,displayTitle']))
            matches = [r for r in runs if r['displayTitle'] == f'{title} {request_id}']
            if len(matches) == 1:
                run_id = matches[0]['databaseId']
                break
            time.sleep(5)
        if run_id is None:
            raise RuntimeError(f'Run not yet found. Look for request {request_id} in Actions; do not dispatch again blindly.')
        print(f'Run: {run_id} — https://github.com/{repo}/actions/runs/{run_id}', flush=True)
        print(f'Resume download: ./scripts/cloud-build.sh {args.build_type} --download-run {run_id}', flush=True)
        run(['gh', 'run', 'watch', str(run_id), '-R', repo, '--exit-status', '--interval', '10'], capture=False)
        receive(repo, run_id, sha, args.output, args.build_type)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, OSError) as error:
        print(f'Cloud build stopped: {error}', file=sys.stderr)
        sys.exit(1)
