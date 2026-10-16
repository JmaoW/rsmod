"""Cached OSRS Wiki wikitext fetcher for the Slayer tools (same approach as tools/drops)."""

import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
CACHE_DIR = TOOL_DIR / ".cache"
API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "rsmod-isle-of-souls-slayerimport/0.1 (ordinaryoak2012@gmail.com)"


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
    time.sleep(1.0)
    return title, wikitext


if __name__ == "__main__":
    import sys
    title, text = fetch(sys.argv[1])
    print(title)
    sys.stdout.buffer.write(text.encode("utf-8", "replace") + chr(10).encode())
