#!/usr/bin/env python3
"""Verify Android's default-SMS role component contract before packaging."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
A = '{http://schemas.android.com/apk/res/android}'
apps = [ET.parse(ROOT / p).getroot().find('application') for p in (
    'app/ui/src/main/AndroidManifest.xml', 'services/comms/src/main/AndroidManifest.xml')]

def component(tag, action, permission=None, schemes=(), mime=None):
    for app in apps:
        for node in app.findall(tag):
            if node.get(A + 'exported') != 'true' or (permission and node.get(A + 'permission') != permission):
                continue
            for f in node.findall('intent-filter'):
                if action not in {x.get(A+'name') for x in f.findall('action')}:
                    continue
                data=f.findall('data')
                if not set(schemes).issubset({x.get(A+'scheme') for x in data}):
                    continue
                if mime and mime not in {x.get(A+'mimeType') for x in data}:
                    continue
                return
    raise AssertionError(f'Missing SMS role component: {action}')

component('activity', 'android.intent.action.SENDTO', schemes=('sms','smsto','mms','mmsto'))
component('receiver', 'android.provider.Telephony.SMS_DELIVER', 'android.permission.BROADCAST_SMS')
component('receiver', 'android.provider.Telephony.WAP_PUSH_DELIVER', 'android.permission.BROADCAST_WAP_PUSH', mime='application/vnd.wap.mms-message')
component('service', 'android.intent.action.RESPOND_VIA_MESSAGE', 'android.permission.SEND_RESPOND_VIA_MESSAGE', schemes=('sms','smsto','mms','mmsto'))
print('Default-SMS role manifest contract verified.')
