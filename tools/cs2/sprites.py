"""Reads sprites (cache archive 8) and writes them as PNG files, with no image library needed.

`frames(group)` returns the sprite group's frames as (width, height, rgba bytes), each already placed
on the group's full canvas. `write_png(path, w, h, rgba)` saves one.

    py -I tools/cs2/sprites.py 5961 out_dir     # every frame of sprite group 5961
"""
import importlib.util, os, struct, sys, zlib
HERE = os.path.dirname(os.path.abspath(__file__))
os.environ.setdefault("JS5_CACHE", os.path.join(HERE, "..", "..", ".data", "cache", "vanilla"))
# Loaded by path: tools/spawns has a different js5.py, and a script may import both.
_spec = importlib.util.spec_from_file_location("cs2_js5", os.path.join(HERE, "js5.py"))
js5 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(js5)

_IDX8 = None


def frames(group):
    global _IDX8
    if _IDX8 is None:
        _IDX8 = js5.idx(8)
    raw = js5.read_entry(_IDX8, 8, group)
    if raw is None:
        return []
    b = js5.decompress(raw)
    n = len(b)
    count = struct.unpack(">H", b[n - 2:])[0]
    p = n - 7 - count * 8
    width, height = struct.unpack(">HH", b[p:p + 4])
    pal_size = b[p + 4] + 1
    p += 5
    xs = struct.unpack(">%dH" % count, b[p:p + 2 * count]); p += 2 * count
    ys = struct.unpack(">%dH" % count, b[p:p + 2 * count]); p += 2 * count
    ws = struct.unpack(">%dH" % count, b[p:p + 2 * count]); p += 2 * count
    hs = struct.unpack(">%dH" % count, b[p:p + 2 * count]); p += 2 * count
    p = n - 7 - count * 8 - (pal_size - 1) * 3
    palette = [0]
    for i in range(pal_size - 1):
        c = int.from_bytes(b[p + 3 * i:p + 3 * i + 3], "big")
        palette.append(c if c else 1)
    out = []
    p = 0
    for f in range(count):
        w, h = ws[f], hs[f]
        flags = b[p]; p += 1
        size = w * h
        idx = [0] * size
        alpha = [0] * size
        if flags & 1 == 0:
            idx = list(b[p:p + size]); p += size
            if flags & 2:
                alpha = list(b[p:p + size]); p += size
        else:
            for x in range(w):
                for y in range(h):
                    idx[y * w + x] = b[p]; p += 1
            if flags & 2:
                for x in range(w):
                    for y in range(h):
                        alpha[y * w + x] = b[p]; p += 1
        canvas = bytearray(width * height * 4)
        for y in range(h):
            for x in range(w):
                i = idx[y * w + x]
                if i == 0:
                    continue
                c = palette[i]
                a = alpha[y * w + x] if flags & 2 else 255
                o = ((ys[f] + y) * width + xs[f] + x) * 4
                canvas[o:o + 4] = bytes(((c >> 16) & 255, (c >> 8) & 255, c & 255, a))
        out.append((width, height, bytes(canvas)))
    return out


def write_png(path, w, h, rgba):
    rows = b"".join(b"\x00" + rgba[y * w * 4:(y + 1) * w * 4] for y in range(h))

    def chunk(kind, data):
        c = kind + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


if __name__ == "__main__":
    group, out_dir = int(sys.argv[1]), sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    for i, (w, h, rgba) in enumerate(frames(group)):
        write_png(os.path.join(out_dir, f"{group}_{i}.png"), w, h, rgba)
        print(f"{group}_{i}.png {w}x{h}")
