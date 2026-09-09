#!/usr/bin/env python3
import argparse
import sqlite3,re,json
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument("--roots", nargs=3, required=True, type=Path)
parser.add_argument("--output", required=True, type=Path)
args=parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
roots={str(i):root.resolve() for i,root in enumerate(args.roots,1)}

def excluded(p):
    s=p.lower()
    return p.startswith('ddd4j-samples/') or any(x in s for x in ('quarkus','panache'))

def norm_dep(s):
    s=s.replace('com.fasterxml.jackson','JACKSON').replace('tools.jackson','JACKSON')
    for x in ('javax.','jakarta.'): s=s.replace(x,'DEPENDENCY.')
    return s

def lexical(text):
    out=[];i=0;n=len(text)
    while i<n:
        c=text[i]
        if c.isspace(): i+=1; continue
        if text.startswith('//',i):
            j=text.find('\n',i+2); i=n if j<0 else j+1; continue
        if text.startswith('/*',i):
            j=text.find('*/',i+2); i=n if j<0 else j+2; continue
        if text.startswith('"""',i):
            j=text.find('"""',i+3); j=n-3 if j<0 else j
            out.append(text[i:j+3]); i=j+3; continue
        if c in ('"',"'"):
            q=c;j=i+1
            while j<n:
                if text[j]=='\\': j+=2; continue
                if text[j]==q: j+=1; break
                j+=1
            out.append(text[i:j]); i=j; continue
        m=re.match(r'[A-Za-z_$][A-Za-z0-9_$.]*|\d+(?:\.\d+)?(?:[A-Za-z]+)?|.',text[i:],re.S)
        out.append(m.group(0)); i+=len(m.group(0))
    return norm_dep(' '.join(out))

def snapshot(root):
    raw=__import__('subprocess').check_output(['git','-C',str(root),'ls-files','-z'])
    tracked={item.decode() for item in raw.split(b'\0') if item}
    c=sqlite3.connect(root/'.codegraph/codegraph.db')
    rows=c.execute("""select file_path,qualified_name,name,signature,start_line,end_line,visibility
                      from nodes where kind='method' and language='java'""")
    data={}
    for p,q,n,s,a,b,v in rows:
        if p not in tracked or excluded(p) or '/src/main/java/' not in p: continue
        lines=(root/p).read_text(encoding='utf-8').splitlines()
        body='\n'.join(lines[a-1:b])
        sig=norm_dep(re.sub(r'\s+',' ',s or '').strip())
        key=f'{p}|{q}|{n}|{sig}'
        data.setdefault(key,[]).append({'start':a,'end':b,'visibility':v,'normalized':lexical(body),'source':body})
    c.close()
    return data

sn={k:snapshot(v) for k,v in roots.items()}
report={'snapshots':{k:{'methods':sum(len(v) for v in d.values()),'keys':len(d)} for k,d in sn.items()},'pairs':{}}
for a,b in (('1','2'),('2','3'),('1','3')):
    ka,kb=set(sn[a]),set(sn[b]); common=ka&kb
    mismatches=[]; ambiguous=[]
    for k in sorted(common):
        la,lb=sn[a][k],sn[b][k]
        if len(la)!=1 or len(lb)!=1:
            ambiguous.append({'key':k,'left_count':len(la),'right_count':len(lb)})
            continue
        if la[0]['normalized']!=lb[0]['normalized']:
            mismatches.append({'key':k,'left':{x:la[0][x] for x in ('start','end','source')},
                                      'right':{x:lb[0][x] for x in ('start','end','source')}})
    report['pairs'][f'{a}-{b}']={
      'common_keys':len(common),'only_left':len(ka-kb),'only_right':len(kb-ka),
      'ambiguous':ambiguous,'body_mismatches':mismatches}
    print(f'{a}-{b}: common={len(common)} only={len(ka-kb)}/{len(kb-ka)} ambiguous={len(ambiguous)} body_mismatches={len(mismatches)}')
out=args.output/'method-body-report.json'
out.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(out)
