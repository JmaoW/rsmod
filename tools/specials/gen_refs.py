#!/usr/bin/env python3
"""Generates the Kotlin reference objects the special attacks use:

  SpecialObjs.kt         every obj in the cache's `sa_energy_requirements` enum (by symbol name)
  SpecialFxRefs.kt       the seqs and spotanims listed in fx_ids.json (by symbol name)

    python tools/specials/gen_refs.py

fx_ids.json is {"seq": [ids or symbol names...], "spot": [...]}; add an id there, rerun, and `fx_seqs.<symbol>` or
`fx_spots.<symbol>` exists. The ids are looked up in .data/symbols/{seq,spotanim}.sym.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/slayer"))
from enums import read_enum  # noqa: E402

OUT = ROOT / "content/other/special-attacks/src/main/kotlin/org/rsmod/content/other/special/attacks/configs"
PKG = "org.rsmod.content.other.special.attacks.configs"


def load_sym(name):
    out = {}
    for line in (ROOT / ".data/symbols" / name).read_text(encoding="utf-8").replace("\r", "").split("\n"):
        t = line.split("\t")
        if len(t) == 2 and t[0].isdigit():
            out[int(t[0])] = t[1]
    return out


def ident(name):
    s = name.replace("++", "_plus_plus").replace("+", "_plus")
    s = re.sub(r"[^A-Za-z0-9_]", "_", s)
    if s[0].isdigit():
        s = "_" + s
    return s


def write(path, text):
    # The working tree is CRLF.
    path.write_bytes(text.replace("\n", "\r\n").encode("utf-8"))


def gen_objs():
    syms = load_sym("obj.sym")
    enum = read_enum(906)
    names = [syms[k] for k in sorted(enum)]
    seen = {}
    lines = []
    for n in names:
        i = ident(n)
        assert i not in seen, f"identifier clash: {n} / {seen[i]}"
        seen[i] = n
        lines.append(f'    val {i} = find("{n}")')
    text = f"""package {PKG}

import org.rsmod.api.type.refs.obj.ObjReferences

typealias sobjs = SpecialObjs

/** Every obj with a special attack energy requirement in the cache (generated, do not edit). */
object SpecialObjs : ObjReferences() {{
{chr(10).join(lines)}
}}
"""
    write(OUT / "SpecialObjs.kt", text)
    print("objs:", len(names))


def gen_fx():
    ids = json.loads((Path(__file__).parent / "fx_ids.json").read_text())
    seq, spot = load_sym("seq.sym"), load_sym("spotanim.sym")
    parts = []
    for kind, table, ref, base, alias, desc in (
        ("seq", seq, "SeqReferences", "seq", "fx_seqs", "SpecialFxSeqs"),
        ("spot", spot, "SpotanimReferences", "spot", "fx_spots", "SpecialFxSpots"),
    ):
        lines, seen = [], set()
        byname = {v: k for k, v in table.items()}
        wanted = set()
        for x in ids[kind]:
            if isinstance(x, str) and x not in byname:
                print(f"warning: no {kind} symbol named {x}, skipped")
            else:
                wanted.add(byname[x] if isinstance(x, str) else x)
        for i in sorted(wanted):
            n = table.get(i)
            if not n:
                print(f"warning: no {kind} symbol for id {i}, skipped")
                continue
            if ident(n) in seen:
                continue
            seen.add(ident(n))
            lines.append(f'    val {ident(n)} = find("{n}")')
        parts.append((ref, base, alias, desc, lines))
    text = f"""package {PKG}

import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences

typealias fx_seqs = SpecialFxSeqs

typealias fx_spots = SpecialFxSpots

"""
    for ref, base, alias, desc, lines in parts:
        text += f"""/** Animations and graphics the special attacks play (generated, do not edit). */
object {desc} : {ref}() {{
{chr(10).join(lines)}
}}

"""
    write(OUT / "SpecialFxRefs.kt", text.rstrip("\n") + "\n")
    print("seqs:", len(parts[0][4]), "spots:", len(parts[1][4]))


if __name__ == "__main__":
    gen_objs()
    gen_fx()
