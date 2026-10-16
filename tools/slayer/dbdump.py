#!/usr/bin/env python3
"""Dumps the Slayer dbtables out of the vanilla cache -> tools/slayer/cache_tables.json.

    python tools/slayer/dbdump.py

The rev 233 cache has the real Slayer data as database tables (113 slayer_task, 114 slayer_master_task,
115 slayer_area, 116 slayer_task_sublist, 117 slayer_unlock). Column names come from
.data/symbols/dbcol.sym, row names from dbrow.sym, and ids that appear in the other symbol files
(npc, obj, struct, enum, stat, category...) are shown by name next to the raw int.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/spawns"))
import js5  # noqa: E402

SYMBOLS = ROOT / ".data/symbols"
TOOL_DIR = Path(__file__).resolve().parent
TABLES = {113: "slayer_task", 114: "slayer_master_task", 115: "slayer_area",
          116: "slayer_task_sublist", 117: "slayer_unlock"}
CONFIG_ARCHIVE, DBROW_GROUP = 2, 38
STRING_TYPE = 36


class Buf:
    def __init__(self, data):
        self.d, self.p = data, 0

    def u8(self):
        v = self.d[self.p]
        self.p += 1
        return v

    def i32(self):
        v = int.from_bytes(self.d[self.p:self.p + 4], "big", signed=True)
        self.p += 4
        return v

    def smart(self):
        if self.d[self.p] < 128:
            return self.u8()
        v = int.from_bytes(self.d[self.p:self.p + 2], "big") - 0x8000
        self.p += 2
        return v

    def var_int(self):
        value = 0
        while True:
            b = self.u8()
            if b & 0x80:
                value = (value | (b & 0x7F)) << 7
            else:
                return value | b

    def string(self):
        end = self.d.index(0, self.p)
        s = self.d[self.p:end].decode("cp1252")
        self.p = end + 1
        return s


def decode_row(data):
    buf = Buf(data)
    columns, table = {}, None
    while buf.p < len(data):
        code = buf.u8()
        if code == 0:
            break
        if code == 3:
            buf.u8()  # column count
            col = buf.u8()
            while col != 0xFF:
                types = [buf.smart() for _ in range(buf.u8())]
                groups = buf.smart()
                values = []
                for _ in range(groups):
                    values.append([buf.string() if t == STRING_TYPE else buf.i32() for t in types])
                columns[col] = {"types": types, "values": values}
                col = buf.u8()
        elif code == 4:
            table = buf.var_int()
        else:
            raise ValueError(f"unknown dbrow code {code}")
    return table, columns


def load_syms(name):
    out = {}
    path = SYMBOLS / f"{name}.sym"
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            parts = line.split("\t")
            if len(parts) == 2 and parts[0].isdigit():
                out[int(parts[0])] = parts[1]
    return out


def main():
    colnames = {}
    for line in (SYMBOLS / "dbcol.sym").read_text(encoding="utf-8").splitlines():
        m = re.match(r"([a-z_]+):(\d+)\t(.*)", line)
        if m:
            colnames[(m.group(1), int(m.group(2)))] = m.group(3).split(":", 1)[1]
    rownames = load_syms("dbrow")

    files = js5.ref_table(CONFIG_ARCHIVE)[DBROW_GROUP]
    group = js5.split_files(js5.read_group(CONFIG_ARCHIVE, DBROW_GROUP), files)
    out = {name: [] for name in TABLES.values()}
    for row_id, data in group.items():
        table, columns = decode_row(data)
        if table not in TABLES:
            continue
        tname = TABLES[table]
        cols = {colnames.get((tname, c), f"col{c}"): v for c, v in sorted(columns.items())}
        out[tname].append({"row": row_id, "name": rownames.get(row_id), "cols": cols})
    for tname, rows in out.items():
        rows.sort(key=lambda r: r["row"])
        print(f"{tname}: {len(rows)} rows")
    (TOOL_DIR / "cache_tables.json").write_text(json.dumps(out, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
