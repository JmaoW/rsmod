"""Converts the rs-map-viewer OSRS npc spawn list into rsmod `npcs.toml` spawn files.

Source:  https://github.com/dennisdev/rs-map-viewer (src/mapviewer/data/npc/npc-spawns-osrs.json)
Output:  content/areas/misc/osrs-npc-spawns/src/main/resources/.../surface.toml + underground.toml
Report:  tools/spawns/last_report.txt

Run from anywhere:  python tools/spawns/convert.py [--fetch]

Every spawn is checked against the real cache (rev 233) before it is written:
  * the npc id must have a name in the cache (nameless types are unclickable placeholders),
  * the cache name must still resemble the spawn's name (ids can be reused),
  * the map square must exist in the cache (spawns on blocked terrain are only reported: fishing
    spots and ducks stand on water).
Everything dropped is listed in the report with its reason. Edit exclusions.txt to drop more.
"""

import collections
import json
import os
import re
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))

import js5  # noqa: E402

SOURCE = os.path.join(HERE, "source", "npc-spawns-osrs.json")
SOURCE_URL = (
    "https://raw.githubusercontent.com/dennisdev/rs-map-viewer/master/"
    "src/mapviewer/data/npc/npc-spawns-osrs.json"
)
MODULE = os.path.join(ROOT, "content", "areas", "misc", "osrs-npc-spawns")
RESOURCES = os.path.join(
    MODULE, "src", "main", "resources", "org", "rsmod", "content", "areas", "misc", "osrsnpcspawns"
)
LUMBRIDGE_TOML = os.path.join(
    ROOT,
    "content/areas/city/lumbridge/src/main/resources/org/rsmod/content/areas/city/lumbridge/npcs.toml",
)
NPC_SYM = os.path.join(ROOT, ".data", "symbols", "npc.sym")
EXCLUSIONS = os.path.join(HERE, "exclusions.txt")
REPORT = os.path.join(HERE, "last_report.txt")

# Map squares with a y of at least this are underground, minigame or instanced space.
UNDERGROUND_Z = 4000 // 64 + 1  # map square z (so y >= 4032)


# ----------------------------------------------------------------------------------------------
# Cache readers
# ----------------------------------------------------------------------------------------------


def _cstr(d, p):
    e = d.index(0, p)
    return d[p:e].decode("latin1"), e + 1


def _parse_npc(d):
    """Reads just enough of an npc definition to get its name and transforms (see NpcTypeDecoder)."""
    out = {"name": None, "multi": [], "level": 0, "ops": []}
    p = 0
    while p < len(d):
        op = d[p]
        p += 1
        if op == 0:
            break
        elif op == 1 or op == 60:
            p += 1 + 2 * d[p]
        elif op == 2:
            out["name"], p = _cstr(d, p)
        elif op == 3:
            _, p = _cstr(d, p)
        elif op == 12:
            p += 1
        elif op in (13, 14, 15, 16, 18):
            p += 2
        elif op == 17:
            p += 8
        elif 30 <= op <= 34:
            text, p = _cstr(d, p)
            out["ops"].append(text)
        elif op in (40, 41):
            p += 1 + 4 * d[p]
        elif 74 <= op <= 79:
            p += 2
        elif op in (93, 99, 107, 109, 111, 122, 123):
            pass
        elif op == 95:
            out["level"] = int.from_bytes(d[p : p + 2], "big")
            p += 2
        elif op in (97, 98, 103, 112, 114, 124):
            p += 2
        elif op in (100, 101, 119):
            p += 1
        elif op == 102:
            flags = d[p]
            p += 1
            for i in range(8):
                if flags & (1 << i):
                    p += 4 if d[p] & 0x80 else 2
                    p += 2 if d[p] & 0x80 else 1
        elif op in (106, 118):
            p += 4
            if op == 118:
                out["multi"].append(int.from_bytes(d[p : p + 2], "big"))
                p += 2
            n = d[p]
            p += 1
            out["multi"] += [int.from_bytes(d[p + 2 * k : p + 2 * k + 2], "big") for k in range(n + 1)]
            p += 2 * (n + 1)
        elif op == 113:
            p += 4
        elif op in (115, 121):
            p += 8 if op == 115 else 1 + 4 * d[p]
        elif op == 249:
            n = d[p]
            p += 1
            for _ in range(n):
                is_str = d[p]
                p += 4
                if is_str:
                    _, p = _cstr(d, p)
                else:
                    p += 4
        else:
            break  # unknown opcode: keep what was read (the name comes early)
    return out


def load_cache_npcs():
    files = js5.split_files(js5.read_group(2, 9), js5.ref_table(2)[9])
    return {fid: _parse_npc(b) for fid, b in files.items()}


def _jhash(s):
    h = 0
    for c in s:
        h = (ord(c) + ((h << 5) - h)) & 0xFFFFFFFF
    return h


def _named_groups(index):
    d = js5.read_group(255, index)
    proto = d[0]
    pos = 1 + (4 if proto >= 6 else 0)
    flags = d[pos]
    pos += 1

    def big(p):
        if proto >= 7 and d[p] & 0x80:
            return int.from_bytes(d[p : p + 4], "big") & 0x7FFFFFFF, p + 4
        return int.from_bytes(d[p : p + 2], "big"), p + 2

    count, pos = big(pos)
    ids, acc = [], 0
    for _ in range(count):
        v, pos = big(pos)
        acc += v
        ids.append(acc)
    names = []
    if flags & 1:
        for _ in range(count):
            names.append(int.from_bytes(d[pos : pos + 4], "big"))
            pos += 4
    return dict(zip(names, ids))


class Terrain:
    """Per map square tile settings (bit 1 = blocked, bit 2 = bridge), cached per square."""

    def __init__(self):
        self.groups = _named_groups(5)
        self.squares = {}
        self.unreadable = set()

    def square(self, mx, mz):
        key = (mx, mz)
        if key not in self.squares:
            gid = self.groups.get(_jhash(f"m{mx}_{mz}"))
            if gid is None:
                self.squares[key] = None
            else:
                try:
                    self.squares[key] = self._read(js5.read_group(5, gid))
                except (IndexError, ValueError):
                    self.unreadable.add(key)  # different terrain layout: skip the blocked check
                    self.squares[key] = {}
        return self.squares[key]

    @staticmethod
    def _read(data):
        settings = {}
        p = 0
        for lvl in range(4):
            for x in range(64):
                for z in range(64):
                    s = 0
                    while True:
                        op = int.from_bytes(data[p : p + 2], "big")  # opcodes are shorts here
                        p += 2
                        if op == 0:
                            break
                        if op == 1:
                            p += 1
                            break
                        if op <= 49:
                            p += 2
                        elif op <= 81:
                            s = op - 49
                        # anything above is an underlay id with no payload
                    settings[(lvl, x, z)] = s
        return settings

    def blocked(self, level, x, z):
        sq = self.square(x // 64, z // 64)
        if sq is None:
            return None
        if not sq:
            return False
        lx, lz = x % 64, z % 64
        eff = level + 1 if level < 3 and sq[(level + 1, lx, lz)] & 2 else level
        return bool(sq[(eff, lx, lz)] & 1)


# ----------------------------------------------------------------------------------------------
# Name checks
# ----------------------------------------------------------------------------------------------


def norm(s):
    return re.sub(r"[^a-z0-9]", "", re.sub(r"<[^>]*>", "", (s or "").lower()))


def words(s):
    return {w for w in re.split(r"[^a-z0-9]+", re.sub(r"<[^>]*>", "", (s or "").lower())) if len(w) > 2}


def cache_names(npcs, i, depth=0):
    v = npcs.get(i)
    if v is None:
        return set()
    out = {v["name"]} if v["name"] else set()
    if depth < 2:
        for t in v["multi"]:
            if t != 65535:
                out |= cache_names(npcs, t, depth + 1)
    return out


def verdict(npcs, i, spawn_name):
    """'ok', 'multi' (quest-state transform, accepted), 'nameless' or 'renamed'."""
    v = npcs.get(i)
    if v is None:
        return "missing"
    names = cache_names(npcs, i)
    if not names:
        return "nameless"
    sn = norm(spawn_name)
    if not sn:
        return "ok"
    for n in names:
        cn = norm(n)
        if cn == sn or cn in sn or sn in cn or words(n) & words(spawn_name):
            return "ok"
    return "multi" if v["multi"] else "renamed"


# ----------------------------------------------------------------------------------------------
# Exclusions
# ----------------------------------------------------------------------------------------------


def load_exclusions():
    """exclusions.txt lines:  `npc <id|name>`, `rect x0 z0 x1 z1 [level]`, `near <id> x z radius`."""
    rules = []
    if not os.path.exists(EXCLUSIONS):
        return rules
    for raw in open(EXCLUSIONS, encoding="utf-8"):
        line = raw.split("#")[0].strip()
        if not line:
            continue
        parts = line.split()
        rules.append((parts[0], parts[1:], raw.strip()))
    return rules


def excluded_by(rules, s):
    for kind, args, text in rules:
        if kind == "npc":
            if (args[0].isdigit() and int(args[0]) == s["id"]) or norm(" ".join(args)) == norm(s["name"]):
                return text
        elif kind == "rect":
            x0, z0, x1, z1 = map(int, args[:4])
            lvl = int(args[4]) if len(args) > 4 else None
            if x0 <= s["x"] <= x1 and z0 <= s["y"] <= z1 and (lvl is None or lvl == s["level"]):
                return text
        elif kind == "near":
            i, x, z, r = map(int, args[:4])
            if s["id"] == i and abs(s["x"] - x) <= r and abs(s["y"] - z) <= r:
                return text
    return None


def lumbridge_squares():
    out = set()
    if not os.path.exists(LUMBRIDGE_TOML):
        return out
    for m in re.finditer(r"coords = '(\d+)_(\d+)_(\d+)_", open(LUMBRIDGE_TOML).read()):
        out.add((int(m.group(2)), int(m.group(3))))
    return out


# ----------------------------------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------------------------------


def main():
    if "--fetch" in sys.argv or not os.path.exists(SOURCE):
        print("downloading", SOURCE_URL)
        os.makedirs(os.path.dirname(SOURCE), exist_ok=True)
        with urllib.request.urlopen(SOURCE_URL) as r, open(SOURCE, "wb") as f:
            f.write(r.read())
    spawns = json.load(open(SOURCE, encoding="utf-8"))

    sym = {}
    for line in open(NPC_SYM):
        parts = line.split()
        if len(parts) >= 2:
            sym[int(parts[0])] = parts[1]
    npcs = load_cache_npcs()
    terrain = Terrain()
    rules = load_exclusions()
    lumb = lumbridge_squares()

    dropped = collections.defaultdict(list)  # reason -> spawns
    kept, seen, on_blocked = [], set(), []
    verdicts = {}
    for s in spawns:
        key = (s["id"], s["x"], s["y"], s["level"])
        if key in seen:
            dropped["duplicate entry in the source"].append(s)
            continue
        seen.add(key)
        i = s["id"]
        if i not in sym:
            dropped["npc id has no symbol name in npc.sym"].append(s)
            continue
        if i not in verdicts:
            verdicts[i] = verdict(npcs, i, s["name"])
        v = verdicts[i]
        if v == "nameless":
            dropped["nameless npc type in the cache (unclickable placeholder)"].append(s)
            continue
        if v in ("renamed", "missing"):
            dropped["id now belongs to a different npc (name changed)"].append(s)
            continue
        if (s["x"] // 64, s["y"] // 64) in lumb:
            dropped["map square already covered by the hand-made Lumbridge file"].append(s)
            continue
        rule = excluded_by(rules, s)
        if rule:
            dropped[f"exclusions.txt: {rule}"].append(s)
            continue
        blocked = terrain.blocked(s["level"], s["x"], s["y"])
        if blocked is None:
            dropped["map square is not in the cache"].append(s)
            continue
        if blocked:
            # Water dwellers (fishing spots, ducks, ...) stand on "blocked" terrain by design, so
            # this is only reported, never dropped.
            on_blocked.append(s)
        kept.append(s)

    def coords(s):
        x, z = s["x"], s["y"]
        return f"{s['level']}_{x // 64}_{z // 64}_{x % 64}_{z % 64}"

    def sort_key(s):
        return (s["x"] // 64, s["y"] // 64, s["level"], s["x"], s["y"], s["id"])

    kept.sort(key=sort_key)
    files = {"surface.toml": [], "underground.toml": []}
    for s in kept:
        target = "underground.toml" if s["y"] // 64 >= UNDERGROUND_Z else "surface.toml"
        files[target].append(s)
    os.makedirs(RESOURCES, exist_ok=True)
    for name, rows in files.items():
        with open(os.path.join(RESOURCES, name), "w", newline="\n") as f:
            f.write("# Generated by tools/spawns/convert.py. Do not edit; change exclusions.txt and re-run.\n\n")
            for s in rows:
                f.write(f"[[spawn]]\nnpc = '{sym[s['id']]}'\ncoords = '{coords(s)}'\n\n")

    write_report(spawns, kept, files, dropped, npcs, sym, on_blocked)
    print(f"read {len(spawns)}, kept {len(kept)} "
          f"(surface {len(files['surface.toml'])}, underground {len(files['underground.toml'])}), "
          f"dropped {len(spawns) - len(kept)}; report: {REPORT}")


# Places the user may want to look at; nothing here is excluded by default.
AREAS_OF_INTEREST = {
    "Fight Caves (we use instances)": (2370, 5050, 2445, 5135),
    "Inferno (we use instances)": (2230, 5300, 2310, 5390),
    "Zulrah shrine (we use an instance)": (2250, 3060, 2290, 3095),
    "Tzhaar city": (2420, 5090, 2500, 5200),
    "Wintertodt": (1600, 3920, 1660, 4040),
    "Tempoross": (3000, 2960, 3060, 3040),
    "Isle of Souls (our isle)": (2080, 2770, 2300, 2980),
}


def write_report(spawns, kept, files, dropped, npcs, sym, on_blocked):
    out = []
    out.append(f"Source spawns: {len(spawns)}")
    out.append(f"Written:       {len(kept)}  (surface {len(files['surface.toml'])}, "
               f"underground {len(files['underground.toml'])})")
    out.append(f"Dropped:       {len(spawns) - len(kept)}\n")
    out.append("== Dropped, by reason ==")
    for reason, rows in sorted(dropped.items(), key=lambda kv: -len(kv[1])):
        out.append(f"\n{len(rows):6d}  {reason}")
        counts = collections.Counter((s["id"], s["name"]) for s in rows)
        for (i, name), c in counts.most_common(40):
            cn = sorted(cache_names(npcs, i))
            out.append(f"          id {i:5d} x{c:<4d} spawn name {name!r:34} cache name {cn[:2]}")
        if len(counts) > 40:
            out.append(f"          ... and {len(counts) - 40} more npc ids")

    out.append("\n== Written spawns inside areas you may care about ==")
    for label, (x0, z0, x1, z1) in AREAS_OF_INTEREST.items():
        rows = [s for s in kept if x0 <= s["x"] <= x1 and z0 <= s["y"] <= z1]
        names = collections.Counter(s["name"] for s in rows).most_common(8)
        out.append(f"\n{label}: {len(rows)} spawns  rect=({x0},{z0})-({x1},{z1})")
        out.append("   " + ", ".join(f"{n} x{c}" for n, c in names))

    out.append(f"\n== Written spawns on blocked terrain: {len(on_blocked)} (mostly water dwellers) ==")
    for (i, name), c in collections.Counter((s["id"], s["name"]) for s in on_blocked).most_common(25):
        out.append(f"  {c:5d}  {name} (id {i})")

    out.append("\n== Written spawns per npc (top 40) ==")
    for (i, name), c in collections.Counter((s["id"], s["name"]) for s in kept).most_common(40):
        out.append(f"  {c:5d}  {name} (id {i}, {sym[i]})")
    open(REPORT, "w", encoding="utf-8").write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
