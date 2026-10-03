#!/usr/bin/env bash
#
# 源码编码体检：每个纳入版本控制的文本文件必须是「无 BOM 的合法 UTF-8」，
# 并且不含 U+FFFD 替换字符。
#
# 为什么需要它
# ------------
# 早期批量改包名时用了 Windows PowerShell 的
# `Get-Content -Raw` + `Set-Content -Encoding UTF8` 组合：
#
#   * `Set-Content -Encoding UTF8`(PS 5.1) 会给每个文件加上 UTF-8 BOM；
#   * `Get-Content -Raw` 默认按系统 ANSI 代码页(GBK)解码，把 UTF-8 字节读成了
#     乱码，再按 UTF-8 写回去就成了永久损坏。
#
# 结果是 19 个 .kt 文件全被加上 BOM，其中 5 个里的非 ASCII 文本被写坏：
# `…` 变成 `鈥?` 并吞掉了后面的引号，`PlayerScreen.kt` 直接编译不过
# (`Syntax error: Expecting '"'.`)。这个脚本就是那次的回归闸门。
#
# 检查项刻意保守：只判断客观事实(BOM / UTF-8 合法性 / 替换字符)，
# 不做「像不像乱码」的启发式判断，避免对正常中文文本误报。
set -uo pipefail

FAILED=0
sec() { echo ""; echo "==== $* ===="; }
bad() { echo "  [!!] $*"; FAILED=1; }

# --------------------------------------------------------------------------
sec "源码编码体检 (BOM / 合法 UTF-8 / U+FFFD)"
# --------------------------------------------------------------------------
# `command -v python3` 在 Windows 上会命中 Microsoft Store 的占位 stub，
# 那个 stub 一跑就失败，所以这里直接试探解释器本身能不能用。
if ! python3 -c 'pass' > /dev/null 2>&1; then
  echo "  [--] 环境里没有可用的 python3，跳过编码体检"
else
python3 - <<'PY' || FAILED=1
import subprocess, sys

EXTS = (".kt", ".kts", ".java", ".xml", ".sh", ".bash", ".yml", ".yaml",
        ".toml", ".md", ".py", ".properties", ".bat", ".pro", ".txt")
EXTRA = {".gitattributes", ".gitignore", ".gitmodules", "gradlew"}

proc = subprocess.run(["git", "ls-files"], capture_output=True, text=True,
                      encoding="utf-8")
if proc.returncode != 0:
    print("  [--] 不是 git 工作区，跳过")
    sys.exit(0)

files = [f for f in proc.stdout.split("\n") if f]
targets = sorted(f for f in files
                 if f.endswith(EXTS) or f.rsplit("/", 1)[-1] in EXTRA)

problems = []
for f in targets:
    try:
        raw = open(f, "rb").read()
    except OSError as exc:
        problems.append((f, f"读不出来: {exc}"))
        continue
    if raw.startswith(b"\xef\xbb\xbf"):
        problems.append((f, "带 UTF-8 BOM (BOM 会跟着进源码/配置文件)"))
        continue
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        problems.append((f, f"不是合法 UTF-8: {exc}"))
        continue
    n = text.count("\ufffd")
    if n:
        problems.append((f, f"含 {n} 个 U+FFFD 替换字符 (说明有字符已经丢了)"))

print(f"  检查了 {len(targets)} 个文本文件")
if problems:
    for f, why in problems:
        print(f"  [!!] {f}: {why}")
    print(f"  [!!] {len(problems)} 个文件未通过编码体检")
    sys.exit(1)
print("  [ok] 全部是无 BOM 的合法 UTF-8，且不含替换字符")
PY
fi

# --------------------------------------------------------------------------
echo ""
if [ "$FAILED" -ne 0 ]; then
  echo "::error title=Encoding::源码编码体检未通过, 见上面 [!!] 行"
  exit 1
fi
echo "==== 编码体检通过 ===="
exit 0
