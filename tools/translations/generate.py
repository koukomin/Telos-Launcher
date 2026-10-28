#!/usr/bin/env python3
"""Manual CI-only translation. Never writes directly into the repository resources."""
import hashlib, json, os, pathlib, re, xml.etree.ElementTree as ET
MODEL='google/madlad400-3b-mt'
REVISION='fa184c675da0b5c9e1c8694fccd4e12e2d422094'
OUTPUT=pathlib.Path('translation-artifact')
TOKEN=re.compile(r'@[a-zA-Z][\w:.]*/[A-Za-z0-9_]+|<[^>]+>|AppOpsNext|Shizuku|WireGuard|SOCKS5|HTTP CONNECT|UID|DNSCrypt|IPv4|IPv6|ODoH|DoH|TCP|UDP|DNS|RPN|%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]|\{(?:name|ext|n(?::\d+)?)\}|\\[ntr\'"\\]|YouTube Music|YouTube|Stremio Fork|Stremio|Telos|Trakt(?:\.tv)?|Obtainium|IMDb|APK|KiB|YYYY-MM-DD|https?://[^\s<>]+')
LANG={'in':'id','iw':'he','nb-rNO':'no','pt-rBR':'pt','zh-rCN':'zh','zh-rTW':'zh'}

def protect(text):
    values=[]
    def substitute(match):
        values.append(match.group());return f'ZXQ{len(values)-1}ZXQ'
    return TOKEN.sub(substitute,text),values

def restore(text, values):
    for index,value in enumerate(values):
        pattern=re.compile(r'Z\s*X\s*Q\s*'+str(index)+r'\s*Z\s*X\s*Q',re.I)
        if len(pattern.findall(text))!=1: raise ValueError('Lost or duplicated protected token')
        text=pattern.sub(lambda _:value,text)
    if re.search(r'ZXQ\d+ZXQ',text,re.I):raise ValueError('Unexpected protected token')
    return text

def android(text):
    # Translate human text, then re-escape Android's quote syntax once.
    return text.replace('\\','\\\\').replace("'","\\'").replace('"','\\"').replace('\n','\\n')

def human(text):
    return re.sub(r'\\([ntr\'"\\])',lambda m:{'n':'\n','t':'\t','r':'\r'}.get(m[1],m[1]),text)

def main():
    import torch
    from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
    from opencc import OpenCC
    assert pathlib.Path('/home/runner').exists(), 'Heavy inference is restricted to a GitHub runner'
    torch.set_num_threads(2);torch.backends.mkldnn.enabled=False
    inventory=json.loads((OUTPUT/'inventory.json').read_text())
    tokenizer=AutoTokenizer.from_pretrained(MODEL,revision=REVISION,trust_remote_code=False)
    model=AutoModelForSeq2SeqLM.from_pretrained(MODEL,revision=REVISION,torch_dtype=torch.bfloat16,low_cpu_mem_usage=True,trust_remote_code=False,use_safetensors=True).eval()
    cache_path=OUTPUT/'cache.json';cache=json.loads(cache_path.read_text()) if cache_path.exists() else {}
    failures=[];converter=OpenCC('s2t')
    def translate(text,lang):
        encoded=tokenizer('<2'+lang+'> '+text,return_tensors='pt',truncation=False)
        if encoded.input_ids.shape[1]>512:raise ValueError('String exceeds safe inference context')
        with torch.inference_mode(): result=model.generate(**encoded,max_new_tokens=512,num_beams=1)
        return tokenizer.decode(result[0],skip_special_tokens=True).strip()
    partition=int(os.environ.get('TRANSLATION_PARTITION','0'));partitions=int(os.environ.get('TRANSLATION_PARTITIONS','1'))
    assert 0 <= partition < partitions
    for number,(locale,records) in enumerate(inventory['locales'].items()):
        if number % partitions != partition:continue
        roots={};translated=0
        for name,record in records.items():
            source=human(record['text']);key=hashlib.sha256((locale+'\0'+name+'\0'+source).encode()).hexdigest()
            try:
                node=ET.fromstring(record['xml'])
                if len(node):raise ValueError('Styled resource needs manual translation preserving markup')
                if 'reuse_text' in record:
                    node.text=record['reuse_text']
                    roots.setdefault(record.get('module','core'),ET.Element('resources')).append(node);translated+=1
                    continue
                if key not in cache:
                    guarded,values=protect(source)
                    if TOKEN.fullmatch(source) or not any(c.isalpha() for c in TOKEN.sub('',source)): text=source
                    else:
                        try:text=restore(translate(guarded,LANG.get(locale,locale)),values)
                        except ValueError:
                            # Literal chunks preserve immutable tokens when model changes markers.
                            chunks=TOKEN.split(source);matches=TOKEN.findall(source);parts=[]
                            for i,chunk in enumerate(chunks):
                                if chunk.strip():
                                    prefix=chunk[:len(chunk)-len(chunk.lstrip())];suffix=chunk[len(chunk.rstrip()):]
                                    parts.append(prefix+translate(chunk.strip(),LANG.get(locale,locale))+suffix)
                                else:parts.append(chunk)
                                if i<len(matches):parts.append(matches[i])
                            text=''.join(parts)
                    if locale=='zh-rTW':text=converter.convert(text)
                    if not text or '\0' in text:raise ValueError('Empty/invalid translation')
                    if sorted(TOKEN.findall(source))!=sorted(TOKEN.findall(text)):raise ValueError('Changed format or brand tokens')
                    cache[key]=text
                    tmp=cache_path.with_suffix('.tmp');tmp.write_text(json.dumps(cache,ensure_ascii=False,indent=2));tmp.replace(cache_path)
                node.text=android(cache[key]);roots.setdefault(record.get('module','core'),ET.Element('resources')).append(node);translated+=1
            except Exception as error:
                failures.append({'locale':locale,'key':name,'error':str(error)})
        for module,root in roots.items():
            ET.indent(root)
            folder=('values-b+zh+Hans' if locale=='zh-rCN' else 'values-b+zh+Hant') if module=='appops' and locale in {'zh-rCN','zh-rTW'} else 'values-'+locale
            path=OUTPUT/('appops-res' if module=='appops' else 'res')/folder/'strings_telos_remaining.xml';path.parent.mkdir(parents=True,exist_ok=True)
            ET.ElementTree(root).write(path,encoding='utf-8',xml_declaration=True)
        print(locale,translated,'/',len(records),flush=True)
        (OUTPUT/'failures.json').write_text(json.dumps(failures,ensure_ascii=False,indent=2))
    (OUTPUT/'provenance.json').write_text(json.dumps({'model':MODEL,'revision':REVISION,'license':'Apache-2.0','source_commit':inventory['source_commit'],'base':inventory['base'],'partition':partition,'partitions':partitions,'review':'Machine-generated. Human semantic review and Android resource validation required before commit.'},indent=2))
    if failures:raise SystemExit(f'{len(failures)} strings require manual translation; no English fallbacks generated.')

if __name__=='__main__':main()
