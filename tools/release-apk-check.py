#!/usr/bin/env python3
"""Check native library contents on both PR and production release builds."""
import os
from pathlib import Path
from zipfile import ZipFile


def verify(path, expected):
    with ZipFile(path) as apk:
        abis = {name.split('/')[1] for name in apk.namelist()
                if name.startswith('lib/') and name.endswith('.so')}
    if abis != {expected}:
        raise ValueError(f'{path}: expected native libraries for {expected}, found {sorted(abis)}')


if __name__ == '__main__':
    apks = list(Path('app/app/build/outputs/apk/default/release').glob('*.apk'))
    if len(apks) != 1:
        raise ValueError(f'Expected exactly one release APK, found {len(apks)}')
    verify(apks[0], os.environ['EXPECTED_ABI'])
    print(f'Native ABI verified: {os.environ["EXPECTED_ABI"]}')
