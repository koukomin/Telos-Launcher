#!/usr/bin/env python3
"""Only an explicit last-commit activation can start expensive PR inference."""
import json, os, pathlib, subprocess
ACTIVATION='tools/translations/activation.json'
allowed=os.environ.get('EVENT_NAME')=='workflow_dispatch' and os.environ.get('MANUAL_GENERATE')=='true'
if os.environ.get('EVENT_NAME')=='pull_request':
    changed=subprocess.check_output(['git','diff','--name-only','HEAD^','HEAD','--',ACTIVATION],text=True).splitlines()
    path=pathlib.Path(ACTIVATION)
    if ACTIVATION in changed and path.exists():
        config=json.loads(path.read_text())
        allowed=config.get('generate') is True and config.get('functional_changes_complete') is True
output=os.environ.get('GITHUB_OUTPUT')
if output:
    with open(output,'a') as stream:stream.write('generate='+str(allowed).lower()+'\n')
print('Translation generation '+('explicitly activated' if allowed else 'disabled; inventory only'))
