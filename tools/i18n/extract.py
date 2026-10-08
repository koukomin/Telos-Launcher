import sys,re,glob,json,os
import xml.etree.ElementTree as ET
import os
RES=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','core','i18n','src','main','res')
def load(d):
    out={}; plurals={}
    for f in sorted(glob.glob(f'{RES}/{d}/*.xml')):
        try: root=ET.parse(f).getroot()
        except Exception: continue
        for e in root:
            if e.tag=='string' and e.get('translatable')!='false':
                inner=(e.text or '')+''.join(ET.tostring(c,encoding='unicode') for c in e)
                out[e.get('name')]=inner
            elif e.tag=='plurals' and e.get('translatable')!='false':
                plurals[e.get('name')]={i.get('quantity'):(i.text or '') for i in e}
    return out,plurals
def unesc(s): return s.replace("\\'","'").replace('\\"','"').replace('\\n','\n').replace('\\@','@').replace('\\?','?')
loc=sys.argv[1]
base,bp=load('values')
have,hp=load('values-'+loc)
miss={k:unesc(v) for k,v in base.items() if k not in have and re.search(r'[A-Za-z]',v)}
json.dump(miss,open(f'missing/{loc}.json','w'),ensure_ascii=False,indent=0)
pm={k:v for k,v in bp.items() if k not in hp}
json.dump(pm,open(f'missing/{loc}.plurals.json','w'),ensure_ascii=False,indent=0)
print(loc,len(miss),len(pm))
