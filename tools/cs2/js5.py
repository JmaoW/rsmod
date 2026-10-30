import bz2, gzip, zlib, struct, os, sys, pickle

CACHE = os.environ.get("JS5_CACHE", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".data", "cache", "vanilla"))
DAT = open(os.path.join(CACHE, "main_file_cache.dat2"), "rb").read()


def idx(n):
    return open(os.path.join(CACHE, f"main_file_cache.idx{n}"), "rb").read()


def read_entry(idx_bytes, archive, group):
    off = group * 6
    e = idx_bytes[off:off + 6]
    if len(e) < 6:
        return None
    length = int.from_bytes(e[0:3], "big")
    sector = int.from_bytes(e[3:6], "big")
    if length == 0:
        return None
    out = bytearray()
    chunk = 0
    while len(out) < length:
        pos = sector * 520
        big = group >= 65536
        hdr = 10 if big else 8
        h = DAT[pos:pos + hdr]
        remaining = length - len(out)
        take = min(remaining, 520 - hdr)
        out += DAT[pos + hdr:pos + hdr + take]
        sector = int.from_bytes(h[hdr - 4:hdr - 1], "big")
        chunk += 1
    return bytes(out)


def decompress(buf):
    comp = buf[0]
    length = struct.unpack(">i", buf[1:5])[0]
    if comp == 0:
        return buf[5:5 + length]
    ulen = struct.unpack(">i", buf[5:9])[0]
    data = buf[9:9 + length]
    if comp == 1:
        return bz2.decompress(b"BZh1" + data)
    if comp == 2:
        return zlib.decompress(data, 31)
    raise Exception(f"comp {comp}")


class Buf:
    def __init__(self, b):
        self.b = b
        self.p = 0

    def u8(self):
        v = self.b[self.p]; self.p += 1; return v

    def u16(self):
        v = struct.unpack(">H", self.b[self.p:self.p + 2])[0]; self.p += 2; return v

    def i32(self):
        v = struct.unpack(">i", self.b[self.p:self.p + 4])[0]; self.p += 4; return v

    def u24(self):
        v = int.from_bytes(self.b[self.p:self.p + 3], "big"); self.p += 3; return v

    def string(self):
        e = self.b.index(0, self.p)
        s = self.b[self.p:e].decode("cp1252"); self.p = e + 1; return s

    def bigsmart(self):
        if self.b[self.p] & 0x80:
            return self.i32() & 0x7FFFFFFF
        return self.u16()

    def remaining(self):
        return len(self.b) - self.p


def ref_table(archive):
    raw = read_entry(idx(255), 255, archive)
    d = Buf(decompress(raw))
    proto = d.u8()
    if proto >= 6:
        d.i32()
    flags = d.u8()
    count = d.bigsmart() if proto >= 7 else d.u16()
    ids = []
    last = 0
    for _ in range(count):
        last += d.bigsmart() if proto >= 7 else d.u16()
        ids.append(last)
    if flags & 1:
        for _ in ids: d.i32()
    for _ in ids: d.i32()  # crc
    if flags & 8:
        for _ in ids: d.i32()
    if flags & 2:
        d.p += 64 * len(ids)
    if flags & 4:
        d.p += 8 * len(ids)
    for _ in ids: d.i32()  # version
    fcounts = [d.bigsmart() if proto >= 7 else d.u16() for _ in ids]
    files = {}
    for g, fc in zip(ids, fcounts):
        fids = []
        last = 0
        for _ in range(fc):
            last += d.bigsmart() if proto >= 7 else d.u16()
            fids.append(last)
        files[g] = fids
    return files


def read_group(archive, group, fids, idxb):
    raw = read_entry(idxb, archive, group)
    if raw is None:
        return {}
    data = decompress(raw)
    n = len(fids)
    if n == 1:
        return {fids[0]: data}
    chunks = data[-1]
    tp = len(data) - 1 - chunks * n * 4
    sizes = [[0] * n for _ in range(chunks)]
    t = Buf(data); t.p = tp
    for c in range(chunks):
        acc = 0
        for f in range(n):
            acc += t.i32()
            sizes[c][f] = acc
    out = {fid: bytearray() for fid in fids}
    p = 0
    for c in range(chunks):
        for i, fid in enumerate(fids):
            out[fid] += data[p:p + sizes[c][i]]
            p += sizes[c][i]
    return {k: bytes(v) for k, v in out.items()}


def read_config_group(group):
    rt = ref_table(2)
    ix = idx(2)
    files = {}
    # configs: group id -> files in archive 2 (group is the config type; files are ids)
    return read_group(2, group, rt[group], ix)


def dec_enum(b):
    d = Buf(b)
    e = {"keytype": None, "valtype": None, "default": None, "entries": {}}
    while True:
        op = d.u8()
        if op == 0:
            break
        elif op == 1: e["keytype"] = chr(d.u8())
        elif op == 2: e["valtype"] = chr(d.u8())
        elif op == 3: e["default"] = d.string()
        elif op == 4: e["default"] = d.i32()
        elif op == 5:
            for _ in range(d.u16()):
                k = d.i32(); e["entries"][k] = d.string()
        elif op == 6:
            for _ in range(d.u16()):
                k = d.i32(); e["entries"][k] = d.i32()
        elif op == 7:
            size = d.u16(); n = d.u16()
            for _ in range(n):
                k = d.u16(); e["entries"][k] = d.string()
        elif op == 8:
            size = d.u16(); n = d.u16()
            for _ in range(n):
                k = d.u16(); e["entries"][k] = d.i32()
        else:
            e["bad_op"] = op
            break
    return e


def dec_struct(b):
    d = Buf(b)
    params = {}
    while True:
        op = d.u8()
        if op == 0:
            break
        elif op == 249:
            for _ in range(d.u8()):
                is_str = d.u8()
                k = d.u24()
                params[k] = d.string() if is_str else d.i32()
        else:
            params["bad_op"] = op
            break
    return params


if __name__ == "__main__":
    out = {}
    for name, grp, dec in (("enum", 8, dec_enum), ("struct", 34, dec_struct)):
        files = read_config_group(grp)
        out[name] = {fid: dec(b) for fid, b in files.items()}
        print(name, len(out[name]))
    pickle.dump(out, open(os.path.join(os.path.dirname(__file__), "cfg.pkl"), "wb"))
