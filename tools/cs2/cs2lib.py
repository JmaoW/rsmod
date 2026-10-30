"""Reads and rebuilds the cache's compiled clientscripts (revision 233).

`raw(id)` is a script's bytes, `parse` turns them into a name, a list of (opcode, value) instructions
and the rest of the footer, and `build` writes that back (`build(parse(b)) == b`). Jump values are
relative: a jump at index i lands on i + 1 + value.
"""
import os, struct, sys
HERE = os.path.dirname(os.path.abspath(__file__))
os.environ.setdefault("JS5_CACHE", os.path.join(HERE, "..", "..", ".data", "cache", "vanilla"))
sys.path.insert(0, HERE)
import js5
IDX12=js5.idx(12)
def raw(sid): return js5.decompress(js5.read_entry(IDX12,12,sid))
def parse(b):
    sw_len=struct.unpack(">H",b[-2:])[0]
    sw_start=len(b)-2-sw_len
    f=sw_start-12
    n,il,sl,ia,sa=struct.unpack(">iHHHH",b[f:f+12])
    p=0
    while b[p]!=0: p+=1
    name=b[:p].decode('latin1'); p+=1
    ins=[]
    while p<f:
        op=struct.unpack(">H",b[p:p+2])[0]; p+=2
        if op==3:
            q=p
            while b[q]!=0: q+=1
            val=b[p:q].decode('latin1'); p=q+1
        elif op<100 and op not in (21,38,39):
            val=struct.unpack(">i",b[p:p+4])[0]; p+=4
        else:
            val=b[p]; p+=1
        ins.append((op,val))
    assert p==f,(p,f)
    assert len(ins)==n,(len(ins),n)
    return dict(name=name,ins=ins,il=il,sl=sl,ia=ia,sa=sa,sw=b[sw_start:len(b)-2])
def build(s):
    out=bytearray(s['name'].encode('latin1')+b'\0')
    for op,val in s['ins']:
        out+=struct.pack(">H",op)
        if op==3: out+=val.encode('latin1')+b'\0'
        elif op<100 and op not in (21,38,39): out+=struct.pack(">i",val)
        else: out+=bytes([val])
    out+=struct.pack(">iHHHH",len(s['ins']),s['il'],s['sl'],s['ia'],s['sa'])
    out+=s['sw']+struct.pack(">H",len(s['sw']))
    return bytes(out)
if __name__=="__main__":
    import sys
    for a in sys.argv[1:]:
        b=raw(int(a)); s=parse(b); assert build(s)==b
        print(a,'ok roundtrip',s['ia'],s['sa'],s['il'],s['sl'],s['sw'].hex())
        for i,x in enumerate(s['ins']): print(i,x)
