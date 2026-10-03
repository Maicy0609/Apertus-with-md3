#!/usr/bin/env python3
"""Apertus APK 体检 —— 判断一个 APK "合不合法 / 完不完整".

用法:
    apkcheck.py <apk> [<apk> ...]
                [--manifest <明文 AndroidManifest.xml>]
                [--expect-package <id>]
                [--expect-activity <类名>]

这条闸门回答三个问题:

  1. ZIP 容器本身有没有坏块 / 截断 / CRC 错误
  2. 每个 dex 是不是**合法 dex**(魔数 + 头部自洽)
  3. Manifest 里点名的每个组件类, DEX 里到底有没有 —— "有没有悬空引用"

第 3 条是这次 "APK 打开就直接退出" 事故的直接回归闸门。
教训: AndroidManifest.xml 里写了一个并不存在的类名时, 编译、打包、签名
全部正常通过; 但 ActivityThread 反射实例化该组件时抛 ClassNotFoundException,
进程在画出第一帧之前就死了 —— 产物"看起来完全正常", 只是打不开。
所以静态检查必须显式验证 **清单里点名的类 == dex 里真实存在的类**。

对齐 / 压缩规则 (targetSdk>=30 + extractNativeLibs=false):
    lib/**/*.so        -> 4096 字节页对齐 且 STORED
    resources.arsc     -> 4 字节对齐 且 STORED
    其它 STORED 条目    -> 4 字节对齐

退出码: 0 = 全部通过, 1 = 至少一项失败。
"""
import argparse
import re
import struct
import sys
import zipfile
from pathlib import Path

PAGE = 4096

# --------------------------------------------------------------------------
# zip 层
# --------------------------------------------------------------------------

def data_offset(zf: zipfile.ZipFile, info: zipfile.ZipInfo) -> int:
    """返回条目数据的真实文件偏移 —— 对齐检查必须看这个, 不能看 header_offset."""
    zf.fp.seek(info.header_offset)
    header = zf.fp.read(30)
    nlen, elen = struct.unpack_from("<HH", header, 26)
    return info.header_offset + 30 + nlen + elen


def check_zip(path: Path, z: zipfile.ZipFile, problems: list) -> dict:
    """CRC + 对齐 + 压缩方式。返回统计信息。"""
    bad = z.testzip()
    if bad is not None:
        problems.append(f"CRC 校验失败 (zip 容器损坏): {bad}")

    names = z.namelist()
    so_files, stored, dex_files = [], 0, []
    for info in z.infolist():
        if info.filename.endswith(".dex") and info.filename.startswith("classes"):
            dex_files.append(info.filename)

        compressed = info.compress_type != 0
        if compressed:
            if info.filename.endswith((".so", ".arsc")):
                problems.append(
                    f"{info.filename} 被压缩了, 应为 STORED "
                    f"(targetSdk>=30 / extractNativeLibs=false 要求未压缩)"
                )
            continue

        stored += 1
        off = data_offset(z, info)
        if info.filename.endswith(".so"):
            so_files.append(info.filename)
            if off % PAGE:
                problems.append(
                    f"{info.filename} 页对齐失败 (offset={off}, mod4096={off % PAGE})"
                )
        elif info.filename.endswith(".arsc"):
            if off % 4:
                problems.append(f"{info.filename} 4 字节对齐失败 (offset={off})")
        elif off % 4:
            problems.append(f"{info.filename} 4 字节对齐失败 (offset={off})")

    sigs = sorted(
        n for n in names
        if n.startswith("META-INF/") and n.rsplit(".", 1)[-1] in ("SF", "RSA", "DSA", "EC")
    )
    return {
        "entries": len(names),
        "stored": stored,
        "so": so_files,
        "dex": sorted(dex_files),
        "signatures": sigs,
    }


# --------------------------------------------------------------------------
# dex 层 —— 只解析到"类名清单"为止, 不依赖任何外部工具
# --------------------------------------------------------------------------

DEX_MAGIC = b"dex\n"
DEX_HEADER_STRUCT = struct.Struct("<8sI20s20I")
DEX_HEADER_SIZE = 0x70
DEX_ENDIAN_TAG = 0x12345678


def _uleb128(data: bytes, off: int):
    result = 0
    shift = 0
    while True:
        if off >= len(data):
            raise ValueError("uleb128 越界")
        byte = data[off]
        off += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, off
        shift += 7
        if shift > 28:
            raise ValueError("uleb128 过长")


def dex_header(data: bytes) -> dict:
    if len(data) < DEX_HEADER_SIZE:
        raise ValueError(f"文件比 dex 头部还短 ({len(data)} < {DEX_HEADER_SIZE})")
    f = DEX_HEADER_STRUCT.unpack_from(data, 0)
    magic = f[0]
    if not magic.startswith(DEX_MAGIC):
        raise ValueError(f"魔数不是 dex\\n: {magic!r}")
    return {
        "magic": magic,
        "version": magic[4:7].decode("ascii", "replace"),
        "file_size": f[3],
        "header_size": f[4],
        "endian_tag": f[5],
        "string_ids_size": f[9],
        "string_ids_off": f[10],
        "type_ids_size": f[11],
        "type_ids_off": f[12],
        "class_defs_size": f[19],
        "class_defs_off": f[20],
    }


def dex_classes(data: bytes) -> tuple:
    """返回 (点号分隔的类名集合, 头部信息)。只读 string_ids / type_ids / class_defs。"""
    h = dex_header(data)
    problems = []
    if h["header_size"] != DEX_HEADER_SIZE:
        problems.append(f"header_size={h['header_size']} (应为 {DEX_HEADER_SIZE})")
    if h["endian_tag"] != DEX_ENDIAN_TAG:
        problems.append(f"endian_tag=0x{h['endian_tag']:08x} (应为 0x12345678)")
    if h["file_size"] != len(data):
        problems.append(f"头部 file_size={h['file_size']} 与实际长度 {len(data)} 不一致")

    strings = []
    for i in range(h["string_ids_size"]):
        off = struct.unpack_from("<I", data, h["string_ids_off"] + 4 * i)[0]
        _n, p = _uleb128(data, off)
        end = data.index(b"\x00", p)
        strings.append(data[p:end].decode("utf-8", "replace"))

    types = []
    for i in range(h["type_ids_size"]):
        idx = struct.unpack_from("<I", data, h["type_ids_off"] + 4 * i)[0]
        types.append(strings[idx])

    classes = set()
    for i in range(h["class_defs_size"]):
        idx = struct.unpack_from("<I", data, h["class_defs_off"] + 32 * i)[0]
        desc = types[idx]
        if desc.startswith("L") and desc.endswith(";"):
            classes.add(desc[1:-1].replace("/", "."))
    return classes, h, problems


# --------------------------------------------------------------------------
# manifest 层 —— 明文 XML (apkanalyzer manifest print / AGP 合并清单)
# --------------------------------------------------------------------------

COMPONENT_TAGS = (
    "application", "activity", "activity-alias", "service", "receiver", "provider",
)
_ELEM = re.compile(r"<(" + "|".join(COMPONENT_TAGS) + r")\b([^>]*?)/?>", re.S)
_ATTR = re.compile(r'\bandroid:(name|targetActivity)\s*=\s*"([^"]*)"')
_PKG = re.compile(r"<manifest\b[^>]*?\bpackage\s*=\s*\"([^\"]*)\"", re.S)


def manifest_components(text: str):
    """(package, [(tag, attr, 全限定类名)]) —— 相对类名按 package 补全。"""
    m = _PKG.search(text)
    pkg = m.group(1) if m else ""
    out = []
    for elem in _ELEM.finditer(text):
        tag, attrs = elem.group(1), elem.group(2)
        for attr in _ATTR.finditer(attrs):
            kind, value = attr.group(1), attr.group(2)
            if not value:
                continue
            if value.startswith("."):
                value = pkg + value
            elif "." not in value:
                value = pkg + "." + value
            out.append((tag, kind, value))
    return pkg, out


def check_manifest(path: Path, dex_classes_all: set, problems: list,
                   expect_package=None, expect_activity=None) -> dict:
    text = path.read_text(encoding="utf-8", errors="replace")
    pkg, comps = manifest_components(text)

    info = {"package": pkg, "components": len(comps)}
    if not pkg:
        problems.append(f"{path.name}: 解析不出 <manifest package=...>")
    if expect_package and pkg != expect_package:
        problems.append(f"包名不符: manifest 里是 '{pkg}', 期望 '{expect_package}'")

    # 悬空引用: 清单点名的类必须在 dex 里真实存在
    dangling = []
    for tag, kind, value in comps:
        if value not in dex_classes_all:
            dangling.append((tag, kind, value))
    if dangling:
        for tag, kind, value in dangling:
            problems.append(
                f"悬空引用: <{tag} android:{kind}=\"{value}\"> 在 DEX 里不存在 "
                f"-> 运行期 ClassNotFoundException"
            )
    else:
        print(f"    manifest 点名的 {len(comps)} 个组件类, DEX 里全部存在")

    if expect_activity and expect_activity not in dex_classes_all:
        problems.append(f"launcher 类 '{expect_activity}' 不在 DEX 里")
    if expect_activity and expect_activity not in {v for _t, _k, v in comps}:
        problems.append(f"launcher 类 '{expect_activity}' 没有被 manifest 声明")
    return info


# --------------------------------------------------------------------------

def check(path: Path, args) -> bool:
    print(f"== {path}  ({path.stat().st_size} bytes)")
    problems: list = []
    dex_classes_all: set = set()

    with zipfile.ZipFile(path) as z:
        stats = check_zip(path, z, problems)
        print(f"    条目 {stats['entries']} (STORED {stats['stored']})  "
              f"native-lib {len(stats['so'])}  签名文件 {stats['signatures'] or '<无 v1 签名文件>'}")

        if not stats["dex"]:
            problems.append("APK 里没有任何 classes*.dex")
        for name in stats["dex"]:
            data = z.read(name)
            try:
                classes, h, hdr_problems = dex_classes(data)
            except Exception as exc:  # noqa: BLE001 - 任何解析失败都算 dex 不合法
                problems.append(f"{name}: 不是合法 dex ({exc})")
                continue
            for p in hdr_problems:
                problems.append(f"{name}: {p}")
            dex_classes_all |= classes
            print(f"    {name}: dex {h['version']}  header=0x{h['header_size']:x}  "
                  f"class_defs={h['class_defs_size']}  string_ids={h['string_ids_size']}")

        if not dex_classes_all:
            problems.append("解析不出任何类, dex 层闸门形同虚设")

        # resources.arsc 必须存在 (targetSdk>=30 的硬要求)
        if "resources.arsc" not in z.namelist():
            problems.append("缺少 resources.arsc")

    if args.manifest:
        check_manifest(Path(args.manifest), dex_classes_all, problems,
                       args.expect_package, args.expect_activity)
    else:
        print("    (未提供 --manifest, 跳过 清单<->DEX 悬空引用检查)")

    print(f"    DEX 里共 {len(dex_classes_all)} 个类")
    for p in problems:
        print(f"  [!!] {p}")
    print(f"{'PASS' if not problems else 'FAIL'}  {path.name}")
    return not problems


def main() -> int:
    ap = argparse.ArgumentParser(description="APK 合法性 / 完整性体检")
    ap.add_argument("apk", nargs="+")
    ap.add_argument("--manifest", help="明文 AndroidManifest.xml (apkanalyzer manifest print 的输出)")
    ap.add_argument("--expect-package")
    ap.add_argument("--expect-activity")
    args = ap.parse_args()

    ok = all(check(Path(p), args) for p in args.apk)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
