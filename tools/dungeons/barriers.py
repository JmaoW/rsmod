"""Passage-type objects (doors, gates, pipes, crevices...) inside the area a dungeon's arrival tile can
reach once doors are open. Each is something a player has to use to get on. Usage: barriers.py [key..]"""
import sys, os, re, collections
sys.path.insert(0, os.path.dirname(__file__))
import collision as K
import census as C
from reachexits import ARRIVALS

PASSAGE = re.compile(r"open|pick-lock|squeeze|crawl|jump|climb|cross|walk-across|swing|enter|push|pass|slash|chop|use|leave|ride|walk-down|walk-up|go-through|walk through|search|peek|clamber|leap|step|balance|tunnel|investigate|unlock|exit", re.I)
SKIP = re.compile(r"rock|tree|crate|box|sack|chest|drawers|cupboard|shelf|bookcase|barrel|table|fire|range|furnace|anvil|bank|altar|coffin|sarcoph|urn|vase|bed|stool|stump|plant|web$", re.I)
HANDLED = {"poordoor", "poordooropen", "poshdoor", "poshdooropen", "castledoubledoorl", "castledoubledoorr", "fencegate_l", "fencegate_r", "rustic_fencegate_l", "rustic_fencegate_r", "ladder_from_cellar"}


def run(key):
    lvl, x, z = ARRIVALS[key]
    K.SOFT = True
    tiles = K.flood(lvl, x, z, 800000)
    K.SOFT = False
    L = C.locs()
    found = collections.defaultdict(list)
    seen = set()
    for (tx, tz) in tiles:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for (lid, shape, rot) in K._locs_at.get((lvl, tx + dx, tz + dz), ()):
                    k = (lid, tx + dx, tz + dz)
                    if k in seen: continue
                    seen.add(k)
                    t = L[lid]; sym = C.LOC_SYM.get(lid, "?")
                    if sym in HANDLED or not t["solid"] and shape not in (0, 1, 2, 3, 9): continue
                    ops = [o for o in t["ops"] if o and o.lower() != "examine"]
                    if not ops or not any(PASSAGE.search(o) for o in ops): continue
                    if SKIP.search(sym) or SKIP.search(t["name"]): continue
                    found[(sym, "/".join(ops)[:30], t["name"])].append((tx + dx, tz + dz))
    print(f"===== {key} (reach {len(tiles)})")
    for (sym, ops, name), v in sorted(found.items(), key=lambda kv: kv[0][0]):
        print(f"  {sym:<42} '{name[:18]}' [{ops}] x{len(v)} {sorted(set(v))[:2]}")


if __name__ == "__main__":
    for k in (sys.argv[1:] or ARRIVALS):
        run(k)
