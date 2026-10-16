import struct, gzip, bz2, sys, os

CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".data", "cache", "vanilla")
dat = open(os.path.join(CACHE, "main_file_cache.dat2"), "rb").read()
_idx = {}


def idx(n):
    if n not in _idx:
        _idx[n] = open(os.path.join(CACHE, f"main_file_cache.idx{n}"), "rb").read()
    return _idx[n]


def read_raw(index, gid):
    e = idx(index)[gid * 6 : gid * 6 + 6]
    if len(e) < 6:
        return None
    size = int.from_bytes(e[:3], "big")
    sector = int.from_bytes(e[3:], "big")
    if size == 0:
        return None
    out = b""
    part = 0
    while len(out) < size:
        base = sector * 520
        if gid > 65535:
            hdr = dat[base : base + 10]
            g = struct.unpack(">IHI", hdr[:10])[0] if False else int.from_bytes(hdr[:4], "big")
            p = int.from_bytes(hdr[4:6], "big")
            nxt = int.from_bytes(hdr[6:9], "big")
            body = dat[base + 10 : base + 520]
            hl = 10
        else:
            hdr = dat[base : base + 8]
            p = int.from_bytes(hdr[2:4], "big")
            nxt = int.from_bytes(hdr[4:7], "big")
            body = dat[base + 8 : base + 520]
        take = min(size - len(out), len(body))
        out += body[:take]
        sector = nxt
        part += 1
    return out


def container(raw):
    comp = raw[0]
    length = int.from_bytes(raw[1:5], "big")
    if comp == 0:
        return raw[5 : 5 + length]
    ulen = int.from_bytes(raw[5:9], "big")
    data = raw[9 : 9 + length]
    if comp == 1:
        return bz2.decompress(b"BZh1" + data)
    return gzip.decompress(data)


def read_group(index, gid):
    raw = read_raw(index, gid)
    return None if raw is None else container(raw)


def ref_table(index):
    d = read_group(255, index)
    proto = d[0]
    pos = 1
    if proto >= 6:
        pos += 4
    flags = d[pos]
    pos += 1

    def big(p):
        if proto >= 7:
            if d[p] & 0x80:
                return int.from_bytes(d[p:p+4], "big") & 0x7FFFFFFF, p + 4
            return int.from_bytes(d[p:p+2], "big"), p + 2
        return int.from_bytes(d[p:p+2], "big"), p + 2

    count, pos = big(pos)
    ids = []
    acc = 0
    for _ in range(count):
        v, pos = big(pos)
        acc += v
        ids.append(acc)
    if flags & 1:
        pos += 4 * count
    pos += 4 * count
    if flags & 8:
        pos += 4 * count
    if flags & 2:
        pos += 64 * count
    if flags & 4:
        pos += 8 * count
    pos += 4 * count
    fcounts = []
    for _ in range(count):
        v, pos = big(pos)
        fcounts.append(v)
    files = {}
    for gi, g in enumerate(ids):
        acc = 0
        fl = []
        for _ in range(fcounts[gi]):
            v, pos = big(pos)
            acc += v
            fl.append(acc)
        files[g] = fl
    return files


def split_files(data, file_ids):
    n = len(file_ids)
    if n == 1:
        return {file_ids[0]: data}
    chunks = data[-1]
    pos = len(data) - 1 - chunks * n * 4
    sizes = [[0] * n for _ in range(chunks)]
    for c in range(chunks):
        acc = 0
        for f in range(n):
            delta = struct.unpack(">i", data[pos : pos + 4])[0]
            pos += 4
            acc += delta
            sizes[c][f] = acc
    out = {fid: b"" for fid in file_ids}
    off = 0
    for c in range(chunks):
        for f in range(n):
            out[file_ids[f]] += data[off : off + sizes[c][f]]
            off += sizes[c][f]
    return out
