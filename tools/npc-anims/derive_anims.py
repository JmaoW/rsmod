"""
Derives an `attack_anim`, a `defend_anim` and a `death_anim` for every npc in the vanilla cache that can be hit, from
the names of the animations the npc already walks and idles with, and writes them to
api/cache-enricher/.../npc/npc_anims.toml.

Why: the `defend_anim` and `death_anim` params default to `human_unarmedblock` and `human_death`,
and the hand-kept npcs.toml only sets them for ~60 npcs. Every other npc therefore flinches and
dies like a player, which is wrong for anything that is not a humanoid. The cache itself does not link an npc to its block animation, but
its animations are named in families: a moss giant stands with `giant_update_moss_ready` and blocks
with `giant_update_moss_defend`; a pyrefiend stands with `pyrefiend_ready` and blocks with
`pyrefiend_defend`. This swaps the ready/walk word for a block (or death) word and keeps the result
only if such a seq exists. Humanoids (`human_ready`, ...) have no such seq and keep the default.

Run from the repo root after changing the cache or the symbol files:
    python -I tools/npc-anims/derive_anims.py
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "spawns"))
import js5  # noqa: E402

SYMBOLS = os.path.join(ROOT, ".data", "symbols")
ENRICHER = os.path.join(
    ROOT, "api", "cache-enricher", "src", "main", "resources", "org", "rsmod", "api", "cache", "enricher", "npc"
)
NPCS_TOML = os.path.join(ENRICHER, "npcs.toml")
OUT = os.path.join(ENRICHER, "npc_anims.toml")

STANCE = {"ready", "walk", "idle", "stand", "run", "ready2", "idle2", "walking", "standing"}
BLOCK = ["defend", "block", "parry", "defence", "defense", "hit", "hurt", "flinch", "damaged", "recoil", "react"]
# `dead` is left out on purpose: those seqs are the pose held after dying, not the death itself.
DEATH = ["death", "die", "dying"]
# Only the plain or clearly melee seqs: npcs with several attack styles (`_attack_magic`, per weapon)
# keep the default rather than a guess.
ATTACK = ["attack", "attack_melee", "melee_attack"]
KINDS = {"attack_anim": ATTACK, "defend_anim": BLOCK, "death_anim": DEATH}
# What each param falls back to when an npc has none: no point writing it out again.
DEFAULTS = {
    "attack_anim": "human_unarmedpunch",
    "defend_anim": "human_unarmedblock",
    "death_anim": "human_death",
}


def sym(name):
    out = {}
    with open(os.path.join(SYMBOLS, name), encoding="utf8") as f:
        for line in f:
            a = line.rstrip("\n").split("\t")
            if len(a) == 2:
                out[int(a[0])] = a[1]
    return out


def read_str(d, p):
    e = d.index(0, p)
    return d[p:e].decode("latin1"), e + 1


def parse_npc(d):
    """Reads only what is needed: the ready and walk anim, the options and the combat level."""
    p = 0
    o = {"ready": -1, "walk": -1, "attackable": False, "combat": 0}
    while p < len(d):
        op = d[p]
        p += 1
        if op == 0:
            break
        if op == 1:
            p += 1 + 2 * d[p]
        elif op == 2:
            _, p = read_str(d, p)
        elif op == 12:
            p += 1
        elif op == 13:
            o["ready"] = int.from_bytes(d[p:p + 2], "big"); p += 2
        elif op == 14:
            o["walk"] = int.from_bytes(d[p:p + 2], "big"); p += 2
        elif op in (15, 16, 18):
            p += 2
        elif op == 17:
            p += 8
        elif 30 <= op < 35:
            text, p = read_str(d, p)
            if text.lower() == "attack":
                o["attackable"] = True
        elif op in (40, 41):
            p += 1 + 4 * d[p]
        elif op == 42:
            p += 1 + d[p]
        elif op == 60:
            p += 1 + 2 * d[p]
        elif 74 <= op < 80:
            p += 2
        elif op == 93 or op == 99:
            pass
        elif op == 95:
            o["combat"] = int.from_bytes(d[p:p + 2], "big"); p += 2
        elif op in (97, 98):
            p += 2
        elif op in (100, 101):
            p += 1
        else:
            break  # Everything wanted comes before the first opcode not handled here.
    return o


def derive(base, words, seq_ids):
    """Swaps the stance word in [base] for each of [words] and returns the seqs that exist."""
    if not base:
        return []
    toks = base.split("_")
    for i in range(len(toks) - 1, -1, -1):
        if toks[i] in STANCE:
            found = []
            for word in words:
                name = "_".join(toks[:i] + [word] + toks[i + 1:])
                if name in seq_ids and name != base:
                    found.append(name)
            if found:
                return found
    m = re.match(r"(.*?)(ready|walk|idle)$", base)  # Names such as `roosterready`.
    if m:
        return [m.group(1) + w for w in words if m.group(1) + w in seq_ids]
    return []


def derive_base(base, words, seq_ids):
    """
    For a variant stance (`troll_ready_shield`, `ork_update_weapon_ready`) whose block or death
    seq is named after the base creature (`troll_death`, `ork_update_death`): drops the variant
    words off the prefix until a seq exists. Words are only cut off the prefix while at least two are
    left, so a family such as `giant_update_...` is never cut down to a bare `giant`.
    """
    if not base:
        return []
    toks = base.split("_")
    for i in range(len(toks)):
        if toks[i] not in STANCE:
            continue
        prefix = toks[:i]
        for keep in range(len(prefix), 0, -1):
            if keep < len(prefix) and keep < 2:
                break
            found = ["_".join(prefix[:keep] + [w]) for w in words]
            found = [n for n in found if n in seq_ids and n != base]
            if found:
                return found
        break
    return []


# The only suffixes `derive_sole_attack` accepts: a plain melee attack named after how it lands.
PLAIN_ATTACK_SUFFIX = {
    "tail", "melee", "bite", "claw", "sting", "swipe", "punch", "stab", "slash", "strike", "01", "02",
}


def derive_sole_attack(base, seq_ids):
    """
    For a family whose one attack seq has a suffix (`scorpion_update_attack_tail`): returns it if
    it is the only `<prefix>_attack_<suffix>` seq with a plain melee suffix. The prefix must be at
    least two words and not a humanoid one, so a weapon variant (`..._attack_spear`) or a whole
    family (`human_attack_*`) is never taken for the creature's own attack.
    """
    if not base:
        return []
    toks = base.split("_")
    for i in range(len(toks)):
        if toks[i] not in STANCE:
            continue
        if i < 2 or toks[0] == "human":
            return []
        stem = "_".join(toks[:i]) + "_attack_"
        found = [
            name for name in seq_ids
            if name.startswith(stem) and set(name[len(stem):].split("_")) <= PLAIN_ATTACK_SUFFIX
        ]
        return found if len(found) == 1 else []
    return []


# Families the names cannot decide, keyed on the walk seq the npcs share. A hellhound idles with the
# small dog seq and has no `dog_update_hellhound_attack`, so the name rule would give it the small
# dog bite and the human block and death. The big-dog set below (heavier 19 frame lunge, made for
# the hellhound models) is the closest the cache has. Chosen by hand, not read from the cache.
FAMILY_OVERRIDES = {
    "dog_update_hellhound_walk": {
        "attack_anim": "dog_update_hellhound_godwars_attack",
        "defend_anim": "dog_update_godwars_defend",
        "death_anim": "dog_update_hellhound_godwars_death",
    },
    "dog_update_hellhound_godwars_walk": {
        "attack_anim": "dog_update_hellhound_godwars_attack",
        "defend_anim": "dog_update_godwars_defend",
        "death_anim": "dog_update_hellhound_godwars_death",
    },
    # The abyssal demon has ready/attack/death seqs but no block seq, so it fell back to the human
    # flinch. Hold its idle animation when hit instead.
    "abyssal_walk": {
        "defend_anim": "abyssal_ready",
    },
    # The Karuulm Slayer Dungeon monsters are named for their attacks (`drake_melee`,
    # `hydra_lesser_attack_ranged`), which the attack words above do not match, and none of them
    # has a block seq, so they punched and flinched like a player. Generic npc combat only swings
    # in melee, so each takes its melee (or ranged, where it has no melee) attack and holds its
    # idle when hit. The wyrm's burrowed form idles with `wyrm_walk` and its standing form with
    # `wyrm_ready`; WyrmScript switches between the two.
    "wyrm_walk": {"attack_anim": "wyrm_attack_melee", "defend_anim": "wyrm_walk"},
    "wyrm_ready": {"attack_anim": "wyrm_attack_melee", "defend_anim": "wyrm_ready"},
    "drake_walk": {
        "attack_anim": "drake_melee",
        "defend_anim": "drake_ready",
        "death_anim": "drake_death_01",
    },
    "hydra_lesser_walk": {
        "attack_anim": "hydra_lesser_attack_ranged",
        "defend_anim": "hydra_lesser_ready",
    },
    "superior_hydra_walk": {
        "attack_anim": "superior_hydra_attack_ranged",
        "defend_anim": "superior_hydra_idle",
    },
    "sulphur_lizard_walk": {
        "attack_anim": "sulphur_lizard_melee",
        "defend_anim": "sulphur_lizard_ready",
    },
    "hydra_stage_1_walk": {
        "attack_anim": "hydra_stage_1_attack_ranged",
        "defend_anim": "hydra_stage_1_ready",
    },
    "hydra_stage_2_walk": {
        "attack_anim": "hydra_stage_2_attack_ranged",
        "defend_anim": "hydra_stage_2_ready",
    },
    "hydra_stage_3_walk": {
        "attack_anim": "hydra_stage_3_attack_ranged",
        "defend_anim": "hydra_stage_3_ready",
    },
    "hydra_stage_4_walk": {
        "attack_anim": "hydra_stage_4_attack_ranged",
        "defend_anim": "hydra_stage_4_ready",
    },
}


def skeleton_attack(name):
    """
    The `skeleton_update` skeletons split their attack by weapon (`_attack_weapon`, `_attack_sword`)
    and have no plain `_attack`. Which weapon an npc holds is in its models, which are not read
    here, so they all get the generic weapon swing. Mages cast instead, and ranged ones are left
    on the default rather than swing at range.
    """
    if any(word in name for word in ("mage", "magic", "mystic")):
        return "skeleton_update_mage_casting"
    if "ranged" in name:
        return None
    return "skeleton_update_attack_weapon"


def parse_seq_frames(d):
    """The first frame of a seq: ("frame", group, file) or ("keyframe", set id), or None."""
    p = 0
    while p < len(d):
        op = d[p]
        p += 1
        if op == 0:
            return None
        if op == 1:
            n = int.from_bytes(d[p:p + 2], "big"); p += 2
            if n == 0:
                continue  # Keyframe seqs carry an empty frame list before their set (op 13).
            # Delays, then the low halves (file ids), then the high halves (group ids).
            file = int.from_bytes(d[p + 2 * n:p + 2 * n + 2], "big")
            group = int.from_bytes(d[p + 4 * n:p + 4 * n + 2], "big")
            return ("frame", group, file)
        if op in (2, 6, 7):
            p += 2
        elif op == 3:
            p += 1 + d[p]
        elif op == 4:
            pass
        elif op in (5, 8, 9, 10, 11):
            p += 1
        elif op == 12:
            p += 1 + 4 * d[p]
        elif op == 13:
            return ("keyframe", int.from_bytes(d[p:p + 4], "big"))
        else:
            return None  # Frames always come before anything not handled here.
    return None


class Skeletons:
    """
    The skeleton (frame base) a seq animates. A seq only moves a model built for its skeleton:
    played on any other it twists the model. Human seqs use frame base 0.
    """

    def __init__(self):
        self.seqs = js5.split_files(js5.read_group(2, 12), js5.ref_table(2)[12])
        self.frames = js5.ref_table(0)
        self.keyframes = js5.ref_table(22)
        self.cache = {}

    def of(self, seq):
        if seq in self.cache:
            return self.cache[seq]
        found = None
        first = parse_seq_frames(self.seqs[seq]) if seq in self.seqs else None
        if first and first[0] == "frame" and first[1] in self.frames:
            files = js5.split_files(js5.read_group(0, first[1]), self.frames[first[1]])
            data = files.get(first[2]) or next(iter(files.values()))
            found = ("frame", int.from_bytes(data[0:2], "big"))
        elif first and first[0] == "keyframe" and first[1] >> 16 in self.keyframes:
            group, file = first[1] >> 16, first[1] & 0xFFFF
            files = js5.split_files(js5.read_group(22, group), self.keyframes[group])
            data = files.get(file) or next(iter(files.values()))
            found = ("keyframe", int.from_bytes(data[1:3], "big"))  # After a version byte.
        self.cache[seq] = found
        return found


HUMAN_SKELETON = ("frame", 0)


def still_when_hit(seq_names, skeletons, ready):
    """
    For an npc with no block seq of its own: its ready seq when it idles on a rig that is not human,
    which tells the server not to flinch at all (see `Npc.combatPlayDefendAnim`). That is how the
    game shows such npcs; the default human block would twist their models. Humanoids keep it.
    """
    name = seq_names.get(ready)
    if name is None or name.startswith("human"):
        return None
    skeleton = skeletons.of(ready)
    if skeleton is None or skeleton == HUMAN_SKELETON:
        return None
    return name


def existing_anims(key):
    """Npcs npcs.toml already gives [key] (`defend_anim`, ...): those are kept as they are."""
    with open(NPCS_TOML, encoding="utf8") as f:
        blocks = f.read().split("[[config]]")
    names = set()
    for block in blocks:
        name = re.search(r"^npc = '([^']+)'", block, re.M)
        if name and re.search(rf"^{key} = ", block, re.M):
            names.add(name.group(1))
    return names


def main():
    seq_names, npc_names = sym("seq.sym"), sym("npc.sym")
    seq_ids = {v: k for k, v in seq_names.items()}
    files = js5.ref_table(2)[9]
    group = js5.split_files(js5.read_group(2, 9), files)
    keep = {key: existing_anims(key) for key in KINDS}
    skeletons = Skeletons()
    stills = 0

    rows = {key: [] for key in KINDS}
    skipped_existing = {key: 0 for key in KINDS}
    no_name = 0
    for nid in sorted(group):
        n = parse_npc(group[nid])
        if not (n["attackable"] or n["combat"] > 0):
            continue
        name = npc_names.get(nid)
        if name is None:
            no_name += 1
            continue
        for key, words in KINDS.items():
            if name in keep[key]:
                skipped_existing[key] += 1
                continue
            override = FAMILY_OVERRIDES.get(seq_names.get(n["walk"]), {}).get(key)
            if override:
                rows[key].append((name, override))
                continue
            if key == "attack_anim" and seq_names.get(n["ready"]) == "skeleton_update_ready":
                override = skeleton_attack(name)
                if override:
                    rows[key].append((name, override))
                continue
            ready = derive(seq_names.get(n["ready"]), words, seq_ids)
            walk = derive(seq_names.get(n["walk"]), words, seq_ids)
            picks = ready[:1] + walk[:1]
            if not picks:
                picks = (derive_base(seq_names.get(n["ready"]), words, seq_ids)[:1]
                         + derive_base(seq_names.get(n["walk"]), words, seq_ids)[:1])
            if not picks and key == "attack_anim":
                picks = (derive_sole_attack(seq_names.get(n["ready"]), seq_ids)[:1]
                         + derive_sole_attack(seq_names.get(n["walk"]), seq_ids)[:1])
            if not picks and key == "defend_anim":
                still = still_when_hit(seq_names, skeletons, n["ready"])
                if still:
                    rows[key].append((name, still))
                    stills += 1
                continue
            if not picks:
                continue
            # When the two disagree, one is a more specific variant (`dragon_block_kbd`): take the
            # longer.
            pick = max(picks, key=len)
            if pick != DEFAULTS[key]:
                rows[key].append((name, pick))

    with open(OUT, "w", encoding="utf8", newline="\n") as f:
        f.write("# Generated by tools/npc-anims/derive_anims.py. Do not edit; re-run the script.\n")
        f.write("# Anims for npcs that npcs.toml does not give one. `npc_name = seq id`.\n")
        for key in KINDS:
            f.write(f"\n[{key}]\n")
            for name, pick in rows[key]:
                f.write(f'"{name}" = {seq_ids[pick]} # {pick}\n')  # Quoted: some names hold `+`.
    for key in KINDS:
        print(f"{key}: wrote {len(rows[key])} npcs (kept {skipped_existing[key]} from npcs.toml)")
    print(f"defend_anim: {stills} of them hold their idle (no block seq, not a human rig)")
    print(f"wrote {OUT}; {no_name} combat npcs have no symbol name")


if __name__ == "__main__":
    main()
