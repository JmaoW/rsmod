"""Minimal enum config reader for the vanilla cache (archive 2, group 8)."""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/spawns"))
import js5  # noqa: E402

_group = None


def _load():
    global _group
    if _group is None:
        files = js5.ref_table(2)[8]
        _group = js5.split_files(js5.read_group(2, 8), files)
    return _group


def read_enum(enum_id):
    """Returns {key: value} for the enum, or None if the cache has no such enum."""
    group = _load()
    if enum_id not in group:
        return None
    data = group[enum_id]
    p, out = 0, {}
    while p < len(data):
        code = data[p]
        p += 1
        if code == 0:
            break
        if code in (1, 2):
            p += 1
        elif code == 3:
            p = data.index(0, p) + 1
        elif code == 4:
            p += 4
        elif code in (5, 6):
            count = int.from_bytes(data[p:p + 2], "big")
            p += 2
            for _ in range(count):
                key = int.from_bytes(data[p:p + 4], "big", signed=True)
                p += 4
                if code == 5:
                    end = data.index(0, p)
                    out[key] = data[p:end].decode("cp1252")
                    p = end + 1
                else:
                    out[key] = int.from_bytes(data[p:p + 4], "big", signed=True)
                    p += 4
    return out
