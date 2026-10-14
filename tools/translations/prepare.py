#!/usr/bin/env python3
"""Inventory new/changed Android strings; inference is deliberately a separate step."""
import argparse, json, pathlib, re, subprocess, xml.etree.ElementTree as ET
BASE = '71262e1e52c1bbb34da722d0ff5e213da665916f'
RES = pathlib.Path('core/i18n/src/main/res')

def records(paths, reader):
    result = {}
    for path in sorted(paths):
        for item in ET.fromstring(reader(path)):
            if item.tag == 'string' and item.get('translatable') != 'false':
                result[item.get('name')] = {'text': ''.join(item.itertext()), 'xml': ET.tostring(item, encoding='unicode')}
    return result

def inventory(base):
    current = records(RES.glob('values/*.xml'), lambda p: p.read_text())
    paths = subprocess.check_output(['git','ls-tree','-r','--name-only',base,str(RES/'values')], text=True).splitlines()
    previous = records([p for p in paths if p.endswith('.xml')], lambda p: subprocess.check_output(['git','show',base+':'+str(p)],text=True))
    delta = subprocess.check_output(['git','diff',base,'--','app/ui/src/main/java'],text=True)
    surfaced = set(re.findall(r'R\.string\.(\w+)', '\n'.join(line for line in delta.splitlines() if line.startswith('+') and not line.startswith('+++'))))
    keys = {k for k,v in current.items() if k not in previous or v['text'] != previous[k]['text']} | surfaced
    keys &= current.keys()
    locales = {}
    for directory in sorted(RES.iterdir()):
        locale = directory.name.removeprefix('values-')
        if not re.fullmatch(r'[a-z]{2,3}(?:-r[A-Z]{2})?',locale) or locale.startswith('en-'): continue
        localized = records(directory.glob('*.xml'),lambda p:p.read_text())
        old_paths = subprocess.check_output(['git','ls-tree','-r','--name-only',base,str(directory)], text=True).splitlines()
        old_localized = records([p for p in old_paths if p.endswith('.xml')], lambda p: subprocess.check_output(['git','show',base+':'+str(p)],text=True))
        stale = {k for k in keys & localized.keys() & old_localized.keys() if k in previous and current[k]['text'] != previous[k]['text'] and localized[k]['text'] == old_localized[k]['text']}
        missing = sorted((keys - localized.keys()) | stale)
        locales[locale] = {k:current[k] for k in missing}
    return {'base':base,'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'strings':{k:current[k] for k in sorted(keys)},'locales':locales}

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default=BASE);p.add_argument('--output',default='translation-artifact/inventory.json');a=p.parse_args()
    data=inventory(a.base);out=pathlib.Path(a.output);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'strings':len(data['strings']),'missing':{l:len(v) for l,v in data['locales'].items()}},ensure_ascii=False))
