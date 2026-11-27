#!/usr/bin/env python3
"""Validate the single release version before any expensive builds or publishing."""
import os
from datetime import datetime
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
    if event == 'push' and ref not in (f'refs/tags/{tag}', 'refs/heads/main'):
        raise ValueError(f'Tag must match version.properties: {tag}')
    if event == 'workflow_dispatch' and ref != 'refs/heads/main':
        raise ValueError('Release dispatch is allowed only from main')
    versions = [version_tuple(t[1:]) for t in tags if re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+', t)]
    if publish and versions and current < max(versions):
        raise ValueError('Refusing to publish an older version than an existing tag')
    return tag, publish


def auto_bump_version(props):
    name = props['versionName']
    code = int(props['versionCode'])
    parts = list(map(int, name.split('.')))
    parts[-1] += 1
    new_name = '.'.join(map(str, parts))

    today_str = datetime.now().strftime('%Y%m%d')
    if str(code).startswith(today_str):
        new_code = code + 1
    else:
        new_code = int(f"{today_str}01")
        if new_code <= code:
            new_code = code + 1

    props['versionName'] = new_name
    props['versionCode'] = str(new_code)

    return f"v{new_name}"


def tag_code(tag):
    """Read the packaged version code, including tags whose source pin was older."""
    annotation = subprocess.check_output(
        ['git', 'for-each-ref', '--format=%(contents:subject)', f'refs/tags/{tag}'], text=True,
    ).strip()
    match = re.fullmatch(r'Telos versionCode=(\d+)', annotation)
    if match:
        return int(match[1])
    old = subprocess.run(['git', 'show', f'{tag}:version.properties'], capture_output=True, text=True)
    if old.returncode == 0:
        return int(dict(line.split('=', 1) for line in old.stdout.splitlines()
                        if line and not line.startswith('#'))['versionCode'])
    build = subprocess.check_output(['git', 'show', f'{tag}:app/app/build.gradle.kts'], text=True)
    return int(re.search(r'versionCode[^\n]*\?: (\d+)', build)[1])


def select_main_version(props, latest, head, read_code, read_commit):
    if latest is None:
        return
    name = latest[1:]
    latest_code = read_code(latest)
    if read_commit(latest) == head and version_tuple(props['versionName']) <= version_tuple(name):
        props.update(versionName=name, versionCode=str(latest_code))
        return
    props['versionCode'] = str(max(int(props['versionCode']), latest_code + 1))
    if version_tuple(props['versionName']) <= version_tuple(name):
        props.update(versionName=name, versionCode=str(latest_code))
        auto_bump_version(props)


def main():
    props = dict(line.split('=', 1) for line in Path('version.properties').read_text().splitlines()
                 if line and not line.startswith('#'))
    tags = subprocess.check_output(['git', 'tag', '-l', 'v*'], text=True).splitlines()
    versions = sorted((t for t in tags if re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+', t)),
                      key=lambda t: version_tuple(t[1:]))
    event = os.environ.get('GITHUB_EVENT_NAME', 'pull_request')
    ref = os.environ.get('GITHUB_REF', '')
    if event in ('push', 'workflow_dispatch'):
        head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        subprocess.run(['git', 'merge-base', '--is-ancestor', head, 'origin/main'], check=True)
        if ref == 'refs/heads/main':
            select_main_version(props, versions[-1] if versions else None, head, tag_code,
                                lambda t: subprocess.check_output(
                                    ['git', 'rev-parse', f'{t}^{{commit}}'], text=True).strip())
        elif ref.startswith('refs/tags/') and ref.removeprefix('refs/tags/') in versions:
            tagged = ref.removeprefix('refs/tags/')
            marker = subprocess.check_output(
                ['git', 'for-each-ref', '--format=%(contents:subject)', ref], text=True,
            ).strip()
            if re.fullmatch(r'Telos versionCode=\d+', marker):
                props.update(versionName=tagged[1:], versionCode=str(tag_code(tagged)))
    tag, publish = validate(props, event, ref, tags)
    if publish:
        previous = [t for t in versions if version_tuple(t[1:]) < version_tuple(props['versionName'])]
        if previous and int(props['versionCode']) <= tag_code(previous[-1]):
            raise ValueError(f"versionCode must exceed the code of {previous[-1]}")
    print(f'tag={tag}')
    print(f"version_code={props['versionCode']}")
    print(f'publish={str(publish).lower()}')


if __name__ == '__main__':
    main()
