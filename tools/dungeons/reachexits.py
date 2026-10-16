"""Exit-looking locs touching the area a dungeon's arrival tile can reach (doors and gates count as open)."""
import sys, os, re
sys.path.insert(0, os.path.dirname(__file__))
import collision as K
import census as C

ARRIVALS = {  # key: (level, x, z)
    "taverley": (0, 2884, 9798), "edgeville": (0, 3097, 9868), "varrock": (0, 3237, 9859),
    "revenant": (0, 3217, 10058), "stronghold": (0, 2429, 9824), "brimhaven": (0, 2713, 9564),
    "karamja": (0, 2856, 9568), "kraken": (0, 2276, 9988), "smoke": (0, 2404, 9415),
    "catacombs": (0, 1665, 10051), "forthos": (0, 1832, 9974), "ham": (0, 3150, 9653),
    "slayer_basement": (3, 3412, 9932), "crash_site": (0, 2128, 5647),
    "mor_ul_rek": (0, 2479, 5174),
    "fremennik": (0, 2808, 10002), "kalphite": (2, 3484, 9510), "mos_le_harmless": (0, 3747, 9373),
    "isle_dungeon": (0, 2169, 9307),
    "lizardman": (0, 1305, 9973), "karuulm": (0, 1311, 10189), "godwars": (2, 2882, 5310),
}
EXIT = re.compile(r"ladder|stair|rope|vine|exit|tunnel|crevice|opening|hole|cave|manhole|trapdoor|entrance|climb", re.I)
GO = re.compile(r"climb|use|leave|exit|walk|enter|squeeze|crawl|jump", re.I)


def run(key):
    lvl, x, z = ARRIVALS[key]
    K.SOFT = True
    tiles = K.flood(lvl, x, z, 600000)
    xs = [a for a, _ in tiles]; zs = [b for _, b in tiles]
    print(f"===== {key}: reach {len(tiles)} x {min(xs)}-{max(xs)} z {min(zs)}-{max(zs)}")
    L = C.locs()
    seen = set()
    for (tx, tz) in sorted(tiles):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for (lid, shape, rot) in K._locs_at.get((lvl, tx + dx, tz + dz), ()):
                    k = (lid, tx + dx, tz + dz)
                    if k in seen: continue
                    seen.add(k)
                    t = L[lid]; sym = C.LOC_SYM.get(lid, "?")
                    ops = [o for o in t["ops"] if o and o.lower() != "examine"]
                    if not ops or not any(GO.search(o) for o in ops): continue
                    if not (EXIT.search(sym) or EXIT.search(t["name"])): continue
                    near = [(C.LOC_SYM.get(l2, "?"), x2, z2, v2) for (l2, x2, z2, v2, s2, r2) in
                            C.census(tx + dx - 3, tz + dz - 6403, tx + dx + 3, tz + dz - 6397)
                            if L.get(l2) and any(o and GO.search(o) for o in L[l2]["ops"])] if tz + dz > 8000 else []
                    print(f"  {sym:<36} {'/'.join(ops)[:20]:<20} ({tx+dx},{tz+dz}) rot{rot} surf:{near[:2]}")


if __name__ == "__main__":
    for k in sys.argv[1:]:
        run(k)
