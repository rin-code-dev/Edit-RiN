#!/usr/bin/env python3
"""Register existing release signing in Actions Secrets after checking an official APK."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def read_properties(path):
    text = path.read_text(encoding='iso-8859-1')
    logical = []
    pending = ''
    for line in text.splitlines():
        line = line.lstrip() if pending else line
        pending += line
        if (len(pending) - len(pending.rstrip('\\'))) % 2:
            pending = pending[:-1]
            continue
        logical.append(pending); pending = ''
    if pending:
        logical.append(pending)
    def decode(value):
        def replace(match):
            part = match[1]
            return chr(int(part[1:], 16)) if part.startswith('u') else {'n': '\n', 'r': '\r', 't': '\t', 'f': '\f'}.get(part, part)
        value = re.sub(r'\\(u[0-9a-fA-F]{4}|.)', replace, value)
        return value.encode('utf-16-le', 'surrogatepass').decode('utf-16-le')
    result = {}
    for line in logical:
        line = line.lstrip()
        if not line or line.startswith(('#', '!')):
            continue
        escaped = False
        split = len(line)
        for i, char in enumerate(line):
            if not escaped and (char in '=:' or char.isspace()):
                split = i; break
            escaped = not escaped if char == '\\' else False
        rest = line[split:].lstrip()
        if rest.startswith(('=', ':')):
            rest = rest[1:].lstrip()
        result[decode(line[:split])] = decode(rest)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference-apk', type=Path, required=True, help='Trusted official release APK')
    parser.add_argument('--dry-run', action='store_true', help='Check key locally without registering Secrets')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    settings = read_properties(root / 'android/release-signing.properties')
    if not all(settings.get(k) for k in ['storeFile', 'storePassword', 'keyAlias', 'keyPassword']):
        raise RuntimeError('Release signing settings are incomplete')
    key = Path(settings['storeFile'])
    if not key.is_absolute():
        key = root / 'android' / key
    sdk = Path(os.environ.get('ANDROID_HOME') or read_properties(root / 'android/local.properties')['sdk.dir'])
    tools = sdk / 'build-tools/37.0.0'
    badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(args.reference_apk)], text=True)
    if "package: name='com.hikariatelier.app'" not in badging or 'application-debuggable' in badging:
        raise RuntimeError('Reference must be an official release APK')
    certs = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(args.reference_apk)], text=True,
                                    stderr=subprocess.DEVNULL)
    env = dict(os.environ, TASK_RELEASE_STORE_PASSWORD=settings['storePassword'])
    certificate = subprocess.check_output(['keytool', '-exportcert', '-keystore', str(key),
        '-storepass:env', 'TASK_RELEASE_STORE_PASSWORD', '-alias', settings['keyAlias']], env=env,
        stderr=subprocess.DEVNULL)
    pin = hashlib.sha256(certificate).hexdigest()
    if set(re.findall(r'certificate SHA-256 digest: ([a-f0-9]+)', certs)) != {pin}:
        raise RuntimeError('Configured release key does not match the trusted release APK')
    print('Existing key matches trusted official release APK. Certificate SHA256:', pin, flush=True)
    if args.dry_run:
        return
    repo = json.loads(subprocess.check_output(['gh', 'repo', 'view', '--json', 'nameWithOwner'], cwd=root))['nameWithOwner']
    secrets = {'CLOUD_RELEASE_KEYSTORE_BASE64': base64.b64encode(key.read_bytes()).decode(),
               'CLOUD_RELEASE_STORE_PASSWORD': settings['storePassword'],
               'CLOUD_RELEASE_KEY_ALIAS': settings['keyAlias'],
               'CLOUD_RELEASE_KEY_PASSWORD': settings['keyPassword'], 'CLOUD_RELEASE_CERT_SHA256': pin}
    for name, value in secrets.items():
        subprocess.run(['gh', 'secret', 'set', name, '-R', repo], input=value.encode(), check=True)
    print('Registered 5 release signing Secrets. No source push, build or Release publication performed.')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('Release signing setup failed. Check local settings, reference APK and GitHub access; private values omitted.', file=sys.stderr)
        sys.exit(1)
