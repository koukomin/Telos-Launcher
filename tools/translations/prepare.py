#!/usr/bin/env python3
"""Inventory changed host and embedded AppOps resources without running inference."""
import argparse, json, pathlib, re, subprocess, xml.etree.ElementTree as ET
BASE = 'af228c22344bd1bd214db65d8bb16c502d654113'
RES = pathlib.Path('core/i18n/src/main/res')
APP_OPS_RES = pathlib.Path('libs/appops-next/src/main/res')
RESOURCE_MODULES = {'core': RES, 'appops': APP_OPS_RES}

def records(paths, reader, module=None):
    result = {}
    for path in sorted(paths):
        for item in ET.fromstring(reader(path)):
            if item.tag == 'string' and item.get('translatable') != 'false':
                row = {'text': ''.join(item.itertext()), 'xml': ET.tostring(item, encoding='unicode')}
                if module: row['module'] = module
                result[item.get('name')] = row
    return result

def previous(directory, base):
    paths = subprocess.check_output(['git','ls-tree','-r','--name-only',base,str(directory)], text=True).splitlines()
    return records([p for p in paths if p.endswith('.xml')], lambda p: subprocess.check_output(['git','show',base+':'+str(p)],text=True))

def locale_directory(root, locale):
    if root == APP_OPS_RES and locale in {'zh-rCN','zh-rTW'}:
        return root / ('values-b+zh+Hans' if locale == 'zh-rCN' else 'values-b+zh+Hant')
    return root / ('values-' + locale)

def inventory(base):
    current, old, core_sources = {}, {}, {}
    for module, root in RESOURCE_MODULES.items():
        rows = records((root/'values').glob('*.xml'),lambda p:p.read_text(),module)
        if module == 'core': core_sources = rows
        if current.keys() & rows.keys(): raise ValueError('Resource names must be unique between modules')
        current.update(rows); old.update(previous(root/'values',base))
    delta = subprocess.check_output(['git','diff',base,'--','app/ui/src/main/java','libs/appops-next/src/main/java','core/base/src/main/java/de/mm20/launcher2/search/TelosAppLabels.kt'],text=True)
    surfaced = set(re.findall(r'R\.string\.(\w+)', '\n'.join(line for line in delta.splitlines() if line.startswith('+') and not line.startswith('+++'))))
    keys = {k for k,v in current.items() if k not in old or v['text'] != old[k]['text']} | surfaced
    keys &= current.keys()
    english_matches = {}
    for name,row in core_sources.items(): english_matches.setdefault(row['text'],[]).append(name)
    directories = sorted([p for p in RES.iterdir() if re.fullmatch(r'values-[a-z]{2,3}(?:-r[A-Z]{2})?',p.name) and not p.name.startswith('values-en-')],key=lambda p:(p.name != 'values-el',p.name))
    locales = {}
    for directory in directories:
        locale = directory.name.removeprefix('values-')
        localized, old_localized = {}, {}
        core_localized = records(directory.glob('*.xml'),lambda p:p.read_text())
        for module,root in RESOURCE_MODULES.items():
            path = locale_directory(root,locale)
            localized.update(records(path.glob('*.xml'),lambda p:p.read_text()))
            old_localized.update(previous(path,base))
        stale = {k for k in keys & localized.keys() & old_localized.keys() if k in old and current[k]['text'] != old[k]['text'] and localized[k]['text'] == old_localized[k]['text']}
        missing = sorted((keys - localized.keys()) | stale)
        rows = {}
        for key in missing:
            row = dict(current[key])
            if row.get('module') == 'appops':
                for match in english_matches.get(row['text'],[]):
                    existing = core_localized.get(match)
                    if existing:
                        node = ET.fromstring(existing['xml'])
                        if not len(node) and node.text and not node.text.startswith('@'):
                            row['reuse_text'] = node.text
                            break
            rows[key] = row
        locales[locale] = rows
    return {'base':base,'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'strings':{k:current[k] for k in sorted(keys)},'locales':locales}

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default=BASE);p.add_argument('--output',default='translation-artifact/inventory.json');a=p.parse_args()
    data=inventory(a.base);out=pathlib.Path(a.output);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'strings':len(data['strings']),'missing':{l:len(v) for l,v in data['locales'].items()},'reused':sum('reuse_text' in r for rows in data['locales'].values() for r in rows.values())},ensure_ascii=False))
