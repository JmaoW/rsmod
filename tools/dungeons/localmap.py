"""ASCII map round a tile: H = reachable from the dungeon arrival (no doors), # blocked, . free but cut off,
letters = the named locs. Usage: localmap.py ARRIVAL_KEY X Z R [sym ...]"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import collision as K, census as C
from reachexits import ARRIVALS
key = sys.argv[1]; cx, cz, r = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
names = sys.argv[5:]
lvl, ax, az = ARRIVALS[key]
reach = K.flood(lvl, ax, az, 200000)
K.SOFT = True
soft = K.flood(lvl, ax, az, 800000)
K.SOFT = False
sym = {v: k for k, v in C.LOC_SYM.items()}
marks = {}
for i, n in enumerate(names):
    for (lid, x, z, l, sh, rot) in C.census(cx - r, cz - r, cx + r, cz + r):
        if lid == sym[n] and l == lvl:
            t = C.locs()[lid]
            w, h = (t["sx"], t["sz"]) if rot % 2 == 0 else (t["sz"], t["sx"])
            for dx in range(w):
                for dz in range(h):
                    marks[(x + dx, z + dz)] = chr(ord('A') + i)
print("legend:", {chr(ord('A') + i): n for i, n in enumerate(names)}, "H hard-reach, s soft-reach, . free, # blocked")
print("      " + "".join(str(x % 10) for x in range(cx - r, cx + r + 1)))
for z in range(cz + r, cz - r - 1, -1):
    row = ""
    for x in range(cx - r, cx + r + 1):
        p = (x, z)
        row += marks.get(p) or ("H" if p in reach else ("s" if p in soft else ("#" if K.blocked_tile(lvl, x, z) else ".")))
    print(f"{z} {row}")
print("x from", cx - r)
