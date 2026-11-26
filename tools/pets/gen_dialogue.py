#!/usr/bin/env python3
"""Turns the OSRS Wiki's pet transcripts into the isle's Talk-to dialogue data.

    python -I tools/pets/gen_dialogue.py

Reads `Transcript:<pet>` for every pet of `gen_pets.PETS` (cached by `wiki.py` in `.cache/`, so
run with network once) and writes `pets/dialogue.json` in the isle module's resources: for every
pet form (`<pet key>/<form label>`) the conversation its "Talk-to" plays, as a tree:

    {"t": "line", "who": "player" | "npc", "text": "..."}
    {"t": "box", "text": "..."}                       a message box (narration, {{tbox}})
    {"t": "menu", "title": "...", "random": false, "opts": [{"label": "...", "nodes": [...]}]}
    {"t": "cond", "branches": [{"cond": "...", "nodes": [...]}]}
    {"t": "end"} / {"t": "back"} / {"t": "restart"}

Only each transcript's talk-to sections are kept: menagerie overheads, "Using items on..." and
the like are left out. A form without its own section uses the closest one (Enraged Tektiny talks
like Tektiny).
"""

import html
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import gen_pets  # noqa: E402
import wiki  # noqa: E402

OUT = (ROOT / "content/areas/misc/isle-of-souls/src/main/resources/org/rsmod/content/areas/misc"
       / "isleofsouls/pets/dialogue.json")

EXCLUDE = re.compile(
    r"overhead|menagerie|using|uses|if the player|right-click|historical|before \d|rare alt|"
    r"other player|mobile client|upon|initial|dig|when dropped|same item|battling",
    re.I,
)

# Transcript titles the item name does not redirect to.
TITLES = {
    "dagannoth_prime": "Transcript:Pet Dagannoth Prime",
    "smol_heredit": "Transcript:Smol Heredit",
}

# Form label -> extra words that name its transcript section.
SYNONYMS = {
    "rock": ["normal"],
    "normal": ["normal", "regular"],
    "gold": ["golden"],
    "red": ["black/grey/red"],
    "grey": ["black/grey/red"],
    "black": ["black/grey/red"],
    "raccoon": ["standard dialogue"],
    "thermonuclear": ["thermonuclear form"],
    "regular": ["regular", "standard dialogue"],
    "white lily": ["white lily"],
    "dragonfruit": ["dragonfruit"],
}


def clean(text):
    text = re.sub(r"\{\{sic\}\}", "", text)
    text = re.sub(r"\{\{(?:nbsp|\*)\}\}", " ", text)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]+)\]\]", r"\1", text)
    text = re.sub(r"\{\{(?:plink|plinkt|plinkp)\|([^|}]+)[^}]*\}\}", r"\1", text)
    text = re.sub(r"\{\{[^{}]*\}\}", "", text)
    text = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>", "", text)
    text = re.sub(r"<br\s*/?>", " ", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = text.replace("'''", "").replace("''", "")
    text = html.unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def sections(wikitext):
    """(heading path, body lines) for every heading's own text, in order."""
    out, path, body = [], [], []
    for line in wikitext.split("\n"):
        m = re.match(r"^(=+)\s*(.*?)\s*=+\s*$", line)
        if m:
            if body:
                out.append((list(path), body))
            level = len(m.group(1)) - 1
            path = path[: level - 1] + [clean(m.group(2))]
            body = []
        elif line.startswith("*"):
            body.append(line)
    if body:
        out.append((list(path), body))
    return out


def template(content, name):
    m = re.match(r"\{\{\s*" + name + r"\s*(?:\|(.*))?\}\}\s*$", content, re.I | re.S)
    return None if m is None else (m.group(1) or "")


def parse(lines):
    """Lines of `*`-depth items -> a node list."""
    items = []
    for line in lines:
        depth = len(line) - len(line.lstrip("*"))
        items.append((depth, line[depth:].strip()))

    def children(start, depth):
        """Indexes of the direct children of items[start - 1] (all at `depth`) and the end."""
        idx, i = [], start
        while i < len(items) and items[i][0] >= depth:
            if items[i][0] == depth:
                idx.append(i)
            i += 1
        return idx, i

    def build(start, depth):
        idx, end = children(start, depth)
        nodes, k = [], 0
        pending_title, pending_random = None, False
        while k < len(idx):
            i = idx[k]
            content = items[i][1]
            nxt = idx[k + 1] if k + 1 < len(idx) else end
            sel = template(content, "tselect")
            if sel is not None:
                pending_title = clean(sel) or None
                k += 1
                continue
            if template(content, "trandom") is not None:
                pending_random = True
                k += 1
                continue
            if template(content, "topt") is not None:
                opts = []
                while k < len(idx) and template(items[idx[k]][1], "topt") is not None:
                    i = idx[k]
                    nxt = idx[k + 1] if k + 1 < len(idx) else end
                    label = clean(template(items[i][1], "topt"))
                    opts.append({"label": label, "nodes": build(i + 1, depth + 1)})
                    k += 1
                nodes.append({"t": "menu", "title": pending_title, "random": pending_random,
                              "opts": opts})
                pending_title, pending_random = None, False
                continue
            if template(content, "tcond") is not None:
                branches = []
                while k < len(idx) and template(items[idx[k]][1], "tcond") is not None:
                    i = idx[k]
                    cond = clean(template(items[i][1], "tcond"))
                    branches.append({"cond": cond, "nodes": build(i + 1, depth + 1)})
                    k += 1
                nodes.append({"t": "cond", "branches": branches})
                continue
            act = template(content, "tact")
            if act is not None:
                act = act.strip().lower()
                if act == "end":
                    nodes.append({"t": "end"})
                elif act in ("other", "previous", "above"):
                    nodes.append({"t": "back"})
                elif act == "initial":
                    nodes.append({"t": "restart"})
                elif "=" not in act:
                    nodes.append({"t": "box", "text": clean(template(content, "tact"))})
                k += 1
                continue
            box = template(content, "tbox")
            if box is not None:
                text = clean(box.split("|")[-1])
                if text:
                    nodes.append({"t": "box", "text": text})
                k += 1
                continue
            m = re.match(r"'''(.+?):?'''\s*:?\s*(.*)$", content, re.S)
            if m:
                text = clean(m.group(2))
                if text and "{{overhead" not in m.group(2):
                    who = "player" if m.group(1).strip().lower() == "player" else "npc"
                    nodes.append({"t": "line", "who": who, "text": text})
            elif content and not content.startswith("{{"):
                text = clean(content)
                if text:
                    nodes.append({"t": "box", "text": text})
            k += 1
        return nodes

    return build(0, 1)


def norm(text):
    return re.sub(r"\s+", " ", re.sub(r"(variant|dialogue|form)$", "", text.lower().strip())).strip()


def score(path, pet_name, label):
    words = [label] + SYNONYMS.get(label, [])
    best = 0
    for part in path:
        p = norm(part)
        for w in words:
            w = w.lower()
            if p == w or p == f"pet {w}":
                best = max(best, 20)
            elif w and w in p:
                best = max(best, 10)
            elif p and p in w and p not in ("standard", ""):
                best = max(best, 5)
    return best


def choose(blocks, pet_name, label):
    usable = [(path, nodes) for path, nodes in blocks if nodes]
    if not usable:
        return None
    if label:
        scored = sorted(usable, key=lambda b: -score(b[0], pet_name, label))
        if score(scored[0][0], pet_name, label) > 0:
            return scored[0][1]
    for path, nodes in usable:
        if [norm(p) for p in path] in (["standard"], ["standard", "owner"]):
            return nodes
    return usable[0][1]


def main():
    out = {}
    missing = []
    for key, name, forms in gen_pets.PETS:
        title = TITLES.get(key, "Transcript:" + name)
        try:
            _, text = wiki.fetch(title)
        except Exception:
            missing.append(name)
            continue
        blocks = []
        for path, lines in sections(text):
            if any(EXCLUDE.search(p) for p in path):
                continue
            blocks.append((path, parse(lines)))
        for _, _, label in forms:
            nodes = choose(blocks, name, label)
            if nodes:
                out[f"{key}/{label}"] = nodes
            else:
                missing.append(f"{key}/{label}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {OUT}: {len(out)} forms")
    if missing:
        print("no dialogue:", ", ".join(missing))


if __name__ == "__main__":
    main()
