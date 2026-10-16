import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import collision as K
# (key, level, anchor x, z): the exit loc the player arrives beside
ANCHORS = {
    "taverley": (0, 2884, 9797),
    "edgeville": (0, 3097, 9867),
    "varrock": (0, 3237, 9858),
    "revenant_high": (0, 3244, 10215),
    "revenant_low": (0, 3218, 10058),
    "stronghold": (0, 2430, 9824),
    "brimhaven": (0, 2714, 9564),
    "karamja": (0, 2856, 9569),
    "kraken": (0, 2276, 9987),
    "smoke": (0, 2404, 9414),
    "catacombs": (0, 1666, 10051),
    "forthos": (0, 1830, 9974),
    "ham": (0, 3149, 9653),
    "slayer_basement": (3, 3412, 9931),
    "crash_site": (0, 2129, 5647),
    "fremennik": (0, 2809, 10001),
    "kalphite": (2, 3483, 9510),
    "mos_le_harmless": (0, 3749, 9373),
    "isle_dungeon": (0, 2168, 9307),
    "lizardman": (0, 1305, 9974),
    "karuulm": (0, 1311, 10185),
    "godwars": (2, 2881, 5311),
}
if __name__ == "__main__":
    for key in (sys.argv[1:] or ANCHORS):
        lvl, ax, az = ANCHORS[key]
        res = []
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if (dx, dz) == (0, 0): continue
                x, z = ax + dx, az + dz
                if K.blocked_tile(lvl, x, z): continue
                n = len(K.flood(lvl, x, z, 30000))
                res.append((n, abs(dx) + abs(dz), x, z))
        res.sort(key=lambda r: (-(r[0] > 200), r[1], -r[0]))
        print(key, (ax, az, lvl), [(x, z, n) for n, d, x, z in res[:4]])
