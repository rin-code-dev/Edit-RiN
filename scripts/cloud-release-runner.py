#!/usr/bin/env python3
"""Actions-only release signing setup and artifact validation; never publishes releases."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

SECRET_NAMES = ['CLOUD_RELEASE_KEYSTORE_BASE64', 'CLOUD_RELEASE_STORE_PASSWORD',
                'CLOUD_RELEASE_KEY_ALIAS', 'CLOUD_RELEASE_KEY_PASSWORD', 'CLOUD_RELEASE_CERT_SHA256']


def properties_escape(value):
    # Java Properties.load(InputStream) uses Latin-1; escape Unicode and separators.
    out = []
    for char in value:
        code = ord(char)
        if char in '\\=:#! ':
            out.append('\\' + char)
        elif char in '\n\r\t\f':
            out.append({'\n': r'\n', '\r': r'\r', '\t': r'\t', '\f': r'\f'}[char])
        elif code > 126 or code < 32:
            raw = char.encode('utf-16-be')
            out.extend(r'\u%04x' % int.from_bytes(raw[i:i+2], 'big') for i in range(0, len(raw), 2))
        else:
            out.append(char)
    return ''.join(out)


def signing_paths():
    return Path(os.environ['RUNNER_TEMP']) / 'cloud-release-signing', Path('android/release-signing.properties')


def prepare():
    missing = [name for name in SECRET_NAMES if not os.environ.get(name)]
    if missing:
        raise RuntimeError('Missing release Secrets: ' + ', '.join(missing))
    pin = os.environ['CLOUD_RELEASE_CERT_SHA256'].lower()
    if not re.fullmatch('[a-f0-9]{64}', pin):
        raise RuntimeError('Invalid pinned release certificate SHA-256')
    directory, config = signing_paths()
    directory.mkdir(mode=0o700, exist_ok=False)
    key = directory / 'release.keystore'
    with key.open('xb') as stream:
        stream.write(base64.b64decode(os.environ['CLOUD_RELEASE_KEYSTORE_BASE64'], validate=True))
    key.chmod(0o600)
    certificate = subprocess.check_output(['keytool', '-exportcert', '-keystore', str(key),
        '-storepass:env', 'CLOUD_RELEASE_STORE_PASSWORD', '-alias', os.environ['CLOUD_RELEASE_KEY_ALIAS']],
        stderr=subprocess.DEVNULL)
    if hashlib.sha256(certificate).hexdigest() != pin:
        raise RuntimeError('Configured key does not match the pinned official release certificate')
    fields = {'storeFile': str(key), 'storePassword': os.environ['CLOUD_RELEASE_STORE_PASSWORD'],
              'keyAlias': os.environ['CLOUD_RELEASE_KEY_ALIAS'], 'keyPassword': os.environ['CLOUD_RELEASE_KEY_PASSWORD']}
    # This file is created only in the ephemeral runner, never in the source snapshot.
    with config.open('x', encoding='ascii') as stream:
        config.chmod(0o600)
        stream.write('\n'.join(f'{k}={properties_escape(v)}' for k, v in fields.items()) + '\n')
    print('Release key matches the pinned certificate; signing configured')


def validate_release(apk, certificate_output, badging, source_config, expected_certificate):
    certificates = re.findall(r'certificate SHA-256 digest: ([a-f0-9]+)', certificate_output)
    if not certificates or set(certificates) != {expected_certificate}:
        raise RuntimeError('Release APK signature does not match the official certificate')
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    code = re.search(r'\bversionCode\s+(\d+)', source_config)
    version = re.search(r'\bversionName\s+[\'"]([^\'"]+)[\'"]', source_config)
    if (not package or package[1] != 'com.hikariatelier.app' or 'application-debuggable' in badging
            or not code or not version or package[2] != code[1] or package[3] != version[1]):
        raise RuntimeError('Release APK package/debug flag/version differs from source')
    return dict(source_sha=os.environ['SOURCE_SHA'], run_id=os.environ['GITHUB_RUN_ID'],
                sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), signer_sha256=expected_certificate,
                build_type='release', package_id=package[1], version_code=int(package[2]),
                version_name=package[3], debuggable=False)


def verify():
    apk = Path('android/app/build/outputs/apk/release/app-release.apk')
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/37.0.0'
    certificates = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
    info = validate_release(apk, certificates, badging, Path('android/app/build.gradle').read_text(),
                            os.environ['CLOUD_RELEASE_CERT_SHA256'].lower())
    output = Path('cloud-output'); output.mkdir(exist_ok=False)
    (output / 'app-release.apk').write_bytes(apk.read_bytes())
    (output / 'build.json').write_text(json.dumps(info, indent=2) + '\n')
    (output / 'SHA256SUMS.txt').write_text(f"{info['sha256']}  app-release.apk\n")
    print('Signed release APK package, version and certificate verified')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'verify'])
    args = parser.parse_args()
    try:
        prepare() if args.action == 'prepare' else verify()
    except Exception:
        # Key aliases, passwords and local signing paths must not appear in exceptions.
        print('Release signing/verification failed; check configured Secrets and source settings', file=sys.stderr)
        sys.exit(1)
