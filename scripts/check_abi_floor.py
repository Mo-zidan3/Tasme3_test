#!/usr/bin/env python3
"""check_abi_floor.py — CI assertion (D-012/D-013): minSdk >= max(.note.android.ident
api_level) over every .so SHIPPED in the given APK/AAB/jniLibs dir, so a future jniLibs bump
cannot regress minSdk silently. Exit 1 with a table on violation.

Usage: python3 scripts/check_abi_floor.py <minSdk> <path>  (path = APK file, AAB file, or dir)
"""
import struct
import sys
from pathlib import Path
import zipfile


def api_level_of_elf(data: bytes) -> int | None:
    if data[:4] != b"\x7fELF":
        return None
    is64 = data[4] == 2
    if is64:
        shoff = struct.unpack_from("<Q", data, 0x28)[0]
        shentsize, shnum, shstrndx = struct.unpack_from("<HHH", data, 0x3A)
    else:
        shoff = struct.unpack_from("<I", data, 0x20)[0]
        shentsize, shnum, shstrndx = struct.unpack_from("<HHH", data, 0x2E)
        shentsize = shentsize if shentsize else 40
    try:
        sh = shoff + shstrndx * shentsize
        shstr_off = (struct.unpack_from("<Q", data, sh + 0x18)[0] if is64
                     else struct.unpack_from("<I", data, sh + 0x10)[0])
        for i in range(shnum):
            sh = shoff + i * shentsize
            name_off = struct.unpack_from("<I", data, sh)[0]
            sh_off = (struct.unpack_from("<Q", data, sh + 0x18)[0] if is64
                      else struct.unpack_from("<I", data, sh + 0x10)[0])
            sh_size = (struct.unpack_from("<Q", data, sh + 0x20)[0] if is64
                       else struct.unpack_from("<I", data, sh + 0x14)[0])
            end = data.index(b"\0", shstr_off + name_off)
            name = data[shstr_off + name_off:end].decode()
            if name == ".note.android.ident":
                note = data[sh_off:sh_off + sh_size]
                namesz, descsz, _ntype = struct.unpack_from("<III", note, 0)
                desc = note[12 + ((namesz + 3) // 4) * 4:]
                return struct.unpack_from("<I", desc, 0)[0]
    except Exception:
        return None
    return None


def so_files(path: Path):
    if path.suffix in {".apk", ".aab", ".apks"}:
        with zipfile.ZipFile(path) as z:
            for n in z.namelist():
                if n.startswith("lib/") and n.endswith(".so"):
                    yield n, z.read(n)
    elif path.is_dir():
        for f in sorted(path.rglob("*.so")):
            yield str(f), f.read_bytes()
    else:
        raise SystemExit(f"unsupported path: {path}")


def main() -> int:
    min_sdk = int(sys.argv[1])
    target = Path(sys.argv[2])
    worst = 0
    rows = []
    for name, data in so_files(target):
        lvl = api_level_of_elf(data)
        if lvl is not None:
            rows.append((name, lvl))
            worst = max(worst, lvl)
    if not rows:
        print(f"no .so found in {target} — nothing to assert")
        return 0
    for name, lvl in rows:
        print(f"  {name}: api_level {lvl}")
    print(f"minSdk={min_sdk} max(api_level)={worst}")
    if worst > min_sdk:
        print(f"FAIL: a shipped .so requires API {worst} > minSdk {min_sdk}")
        return 1
    print("OK: minSdk >= max(api_level) over all shipped .so")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
