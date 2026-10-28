#!/usr/bin/env python3
"""Check changed UI-string coverage and Android resource syntax."""
import re
import os
import xml.etree.ElementTree as ET
from prepare import RES, RESOURCE_MODULES, inventory, records, BASE

TOKEN = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]|\{(?:name|ext|n(?::\d+)?)\}')

def main():
    data = inventory(os.environ.get('TELOS_TRANSLATION_BASE', BASE))
    missing = {locale: list(rows) for locale, rows in data['locales'].items() if rows}
    if missing:
        raise SystemExit('Missing or stale translations: ' + repr({locale: {'count': len(keys), 'examples': keys[:8]} for locale,keys in missing.items()}))
    sources = {}
    for root in RESOURCE_MODULES.values(): sources.update(records((root/'values').glob('*.xml'),lambda p:p.read_text()))
    checked = 0
    for directory in [p for root in RESOURCE_MODULES.values() for p in root.glob('values-*')]:
        names = set()
        for path in directory.glob('*.xml'):
            for node in ET.parse(path).getroot():
                if node.tag != 'string':
                    continue
                name = node.get('name')
                if name in names:
                    raise SystemExit(f'Duplicate string: {directory.name}/{name}')
                names.add(name)
                if path.name not in {'strings_telos_remaining.xml', 'strings_installer_dock_wallpaper.xml', 'strings_virtual_app_names.xml'}:
                    continue
                text = ''.join(node.itertext())
                source = sources[name]['text']
                if sorted(TOKEN.findall(text)) != sorted(TOKEN.findall(source)):
                    raise SystemExit(f'Changed placeholders: {directory.name}/{name}')
                for match in re.finditer("['\"]", text):
                    prefix = text[:match.start()]
                    slashes = len(prefix) - len(prefix.rstrip('\\'))
                    if slashes % 2 != 1:
                        raise SystemExit(f'Unescaped Android quote: {directory.name}/{name}')
                checked += 1
    print(f'{checked} translations validated; complete changed-string coverage in {len(data["locales"])} base locales.')

if __name__ == '__main__':
    main()
