#!/usr/bin/env python3
"""Check the static Recents fallback for every registered virtual-app task."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
android = '{http://schemas.android.com/apk/res/android}'
contract = (root / 'core/base/src/main/java/de/mm20/launcher2/applock/SettingsDeepLinkContract.kt').read_text()
body = contract.split('fun virtualAppTaskId(', 1)[1].split('/** Alias metadata', 1)[0]
identities = set(re.findall(r'"([a-z_]+)"', body)) - {'settings'}
# Route literals contain a slash, and the tab value "messages" is also its task identity.
manifest = ET.parse(root / 'app/ui/src/main/AndroidManifest.xml').getroot()
application = manifest.find('application')
assert application is not None
aliases = {item.get(android + 'name').removeprefix('.settings.TelosApp_'): item
           for item in application.findall('activity-alias')
           if item.get(android + 'name', '').startswith('.settings.TelosApp_')}
assert identities == aliases.keys(), (identities - aliases.keys(), aliases.keys() - identities)
for identity, alias in aliases.items():
    assert alias.get(android + 'targetActivity') == '.settings.TelosAppActivity', identity
    assert alias.get(android + 'exported') == 'false', identity
    assert alias.get(android + 'label', '').startswith('@string/'), identity
    assert alias.get(android + 'label') not in {'@string/settings', '@string/app_name'}, identity
    icon = alias.get(android + 'icon', '').removeprefix('@drawable/')
    assert icon and any(root.glob(f'*/**/src/main/res/drawable/{icon}.xml')), identity
print(f'Static Recents metadata verified for {len(aliases)} virtual-app task identities.')
