"""For each exit-looking loc in a dungeon, look for a loc that could be its other end 6400 tiles south
(the usual underground offset). Usage: python tools/dungeons/exits.py <region> [...]"""
import sys, re, os
sys.path.insert(0, os.path.dirname(__file__))
import census as C
from regions import REGIONS

EXIT = re.compile(r"ladder|stair|rope|vine|exit|tunnel|crevice|opening|hole|cave|manhole|trapdoor|entrance", re.I)
GO = re.compile(r"climb|use|leave|exit|walk|enter|squeeze|crawl", re.I)
NEAR = re.compile(r"ladder|stair|rope|vine|exit|tunnel|crevice|opening|hole|cave|manhole|trapdoor|entrance|rocks|cavern|ruin|steps|door|gate", re.I)


def main(names):
    L = C.locs()
    for name in names:
        print("=====", name)
        for (lid, x, z, lvl, sh, rot) in sorted(C.census(*REGIONS[name]), key=lambda r: (r[1], r[2])):
            t = L.get(lid)
            if not t: continue
            sym = C.LOC_SYM.get(lid, "?")
            ops = [o for o in t["ops"] if o and o.lower() != "examine"]
            if not ops: continue
            if not (EXIT.search(sym) or EXIT.search(t["name"])): continue
            if not any(GO.search(o) for o in ops): continue
            near = []
            for (l2, x2, z2, lv2, s2, r2) in C.census(x - 4, z - 6400 - 4, x + 4, z - 6400 + 4):
                t2 = L.get(l2)
                if t2 and any(o and GO.search(o) for o in t2["ops"]):
                    near.append((C.LOC_SYM.get(l2, "?"), x2, z2, lv2))
            print(f"  {sym:<38} {'/'.join(ops)[:22]:<22} ({x},{z},{lvl}) rot{rot}  surface: {near[:3]}")


if __name__ == "__main__":
    main(sys.argv[1:])
