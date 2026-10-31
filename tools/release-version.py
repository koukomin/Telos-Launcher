#!/usr/bin/env python3
"""Validate the single release version before any expensive builds or publishing."""
import json
import os
from pathlib import Path
import re
import subprocess


def version_tuple(value):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", value):
        raise ValueError(f"Invalid semantic version: {value}")
    return tuple(map(int, value.split('.')))


def validate(properties, event, ref, tags):
    name = properties['versionName']
    current = version_tuple(name)
    code = int(properties['versionCode'])
    if not 0 < code <= 2100000000:
        raise ValueError('versionCode must be a positive Android version code')
    tag = f'v{name}'
    publish = event in ('push', 'workflow_dispatch')
    if event == 'push' and ref != f'refs/tags/{tag}':
        raise ValueError(f'Tag must match version.properties: {tag}')
    if event == 'workflow_dispatch' and ref != 'refs/heads/main':
        raise ValueError('Release dispatch is allowed only from main')
    versions = [version_tuple(t[1:]) for t in tags if re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+', t)]
    if publish and versions and current < max(versions):
        raise ValueError('Refusing to publish an older version than an existing tag')
    return tag, publish


def main():
    props = dict(line.split('=', 1) for line in Path('version.properties').read_text().splitlines()
                 if line and not line.startswith('#'))
    tags = subprocess.check_output(['git', 'tag', '-l', 'v*'], text=True).splitlines()
    tag, publish = validate(props, os.environ.get('GITHUB_EVENT_NAME', 'pull_request'),
                            os.environ.get('GITHUB_REF', ''), tags)
    if publish:
        head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        subprocess.run(['git', 'merge-base', '--is-ancestor', head, 'origin/main'], check=True)
        if tag in tags:
            tagged = subprocess.check_output(['git', 'rev-parse', f'{tag}^{{commit}}'], text=True).strip()
            if tagged != head:
                raise ValueError('Existing tag points to a different commit')
        previous = sorted((t for t in tags if re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+', t)
                           and version_tuple(t[1:]) < version_tuple(props['versionName'])),
                          key=lambda t: version_tuple(t[1:]))
        if previous:
            old = subprocess.run(['git', 'show', f'{previous[-1]}:version.properties'],
                                 capture_output=True, text=True)
            if old.returncode == 0:
                old_code = int(dict(line.split('=', 1) for line in old.stdout.splitlines()
                                    if line and not line.startswith('#'))['versionCode'])
            else:
                old_build = subprocess.check_output([
                    'git', 'show', f'{previous[-1]}:app/app/build.gradle.kts'], text=True)
                old_code = int(re.search(r'versionCode[^\n]*\?: (\d+)', old_build)[1])
            if int(props['versionCode']) <= old_code:
                raise ValueError(f'versionCode must exceed {old_code} from {previous[-1]}')
        releases = json.loads(subprocess.check_output([
            'gh', 'api', '--paginate', '--slurp', f'repos/{os.environ["GITHUB_REPOSITORY"]}/releases',
        ], text=True))
        for release in (release for page in releases for release in page):
            if release['tag_name'] == tag and not release['draft']:
                raise ValueError(f'{tag} is already published; bump version.properties')
    print(f'tag={tag}')
    print(f'publish={str(publish).lower()}')


if __name__ == '__main__':
    main()
