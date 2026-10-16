#!/usr/bin/env python3
"""Parses the wiki's Slayer master pages into tools/slayer/masters.json.

    python tools/slayer/parse_masters.py

For each master it reads "<Master>/Slayer assignments" (task table) and the master's own page
(points table, level requirements, block cost) from the cached wiki wikitext, and writes:

    masters.json   {master: {combat, slayer, blockCost, points: {...}, tasks: [...]}}
"""

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import wiki

TOOL_DIR = Path(__file__).resolve().parent
MASTERS = ["Turael", "Vannaka", "Duradel"]
# From each master page: "combat level of at least 85", "combat level of 100 and a Slayer level of 50".
REQUIREMENTS = {"Turael": (0, 0), "Vannaka": (40, 0), "Nieve": (85, 0), "Duradel": (100, 50)}


def strip_refs(text):
    text = re.sub(r"<ref.*?</ref>", "", text, flags=re.S)
    text = re.sub(r"\{\{(?:CiteTwitter|Cite[A-Za-z]*)\|.*?\}\}", "", text, flags=re.S)
    return text


def parse_table(text):
    """Returns (headers, rows) for the first wikitable in text; rows are lists of cell strings."""
    start = text.index("{|")
    end = text.index("|}", start)
    body = text[start:end]
    blocks = re.split(r"\n\|-[^\n]*\n", body)
    headers = [re.sub(r"^!(?:[^|]*\|)?", "", h).strip()
               for h in blocks[0].split("\n") if h.startswith("!")]
    rows = []
    for block in blocks[1:]:
        cells, current = [], None
        for line in block.split("\n"):
            if line.startswith("|") and not line.startswith("|}"):
                if current is not None:
                    cells.append(current)
                current = line[1:]
            elif current is not None:
                current += "\n" + line
        if current is not None:
            cells.append(current)
        if cells:
            rows.append(cells)
    return headers, rows


def clean_cell(cell):
    cell = re.sub(r"^[^|\[{]*\|(?=[^|])", "", cell) if cell.startswith("data-sort-value") else cell
    return strip_refs(cell).strip()


def link_text(cell):
    """First wikilink's display name, minus the 'Slayer task/' prefix."""
    m = re.search(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]", cell)
    if not m:
        return cell.strip()
    name = m.group(2) or m.group(1)
    return name.strip()


def all_links(cell):
    return [(m.group(2) or m.group(1)).strip()
            for m in re.finditer(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]", cell)]


def parse_range(cell):
    nums = re.findall(r"\d+", cell)
    if not nums:
        return None
    return [int(nums[0]), int(nums[-1])]


def parse_requirements(cell):
    req = {"slayer": 0, "combat": 0, "other": []}
    # Only text before any parenthetical alternative like "brutal black dragons (Slayer 77)".
    for m in re.finditer(r"\{\{SCP\|(\w+)\|(\d+)", cell):
        skill, level = m.group(1).lower(), int(m.group(2))
        before = cell[:m.start()]
        if before.count("(") > before.count(")"):
            continue
        if skill == "slayer":
            req["slayer"] = max(req["slayer"], level)
        elif skill == "combat":
            req["combat"] = max(req["combat"], level)
        else:
            req["other"].append(f"{skill} {level}")
    plain = re.sub(r"\{\{SCP\|[^}]*\}\}", "", cell)
    for text in re.findall(r"(?:completion of|partial completion of|Completion of)\s*\[\[([^\]|]+)", plain):
        req["other"].append("quest: " + text)
    for text in re.findall(r"[Uu]nlocked the ''([^']+)''", plain):
        req["other"].append("unlock: " + text)
    return req


def parse_assignments(master):
    _, text = wiki.fetch(f"{master}/Slayer assignments")
    headers, rows = parse_table(text)
    idx = {}
    for i, h in enumerate(headers):
        low = h.lower()
        if "monster" in low:
            idx["monster"] = i
        elif low.startswith("amount"):
            idx["amount"] = i
        elif "extended" in low:
            idx["extended"] = i
        elif "unlock" in low:
            idx["req"] = i
        elif low == "alternative(s)":
            idx["alt"] = i
        elif "weight" in low:
            idx["weight"] = i
    tasks = []
    for cells in rows:
        if len(cells) != len(headers):
            print(f"  ! {master}: row with {len(cells)} cells, expected {len(headers)}: {cells[0][:40]}")
            continue
        weight = re.search(r"\{\{\+=\|weight\|(\d+)", cells[idx["weight"]])
        if not weight:
            print(f"  ! {master}: no weight for {cells[0][:40]}")
            continue
        req = parse_requirements(strip_refs(cells[idx["req"]]))
        raw_req = re.sub(r"\s+", " ", strip_refs(cells[idx["req"]])).strip()
        tasks.append({
            "name": link_text(cells[idx["monster"]]),
            "link": all_links(cells[idx["monster"]])[0] if all_links(cells[idx["monster"]]) else None,
            "amount": parse_range(clean_cell(cells[idx["amount"]])),
            "extended": parse_range(clean_cell(cells[idx["extended"]])) if "extended" in idx else None,
            "weight": int(weight.group(1)),
            "slayer": req["slayer"],
            "combat": req["combat"],
            "other": req["other"],
            "rawReq": raw_req,
            "alternatives": all_links(cells[idx["alt"]]) if "alt" in idx else [],
        })
    return tasks


def parse_points(text):
    """The first points table: rows 'Every task', 'Every 10th'... Returns {interval: default}."""
    start = text.index("Task interval")
    chunk = text[start:text.index("|}", start)]
    out = {}
    for m in re.finditer(r"\|Every (task|\d[\d,]*)(?:th|st|nd|rd)?\s*\n\|(\d+)", chunk):
        key = "1" if m.group(1) == "task" else m.group(1).replace(",", "")
        out[key] = int(m.group(2))
    return out


def main():
    result = {}
    for master in MASTERS:
        print(master)
        _, page = wiki.fetch(master)
        try:
            tasks = parse_assignments(master)
        except RuntimeError:
            tasks = []  # no assignments subpage (Vannaka): tasks come from the cache only
        points = parse_points(page) if "Task interval" in page else {}
        block = re.search(r"[Bb]locking a task with [^.]*? costs (\d+)", page)
        combat = re.search(r"combat level of at least (\d+)", page)
        slayer = re.search(r"Slayer level of at least (\d+)|level (\d+) Slayer", page)
        result[master] = {
            "combat": REQUIREMENTS[master][0],
            "slayer": REQUIREMENTS[master][1],
            "slayerPageHint": slayer.group(0) if slayer else None,
            "blockCost": int(block.group(1)) if block else None,
            "points": points,
            "tasks": tasks,
        }
        print(f"  {len(tasks)} tasks, points {points}, combat {result[master]['combat']}, "
              f"block {result[master]['blockCost']}, slayer hint {result[master]['slayerPageHint']}")
    (TOOL_DIR / "masters.json").write_text(json.dumps(result, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
