#!/usr/bin/env python3
"""Builds the Isle of Souls drop data files from the OSRS Wiki.

    python tools/drops/wiki_import.py              # uses tools/drops/monsters.txt
    python tools/drops/wiki_import.py --refresh    # ignore the page cache and download again

Reads the monster page names in monsters.txt (one per line, `#` for comments), downloads each page's
wikitext (cached in tools/drops/.cache), and writes into
content/other/drop-tables/src/main/resources/org/rsmod/content/other/drops/:

    osrs.toml     the monsters' drop tables, with the npc ids taken from each page's infobox
    boosted.toml  the drops the Raging Echoes relics boost (from the wiki's "Boosted drops" page)

Both files are regenerated completely: put your own changes in isle.toml instead.
The data format is described in docs/isle_of_souls_drops.md. A report of everything that was
skipped (and why) is printed at the end and written to tools/drops/last_report.txt.
"""

import argparse
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOOL_DIR = Path(__file__).resolve().parent
CACHE_DIR = TOOL_DIR / ".cache"
RESOURCES = ROOT / "content/other/drop-tables/src/main/resources/org/rsmod/content/other/drops"
API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "rsmod-isle-of-souls-dropimport/0.2 (ordinaryoak2012@gmail.com)"

AREAS = [
    "Misthalin", "Karamja", "Asgarnia", "Fremennik Province", "Kandarin", "Kharidian Desert",
    "Morytania", "Tirannwn", "Wilderness", "Kebos and Kourend", "Varlamore",
]

# Shared tables: the wiki page "Rare drop table" has all three as sections.
SHARED_PAGE = "Rare drop table"
SHARED_SECTIONS = {
    "rare drop table": "Rare drop table",
    "gem drop table": "Gem drop table",
    "mega-rare drop table": "Mega-rare drop table",
}
SHARED_NAMES = {name.lower(): name for name in SHARED_SECTIONS.values()}

# Templates on monster pages that are not drops: ignored without a warning.
IGNORED_TEMPLATES = {
    "infobox monster", "droplogproject", "average drop value", "herbdroptableinfo", "reflist",
    "set drop table sources", "namedref", "slayerdropvaluetable", "synced switch",
}


class Report:
    def __init__(self):
        self.lines = []

    def warn(self, text):
        self.lines.append(text)

    def write(self):
        text = "\n".join(self.lines) if self.lines else "No warnings."
        (TOOL_DIR / "last_report.txt").write_text(text + "\n", encoding="utf-8")
        return text


report = Report()


# --- Fetching ------------------------------------------------------------------------------------

def fetch_wikitext(page, refresh=False):
    """Returns (resolved title, wikitext) for a page, following redirects. Cached on disk."""
    CACHE_DIR.mkdir(exist_ok=True)
    cache = CACHE_DIR / (re.sub(r"[^A-Za-z0-9_.-]", "_", page) + ".json")
    if cache.exists() and not refresh:
        data = json.loads(cache.read_text(encoding="utf-8"))
        return data["title"], data["wikitext"]

    params = urllib.parse.urlencode({
        "action": "parse", "format": "json", "formatversion": "2", "prop": "wikitext",
        "redirects": "1", "page": page,
    })
    request = urllib.request.Request(API + "?" + params, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        payload = json.load(response)
    if "parse" not in payload:
        raise RuntimeError(f"Wiki has no page `{page}`: {payload.get('error', payload)}")
    title = payload["parse"]["title"]
    wikitext = payload["parse"]["wikitext"]
    cache.write_text(json.dumps({"title": title, "wikitext": wikitext}), encoding="utf-8")
    time.sleep(1.0)  # be polite to the wiki
    return title, wikitext


# --- Wikitext helpers ----------------------------------------------------------------------------

def find_templates(text):
    """Yields (start, end, body) for each top-level {{...}} in text (body excludes the braces)."""
    i, n = 0, len(text)
    while i < n - 1:
        if text.startswith("{{", i):
            depth, j = 0, i
            while j < n - 1:
                if text.startswith("{{", j):
                    depth += 1
                    j += 2
                elif text.startswith("}}", j):
                    depth -= 1
                    j += 2
                    if depth == 0:
                        break
                else:
                    j += 1
            if depth == 0:
                yield i, j, text[i + 2:j - 2]
                i = j
                continue
        i += 1


def split_args(body):
    """Splits a template body on top-level pipes (not inside {{ }} or [[ ]])."""
    parts, depth, current, i = [], 0, "", 0
    while i < len(body):
        two = body[i:i + 2]
        if two in ("{{", "[["):
            depth += 1
            current += two
            i += 2
        elif two in ("}}", "]]"):
            depth -= 1
            current += two
            i += 2
        elif body[i] == "|" and depth == 0:
            parts.append(current)
            current = ""
            i += 1
        else:
            current += body[i]
            i += 1
    parts.append(current)
    return parts


def parse_template(body):
    """Returns (name, positional args, named args) of a template body."""
    parts = split_args(body)
    name = parts[0].strip()
    positional, named = [], {}
    for part in parts[1:]:
        m = re.match(r"^\s*([A-Za-z0-9_ ]+?)\s*=(.*)$", part, re.S)
        if m:
            named[m.group(1).strip().lower()] = m.group(2).strip()
        else:
            positional.append(part.strip())
    return name, positional, named


def clean(text):
    """Strips refs, comments, templates, links and tags from a short piece of wikitext."""
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"<ref[^>]*?/>", "", text)
    text = re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.S)
    text = re.sub(r"<br\s*/?>", " ", text)
    text = re.sub(r"\{\{[^{}]*\}\}", "", text)
    text = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text)
    return re.sub(r"\s+", " ", text.replace("&nbsp;", " ")).strip()


# --- Drop line parsing ---------------------------------------------------------------------------

NUMBER = re.compile(r"\d{1,3}(?:,\d{3})+|\d+")


def parse_rarity(text):
    """Returns 'always', a 'n/d' string, or None if the rarity is not a fixed chance."""
    value = clean(text).lower().replace("–", "-").replace("~", "").strip()
    if value == "always":
        return "always"
    m = re.fullmatch(r"([\d,]+(?:\.\d+)?)\s*/\s*([\d,]+(?:\.\d+)?)", value)
    if m:
        return f"{m.group(1)}/{m.group(2)}".replace(",", "")
    return None


def parse_quantity(text):
    """Returns (qty string, noted flag)."""
    value = clean(text).lower() if text else "1"
    noted = "(noted)" in value
    value = re.sub(r"\([^)]*\)", "", value)
    numbers = [int(n.replace(",", "")) for n in NUMBER.findall(value)]
    numbers = [n for n in numbers if n > 0]
    if not numbers:
        return "1", noted
    low, high = min(numbers), max(numbers)
    return (str(low) if low == high else f"{low}-{high}"), noted


def drops_line(named, ctx):
    """Turns the arguments of a DropsLine into an entry dict, or None (warns when it skips)."""
    name = clean(named.get("name", ""))
    if not name or name.lower() == "nothing":
        return None
    if "(f)" in named.get("namenotes", ""):
        return None  # free-to-play only line: this server is members
    if name.lower() == "brimstone key":
        return None  # only on a slayer task from Konar; its rarity is a template
    rarity = parse_rarity(named.get("rarity", ""))
    if rarity is None:
        report.warn(f"{ctx}: `{name}` skipped, rarity is not a fixed chance: "
                    f"`{clean(named.get('rarity', ''))}`")
        return None
    qty, noted = parse_quantity(named.get("quantity", "1"))
    entry = {"rarity": rarity, "qty": qty, "noted": noted}
    rolls = re.search(r"\d+", named.get("rolls", ""))
    if rolls and int(rolls.group(0)) > 1:
        entry["rolls"] = int(rolls.group(0))
    own_version = clean(named.get("dropversion", ""))
    if own_version:
        entry["version"] = own_version
    if name.lower() in SHARED_NAMES:
        entry["table"] = SHARED_NAMES[name.lower()]
    else:
        entry["item"] = name
    return entry


def herb_table_factors(wikitext):
    """Reads (herb name, weight factor, quantity default) rows from Template:HerbDropLines."""
    rows = []
    for _, _, body in find_templates(wikitext.replace("<includeonly>", "").replace("</includeonly>", "")):
        name, _, named = parse_template(body)
        if name.lower() != "dropsline":
            continue
        m = re.search(r"\(\s*(\d+)\s*\*", named.get("rarity", ""))
        if m:
            rows.append((clean(named["name"]), int(m.group(1))))
    return rows


def herb_entries(arg, quantity, herbs, ctx):
    m = re.fullmatch(r"\s*(\d+)\s*/\s*(\d+)\s*", arg or "")
    if not m or not herbs:
        report.warn(f"{ctx}: herb drop table `{arg}` could not be expanded")
        return []
    base = (int(m.group(1)) / int(m.group(2))) / 128.0
    entries = []
    for name, factor in herbs:
        denominator = round(1.0 / (factor * base))
        if denominator < 1:
            continue
        qty, noted = parse_quantity(quantity or "1")
        entries.append({"item": name, "rarity": f"1/{denominator}", "qty": qty, "noted": noted})
    return entries


# --- Page parsing --------------------------------------------------------------------------------

HEADING = re.compile(r"^(={2,6})\s*(.*?)\s*\1\s*$")


def walk_page(text):
    """Yields ('heading', level, title) and ('template', name, positional, named) in order."""
    i, n = 0, len(text)
    line_start = True
    while i < n:
        if line_start:
            end = text.find("\n", i)
            end = n if end == -1 else end
            m = HEADING.match(text[i:end])
            if m:
                yield ("heading", len(m.group(1)), clean(m.group(2)))
                i = end + 1
                continue
        if text.startswith("{{", i):
            found = next(find_templates(text[i:]), None)
            if found and found[0] == 0:
                _, end, body = found
                name, positional, named = parse_template(body)
                yield ("template", name, positional, named)
                i += end
                line_start = False
                continue
        line_start = text[i] == "\n"
        i += 1


def infobox_versions(text):
    """Returns [(set of drop versions, [npc ids])] from every Infobox Monster on the page."""
    versions = []
    for match in re.finditer(r"\{\{\s*Infobox Monster", text, re.I):
        found = next(find_templates(text[match.start():]), None)
        if not found or found[0] != 0:
            continue
        name, _, named = parse_template(found[2])
        indexes = sorted({int(m.group(1)) for k in named for m in [re.fullmatch(r"id(\d+)", k)] if m})
        keys = [str(i) for i in indexes] or [""]
        for k in keys:
            drop = clean(named.get("dropversion" + k, named.get("dropversion", "")))
            drops = [d.strip() for d in drop.split(",") if d.strip()]
            ids = [int(x) for x in re.findall(r"\d+", named.get("id" + k, ""))]
            versions.append((drops, ids))
    return versions


def classify(heading):
    h = heading.lower()
    if "100%" in h:
        return "always"
    if "tertiary" in h or "pre-roll" in h or "preroll" in h:
        return "tertiary"
    return "main"


def parse_monster_page(title, text, herbs, extra_ids=()):
    """Returns a list of monster blocks: dicts with source, dropversion, ids, always/main/tertiary."""
    infobox = infobox_versions(text)
    if extra_ids:
        infobox = infobox + [([], list(extra_ids))]
    lines = []  # (version or None, kind, heading, entry)
    heading, block = "", None

    def add(version, kind, entry):
        lines.append((version or None, kind, heading, entry))

    for event in walk_page(text):
        if event[0] == "heading":
            heading = event[2]
            continue
        _, name, positional, named = event
        key = name.lower()
        ctx = f"{title} [{heading}]"
        if key == "dropstablehead":
            block = {"dropversion": clean(named.get("dropversion", "")), "kind": classify(heading)}
        elif key == "dropstablebottom":
            block = None
        elif key in ("dropsline", "dropslinereward") and block is not None:
            entry = drops_line(named, ctx)
            if entry is None:
                continue
            kind = block["kind"]
            if entry["rarity"] == "always":
                if kind == "tertiary":
                    report.warn(f"{ctx}: `{entry.get('item')}` skipped: an \"always\" drop in a "
                                f"tertiary table is conditional (a quest or activity)")
                    continue
                if "table" in entry:
                    continue
                kind = "always"
            elif kind == "always":
                kind = "main"
            add(entry.pop("version", None) or block["dropversion"], kind, entry)
        elif key == "dropslineclue" and block is not None:
            rarity = parse_rarity(named.get("rarity", ""))
            clue = clean(named.get("type", ""))
            if rarity is None or rarity == "always" or not clue:
                report.warn(f"{ctx}: clue line skipped: {named}")
                continue
            entry = {"item": f"Clue scroll ({clue})", "rarity": rarity, "qty": "1", "noted": False}
            add(block["dropversion"], "tertiary", entry)
        elif key == "herbdroplines" and block is not None:
            quantity = positional[1] if len(positional) > 1 else None
            for entry in herb_entries(positional[0] if positional else "", quantity, herbs, ctx):
                add(block["dropversion"], "main", entry)
        elif key == "raredroptable":
            version = clean(named.get("dropversion", ""))
            for index, shared in enumerate(("Rare drop table", "Gem drop table")):
                if index < len(positional):
                    rarity = parse_rarity(positional[index])
                    if rarity is None:
                        report.warn(f"{ctx}: {shared} rate `{positional[index]}` not understood")
                        continue
                    add(version, "main", {"table": shared, "rarity": rarity, "qty": "1",
                                          "noted": False})
            if not positional:
                report.warn(f"{ctx}: rare drop table with no rate was skipped")
        elif key == "gemdroptable":
            rarity = parse_rarity(positional[0]) if positional else None
            if rarity:
                add(clean(named.get("dropversion", "")), "main",
                    {"table": "Gem drop table", "rarity": rarity, "qty": "1", "noted": False})
            else:
                report.warn(f"{ctx}: gem drop table rate not understood: {positional}")
        elif key not in IGNORED_TEMPLATES and "drop" in key and key not in (
            "dropstablehead", "dropstablebottom"
        ):
            report.warn(f"{ctx}: template `{name}` is not supported, its drops were skipped")

    if not lines:
        report.warn(f"{title}: no drop tables found")
        return []

    # Every drop version of the page: from the infobox and from lines that name one. Lines that
    # name none belong to all of them.
    versions = []
    for version in [d for ds, _ in infobox for d in ds] + [v for v, _, _, _ in lines if v]:
        if version.lower() not in [x.lower() for x in versions]:
            versions.append(version)
    if not versions:
        versions = [""]

    blocks = []
    for version in versions:
        mine = [(k, h, e) for v, k, h, e in lines if v is None or v.lower() == version.lower()]
        ids = []
        for drops, version_ids in infobox:
            if not version or version.lower() in {d.lower() for d in drops}:
                ids += [i for i in version_ids if i not in ids]
        if not ids:
            ids = list(dict.fromkeys(i for _, version_ids in infobox for i in version_ids))
            if version and ids:
                report.warn(f"{title}: drop version `{version}` matches no infobox version; "
                            f"using every npc id of the page")
        blocks.append(build_block(title, version, ids, mine))
    return [b for b in blocks if b["always"] or b["main"] or b["tertiary"]]


def build_block(title, version, ids, lines):
    always = [e for k, _, e in lines if k == "always"]
    main = [(h, e) for k, h, e in lines if k == "main"]
    tertiary = [e for k, _, e in lines if k == "tertiary"]
    extra_shared = []

    # A main table is one roll and cannot add up to more than 100%. When a page lists several
    # tables that each add up to 100% (Kalphite Queen's consumables, for example), each is its
    # own independent roll: they become shared tables that are always rolled.
    totals = {}
    for heading, entry in main:
        totals[heading] = totals.get(heading, 0.0) + to_fraction(entry["rarity"])
    if sum(totals.values()) > 1.02:
        separate = [h for h, t in totals.items() if 0.98 <= t <= 1.02]
        for heading in separate:
            name = f"{source_label(title)} {version} {heading}".replace("  ", " ")
            entries = [e for h, e in main if h == heading]
            extra_shared.append((name, entries))
            tertiary.append({"table": name, "rarity": "1/1", "qty": "1", "noted": False})
        main = [(h, e) for h, e in main if h not in separate]

    rolls = {e.get("rolls", 1) for _, e in main}
    if len(rolls) > 1:
        report.warn(f"{title} ({version or 'default'}): main lines have different roll counts "
                    f"{sorted(rolls)}; using {max(rolls)}")
    return {"source": source_label(title), "dropversion": version, "ids": ids, "always": always,
            "main": [e for _, e in main], "tertiary": tertiary, "extra_shared": extra_shared,
            "main_rolls": max(rolls) if rolls else 1}


def source_label(title):
    m = re.fullmatch(r"(.*) \(Echo\)", title)
    return f"Echo {m.group(1)}" if m else title


def parse_shared_tables(text):
    """Returns {shared table name: [entries]} from the Rare drop table page."""
    shared = {}
    top = ""
    block_open = False
    for event in walk_page(text):
        if event[0] == "heading":
            level, title = event[1], event[2]
            if level == 2:
                top = title.lower()
            continue
        _, name, positional, named = event
        key = name.lower()
        if key == "dropstablehead":
            block_open = True
        elif key == "dropstablebottom":
            block_open = False
        elif key in ("dropsline", "dropslinereward") and block_open and top in SHARED_SECTIONS:
            entry = drops_line(named, f"{SHARED_PAGE} [{top}]")
            if entry is not None and entry["rarity"] != "always":
                shared.setdefault(SHARED_SECTIONS[top], []).append(entry)
    return shared


# --- Output --------------------------------------------------------------------------------------

def to_fraction(rarity):
    n, d = rarity.split("/")
    return float(n) / float(d)


def toml_str(value):
    return json.dumps(value, ensure_ascii=False)


def line(entry, with_chance=True):
    parts = []
    if "table" in entry:
        parts.append(f"table = {toml_str(entry['table'])}")
    else:
        parts.append(f"item = {toml_str(entry['item'])}")
        if entry["qty"] != "1":
            parts.append(f"qty = {toml_str(entry['qty'])}")
        if entry["noted"]:
            parts.append("noted = true")
    if with_chance:
        parts.append(f"chance = {toml_str(entry['chance'])}")
    return "{ " + ", ".join(parts) + " }"


def normalise_main(entries, label):
    """The main table is one roll: its chances must not add up to more than 100%."""
    for entry in entries:
        entry["chance"] = entry["rarity"]
    total = sum(to_fraction(e["rarity"]) for e in entries)
    if total <= 1.0 + 1e-9:
        return entries
    if total <= 1.02:
        report.warn(f"{label}: main chances add up to {total:.4f}; scaled down to 100%")
    else:
        report.warn(f"{label}: main chances add up to {total:.2f} (far over 100%); scaled down. "
                    f"Check this table by hand")
    for entry in entries:
        entry["chance"] = f"{to_fraction(entry['rarity']) / total:.8f}".rstrip("0")
    return entries


def render_list(key, entries, with_chance=True):
    if not entries:
        return ""
    body = "".join(f"  {line(e, with_chance)},\n" for e in entries)
    return f"{key} = [\n{body}]\n"


def render_monster(block):
    out = ["[[monster]]\n", f"source = {toml_str(block['source'])}\n"]
    if block["dropversion"]:
        out.append(f"# drop version: {block['dropversion']}\n")
    if block.get("inherit"):
        out.append(f"inherit = [{', '.join(toml_str(i) for i in block['inherit'])}]\n")
    if block["ids"]:
        out.append(f"ids = [{', '.join(str(i) for i in block['ids'])}]\n")
    elif block.get("no_ids_note"):
        out.append("# no npc ids: attach to the npcs with `attach = true` in isle.toml\n")
    else:
        out.append("# no npc ids in this cache: nothing is attached (attach it in isle.toml)\n")
        report.warn(f"{block['source']}: no npc ids in the cache, so this table is not attached")
    if block.get("main_rolls", 1) > 1:
        out.append(f"main_rolls = {block['main_rolls']}\n")
    out.append(render_list("always", block["always"], with_chance=False))
    label = f"{block['source']} ({block['dropversion'] or 'default'})"
    out.append(render_list("main", normalise_main(block["main"], label)))
    for e in block["tertiary"]:
        e["chance"] = e["rarity"]
    out.append(render_list("tertiary", block["tertiary"]))
    shared = "".join(render_shared(n, es) for n, es in block["extra_shared"])
    return shared + "".join(out) + "\n"


def render_shared(name, entries):
    entries = normalise_main(entries, f"shared table {name}")
    return f"[[shared]]\nname = {toml_str(name)}\n{render_list('main', entries)}\n"


# --- Boosted drops -------------------------------------------------------------------------------

def parse_boosted(refresh):
    """Returns (boosts, uniques). boosts are (source, item) pairs the relics make more likely;
    uniques are the (source, item, rarity) echo boss drops, which are not boosted."""
    boosts = []
    uniques = []
    for area in AREAS:
        title, text = fetch_wikitext(f"Raging Echoes League/Areas/{area}/Notable Drops", refresh)
        for _, _, body in find_templates(text):
            name, _, named = parse_template(body)
            if name != "LeagueBoostedDrop":
                continue
            item = clean(named.get("name") or named.get("optname", ""))
            if named.get("tiers") != "3" or not named.get("multi1"):
                # Tiers=1: a fixed rate, not boosted by the relics. Echo boss uniques come here.
                source = clean(named.get("source") or named.get("optsource", ""))
                rarity = parse_rarity(named.get("rarity", ""))
                if source.startswith("Echo ") and item and rarity:
                    uniques.append((source, item, rarity))
                continue
            raw_source = named.get("source") or named.get("optsource", "")
            links = re.findall(r"\[\[([^\]|]+)(?:\|[^\]]*)?\]\]", re.sub(r"<ref.*?(?:/>|</ref>)", "", raw_source, flags=re.S))
            sources = links or [clean(raw_source)]
            for source in sources:
                source = clean(source)
                if item and source:
                    boosts.append((source, item))
    return boosts, uniques


# --- Echo bosses ---------------------------------------------------------------------------------

# Each echo boss also rolls its normal boss's drops. (The Dagannoth Kings, Hunllef and Sol Heredit
# are special: see docs/isle_of_souls_drops.md.)
ECHO_INHERITS = {
    "Echo Cerberus": ["Cerberus"],
    "Echo King Black Dragon": ["King Black Dragon"],
    "Echo Kalphite Queen": ["Kalphite Queen"],
    "Echo Thermonuclear smoke devil": ["Thermonuclear smoke devil"],
    "Echo Grotesque Guardians": ["Grotesque Guardians"],
    "Echo Hespori": ["Hespori"],
}


def echo_blocks(uniques):
    """One table per echo boss, with its unique drops as independent rolls. They have no npc ids:
    the cache has no echo npcs yet, so attach them (`attach = true`) once the npcs exist."""
    by_source = {}
    for source, item, rarity in uniques:
        by_source.setdefault(source, []).append(
            {"item": item, "rarity": rarity, "qty": "1", "noted": False})
    blocks = []
    for source, entries in by_source.items():
        blocks.append({
            "source": source, "dropversion": "", "ids": [], "always": [], "main": [],
            "tertiary": entries, "extra_shared": [], "main_rolls": 1,
            "inherit": ECHO_INHERITS.get(source, []), "no_ids_note": True,
        })
    return blocks


def valid_npc_ids():
    """Npc ids that exist in this server's cache (from the symbol file), or None if unknown."""
    sym = ROOT / ".data/symbols/npc.sym"
    if not sym.exists():
        return None
    ids = set()
    for text in sym.read_text(encoding="utf-8", errors="replace").splitlines():
        head = text.split("\t")[0].strip()
        if head.isdigit():
            ids.add(int(head))
    return ids


# --- Main ----------------------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("--monsters", default=str(TOOL_DIR / "monsters.txt"))
    parser.add_argument("--refresh", action="store_true", help="download pages again")
    args = parser.parse_args()

    pages = []
    for raw in Path(args.monsters).read_text(encoding="utf-8").splitlines():
        text = raw.split("#")[0].strip()
        if not text:
            continue
        # `Page`, or `Page | ids_from=Other page; Another page | ids=1,2,3`
        parts = [x.strip() for x in text.split("|")]
        options = dict(x.split("=", 1) for x in parts[1:] if "=" in x)
        pages.append((parts[0], options))

    _, herb_text = fetch_wikitext("Template:HerbDropLines", args.refresh)
    herbs = herb_table_factors(herb_text)
    _, shared_text = fetch_wikitext(SHARED_PAGE, args.refresh)
    shared = parse_shared_tables(shared_text)

    blocks = []
    assigned = set()
    cache_ids = valid_npc_ids()
    for page, options in pages:
        try:
            title, text = fetch_wikitext(page, args.refresh)
            extra = [int(x) for x in re.findall(r"\d+", options.get("ids", ""))]
            for other in [x.strip() for x in options.get("ids_from", "").split(";") if x.strip()]:
                _, other_text = fetch_wikitext(other, args.refresh)
                for _, ids in infobox_versions(other_text):
                    extra += [i for i in ids if i not in extra]
        except Exception as error:  # noqa: BLE001 - report and keep going
            report.warn(f"{page}: could not be downloaded: {error}")
            continue
        found = parse_monster_page(title, text, herbs, extra)
        for block in found:
            # An npc can only have one table in a file (the files themselves are layered), so
            # when a version of the same page or another page repeats an id, the first one wins.
            kept = [i for i in block["ids"] if i not in assigned]
            if len(kept) != len(block["ids"]):
                report.warn(f"{title} ({block['dropversion'] or 'default'}): "
                            f"{len(block['ids']) - len(kept)} npc ids already have a table; skipped")
            if block["ids"] and not kept:
                block["skip"] = True
            block["ids"] = kept
            assigned.update(kept)
        found = [b for b in found if not b.get("skip")]
        if cache_ids is not None:
            for block in found:
                missing = [i for i in block["ids"] if i not in cache_ids]
                if missing:
                    report.warn(f"{title} ({block['dropversion'] or 'default'}): "
                                f"{len(missing)} npc ids are not in this cache and were dropped")
                    block["ids"] = [i for i in block["ids"] if i in cache_ids]
        print(f"{title}: {len(found)} drop table(s)", file=sys.stderr)
        blocks += found

    RESOURCES.mkdir(parents=True, exist_ok=True)
    header = ("# GENERATED by tools/drops/wiki_import.py from the OSRS Wiki. Do not hand-edit: the next\n"
              "# import overwrites this file. Put changes and additions in isle.toml.\n\n")
    osrs = header + "".join(render_shared(n, e) for n, e in shared.items())
    osrs += "".join(render_monster(b) for b in blocks)
    (RESOURCES / "osrs.toml").write_text(osrs, encoding="utf-8", newline="\n")

    boosts, uniques = parse_boosted(args.refresh)
    echo = echo_blocks(uniques)
    (RESOURCES / "osrs.toml").write_text(osrs + "".join(render_monster(b) for b in echo),
                                         encoding="utf-8", newline="\n")
    boosted = header.replace("osrs.toml", "boosted.toml") + "".join(
        f"[[boost]]\nsource = {toml_str(s)}\nitem = {toml_str(i)}\n\n" for s, i in boosts)
    (RESOURCES / "boosted.toml").write_text(boosted, encoding="utf-8", newline="\n")

    print(f"Wrote {len(blocks)} monster tables, {len(echo)} echo boss tables, "
          f"{len(shared)} shared tables, {len(boosts)} boosted drops.", file=sys.stderr)
    print(report.write(), file=sys.stderr)


if __name__ == "__main__":
    main()
