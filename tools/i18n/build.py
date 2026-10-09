import sys,re,json,os
loc=sys.argv[1]
OUT=sys.argv[2] if len(sys.argv)>2 else 'strings_telos_translations.xml'  # pass another file name to add a batch without replacing the earlier one
src=json.load(open(f'missing/{loc}.json'))
import glob
tr={}
for f in sorted(glob.glob(f'done/{loc}.part*.json')): tr.update(json.load(open(f)))
plr=None
try: plr=json.load(open(f'done/{loc}.plurals.json'))
except Exception: pass
ph=lambda s:sorted(re.findall(r'%(?:\d+\$)?[sdfx%]|<[^>]+>',s))
def esc(s):
    s=s.replace('&','&amp;') if not re.search(r'<[a-z]',s) else s
    if not re.search(r'<[a-z]',s): s=s.replace('<','&lt;').replace('>','&gt;')
    s=s.replace("'","\\'").replace('"','\\"').replace('\n','\\n')
    if s.startswith('@') or s.startswith('?'): s='\\'+s
    return s
out=[];bad=[]
for k,v in tr.items():
    if k not in src: continue
    if ph(src[k])!=ph(v): bad.append(k); continue
    out.append(f'    <string name="{k}">{esc(v)}</string>')
pl=[]
if plr:
    for k,q in plr.items():
        pl.append(f'    <plurals name="{k}">')
        for qq,t in q.items(): pl.append(f'        <item quantity="{qq}">{esc(t)}</item>')
        pl.append('    </plurals>')
d=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','core','i18n','src','main','res','values-'+loc)
import os; os.makedirs(d, exist_ok=True)
open(f'{d}/{OUT}','w').write("<?xml version='1.0' encoding='utf-8'?>\n<resources>\n"+'\n'.join(out+pl)+"\n</resources>\n")
print(loc,'written',len(out),'skipped(placeholder mismatch)',bad[:10],len(bad),'missing',len(src)-len(out)-len(bad))
