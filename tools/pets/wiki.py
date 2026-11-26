"""Cached OSRS Wiki wikitext fetcher for the pet tools."""

import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

CACHE_DIR = Path(__file__).resolve().parent / ".cache"
API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "rsmod-league-dev/1.0 (personal dev project)"


def fetch(page, refresh=False):
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
    time.sleep(0.5)
    return title, wikitext


if __name__ == "__main__":
    title, text = fetch(sys.argv[1])
    sys.stdout.buffer.write((title + "\n" + text + "\n").encode("utf-8", "replace"))
