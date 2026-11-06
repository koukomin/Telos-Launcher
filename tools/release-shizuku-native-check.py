#!/usr/bin/env python3
"""Check actual packaged Shizuku binaries, including ARM64 16 KB ELF/RELRO alignment."""
# SPDX-License-Identifier: GPL-3.0-or-later
import argparse
from pathlib import Path
import struct
import zipfile


def check_arm64(data, name):
    assert data[:6] == b'\x7fELF\x02\x01', f'{name}: expected little-endian ELF64'
    assert struct.unpack_from('<H', data, 18)[0] == 183, f'{name}: expected AArch64'
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    assert size >= 56 and offset + size * count <= len(data), f'{name}: invalid program headers'
    headers = [struct.unpack_from('<IIQQQQQQ', data, offset + i * size) for i in range(count)]
    loads = [p for p in headers if p[0] == 1]
    assert loads, f'{name}: no LOAD segments'
    for p in loads:
        assert p[7] >= 16384 and (p[2] - p[3]) % 16384 == 0, f'{name}: LOAD is not 16 KB aligned'
    for relro in (p for p in headers if p[0] == 0x6474e552):
        end = relro[3] + relro[6]
        containing = [p for p in loads if p[3] <= relro[3] and end <= p[3] + p[6]]
        assert end % 16384 == 0 or any(p[3] + p[6] == end for p in containing), f'{name}: unsafe RELRO trailing page'


def check_apk(apk, require_both=False):
    with zipfile.ZipFile(apk) as archive:
        names = set(archive.namelist())
        abis = {name.split('/')[1] for name in names if name.startswith('lib/') and name.endswith('.so')}
        arm_abis = {'arm64-v8a', 'armeabi-v7a'}
        if require_both:
            # AndroidX test dependencies include emulator binaries. They are not release payloads.
            assert arm_abis <= abis, f'harness is missing an ARM ABI: {abis}'
            for abi in abis - arm_abis:
                assert not any(f'lib/{abi}/{lib}' in names for lib in ('libadb.so', 'librish.so', 'libshizuku.so')), f'unsupported Shizuku ABI: {abi}'
        else:
            assert abis and abis <= arm_abis, f'unexpected packaged ABIs: {abis}'
        for abi in sorted(abis & arm_abis):
            for library in ('libadb.so', 'librish.so', 'libshizuku.so'):
                name = f'lib/{abi}/{library}'
                assert name in names, f'missing {name}'
                data = archive.read(name)
                assert data.startswith(b'\x7fELF'), f'{name}: not an ELF binary'
                if abi == 'arm64-v8a':
                    check_arm64(data, name)
                print(f'OK {name}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-both', action='store_true')
    parser.add_argument('apk', nargs='?', type=Path)
    args = parser.parse_args()
    apks = [args.apk] if args.apk else list(Path('app/app/build/outputs/apk/default/release').glob('*.apk'))
    assert len(apks) == 1, 'expected exactly one APK'
    check_apk(apks[0], args.require_both)
