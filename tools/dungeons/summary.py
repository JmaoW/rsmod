import sys, collections
sys.path.insert(0, __import__("os").path.dirname(__file__))
import census as C
from regions import REGIONS

def run(name, quiet_names=()):
    L = C.locs()
    byname = collections.defaultdict(list)
    for (lid, x, z, lvl, shape, rot) in C.census(*REGIONS[name]):
        t = L.get(lid)
        if not t: continue
        ops = [o for o in t["ops"] if o and o.lower() != "examine"]
        if not ops and not t["multi"]: continue
        byname[(lid, C.LOC_SYM.get(lid, "?"), t["name"], "/".join(ops))].append((x, z, lvl))
    return byname

if __name__ == "__main__":
    for name in sys.argv[1:]:
        print("=====", name, REGIONS[name])
        for k, v in sorted(run(name).items(), key=lambda kv: (kv[0][2], kv[0][1])):
            coords = v[:4] if len(v) <= 4 else v[:2]
            print(f"  {k[0]:>6} {k[1][:34]:<34} '{k[2][:26]}' [{k[3][:40]}] x{len(v)} {coords}")
