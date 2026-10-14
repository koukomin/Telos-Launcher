#!/usr/bin/env python3
"""Report APK ZIP sizes without extracting or loading entry payloads into memory."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def category(name):
    if name.endswith('.dex') and '/' not in name:
        return 'dex'
    if name.startswith('lib/'):
        return 'native'
    if name == 'resources.arsc':
        return 'resource_table'
    if name.startswith('res/'):
        return 'resources'
    if name.startswith('assets/'):
        return 'assets'
    if name.startswith('META-INF/'):
        return 'metadata'
    return 'other'


def report(path, top=30):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    categories = {}
    with zipfile.ZipFile(path) as archive:
        entries = [entry for entry in archive.infolist() if not entry.is_dir()]
        for entry in entries:
            row = categories.setdefault(category(entry.filename), {
                'entries': 0, 'compressed_bytes': 0, 'uncompressed_bytes': 0,
            })
            row['entries'] += 1
            row['compressed_bytes'] += entry.compress_size
            row['uncompressed_bytes'] += entry.file_size
        compressed = sum(entry.compress_size for entry in entries)
        largest = sorted(entries, key=lambda entry: (-entry.compress_size, entry.filename))[:top]
        return {
            'schema': 1,
            'apk': path.name,
            'sha256': digest.hexdigest(),
            'apk_bytes': path.stat().st_size,
            'zip_entry_bytes': compressed,
            # Includes central directory, alignment, signing block and ZIP headers.
            'container_overhead_bytes': path.stat().st_size - compressed,
            'categories': dict(sorted(categories.items())),
            'largest_entries': [{
                'path': entry.filename,
                'compressed_bytes': entry.compress_size,
                'uncompressed_bytes': entry.file_size,
                'compression_method': entry.compress_type,
            } for entry in largest],
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--output', type=Path, help='Write JSON to this path; default stdout')
    parser.add_argument('--top', type=int, default=30)
    args = parser.parse_args()
    if args.top < 0:
        parser.error('--top must be non-negative')
    payload = json.dumps(report(args.apk, args.top), indent=2, ensure_ascii=False) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding='utf-8')
    else:
        print(payload, end='')


if __name__ == '__main__':
    main()
