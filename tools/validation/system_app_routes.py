#!/usr/bin/env python3
"""Catch missing destination handlers: Kotlin compilation cannot detect Nav3 registry gaps."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'app/ui/src/main/java/de/mm20/launcher2/ui'
DESTINATIONS = (
    'SystemDashboardRoute', 'SystemAppDetailsRoute', 'NetworkPackageFirewallRoute',
    'FreezeSettingsRoute', 'FreezeDashboardRoute', 'DeviceOwnerSetupRoute',
)
for host in ('settings/SettingsActivity.kt', 'launcher/scaffold/components/TelosAppPageComponent.kt'):
    source = (UI / host).read_text()
    registered = set(re.findall(r'entry<([\w.]+)>', source))
    names = {entry.rsplit('.', 1)[-1] for entry in registered}
    missing = set(DESTINATIONS) - names
    assert not missing, f'{host}: missing System management destinations: {sorted(missing)}'
print('System app management destinations registered in standalone and embedded hosts.')
