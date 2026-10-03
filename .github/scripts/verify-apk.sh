#!/usr/bin/env bash
#
# Apertus APK 完整性 / 合法性闸门 —— 在 build job 里跑, 不需要模拟器。
#
# 它回答的是"这个 APK 到底合不合法、完不完整", 不是"功能对不对":
#
#   * ZIP 容器与每个条目的 CRC           (unzip -t + python)
#   * 每个 classes*.dex 的魔数与头部自洽  (python, 不依赖外部工具)
#   * resources.arsc 存在且未压缩
#   * 4 字节对齐 / .so 页对齐 / 签名有效   (zipalign + apksigner)
#   * **APK 能装得上**: manifest 里点名的每个组件类都在 DEX 里
#
# 最后一条是"打开就闪退"事故的回归闸门:
# 清单里写了一个不存在的类名时, 编译、打包、签名全都会成功,
# 只有装到设备上、点开的那一刻才 ClassNotFoundException。
# 所以静态检查必须显式验证 "清单里点名的类 == DEX 里真实存在的类"。
#
# 用法: verify-apk.sh [apk 路径]      (不给路径就在 composeApp/build 下找)

set -uo pipefail

PACKAGE="${PACKAGE:-com.apertus.music}"
EXPECTED_ACTIVITY="${EXPECTED_ACTIVITY:-com.apertus.music/com.apertus.music.MainActivity}"
LAUNCHER_CLASS="${EXPECTED_ACTIVITY#*/}"
OUT_DIR="${OUT_DIR:-verify}"
APK="${1:-}"

mkdir -p "$OUT_DIR"

FAILED=0
ok()  { echo "  [ok] $*"; }
bad() { echo "  [!!] $*"; FAILED=1; }
sec() { echo; echo "==== $* ===="; }

# --------------------------------------------------------------------------
sec "0. 定位工具与产物"
# --------------------------------------------------------------------------
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
BUILD_TOOLS="$(find "$SDK_ROOT/build-tools" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -n1)"
AAPT2="$BUILD_TOOLS/aapt2"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
APKANALYZER="$(find "$SDK_ROOT/cmdline-tools" -maxdepth 3 -type f -name apkanalyzer -print -quit 2>/dev/null || true)"

echo "SDK_ROOT=$SDK_ROOT"
echo "build-tools=$BUILD_TOOLS"
for t in "$AAPT2" "$ZIPALIGN" "$APKSIGNER"; do
  [ -x "$t" ] || bad "缺少必需工具: $t"
done
[ -n "$APKANALYZER" ] && echo "apkanalyzer=$APKANALYZER" || echo "apkanalyzer=<未找到, 部分检查降级>"
echo "package=$PACKAGE  launcher=$EXPECTED_ACTIVITY"

if [ -z "$APK" ]; then
  APK="$(find composeApp/build/outputs/apk -name '*.apk' -print -quit 2>/dev/null || true)"
fi
echo "APK=$APK"
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  bad "找不到 APK 产物"
  echo "RESULT: FAIL"; exit 1
fi
ls -l "$APK"
ok "APK 存在 ($(stat -c%s "$APK") 字节)"

# --------------------------------------------------------------------------
sec "1. ZIP 容器完整性 (unzip -t)"
# --------------------------------------------------------------------------
if unzip -tqq "$APK" > "$OUT_DIR/unzip-t.txt" 2>&1; then
  ok "ZIP 结构完整, 无 CRC 错误 / 截断"
else
  bad "ZIP 结构损坏:"; tail -n 20 "$OUT_DIR/unzip-t.txt"
fi

# --------------------------------------------------------------------------
sec "2. aapt2 dump badging (Android 官方解析)"
# --------------------------------------------------------------------------
if [ -x "$AAPT2" ]; then
  "$AAPT2" dump badging "$APK" > "$OUT_DIR/badging.txt" 2>&1 || bad "aapt2 dump badging 失败"
  grep -E "^(package|sdkVersion|targetSdkVersion|application-label|launchable-activity|native-code)" \
    "$OUT_DIR/badging.txt" || true

  grep -q "package: name='$PACKAGE'" "$OUT_DIR/badging.txt" \
    && ok "package 名 = $PACKAGE" \
    || bad "package 名不是 $PACKAGE: $(grep -m1 '^package:' "$OUT_DIR/badging.txt" || echo '<无法解析>')"

  grep -q "launchable-activity: name='$LAUNCHER_CLASS'" "$OUT_DIR/badging.txt" \
    && ok "launcher activity = $LAUNCHER_CLASS" \
    || bad "launcher activity 不是 $LAUNCHER_CLASS: $(grep -m1 'launchable-activity' "$OUT_DIR/badging.txt" || echo '<无>')"

  grep -q "uses-permission: name='android.permission.INTERNET'" "$OUT_DIR/badging.txt" \
    && ok "声明了 INTERNET 权限" \
    || bad "没有声明 INTERNET 权限 (网络/播放会失败)"
else
  bad "aapt2 不可用, 跳过 badging"
fi

# --------------------------------------------------------------------------
sec "3. 取明文 AndroidManifest.xml (供悬空引用检查用)"
# --------------------------------------------------------------------------
MANIFEST_TXT="$OUT_DIR/AndroidManifest.xml"
if [ -n "$APKANALYZER" ] && "$APKANALYZER" manifest print "$APK" > "$MANIFEST_TXT" 2>"$OUT_DIR/apkanalyzer.err"; then
  grep -q "<manifest" "$MANIFEST_TXT" || bad "apkanalyzer manifest print 的输出里没有 <manifest>"
  ok "apkanalyzer manifest print 成功 (manifest 可解析)"
else
  MERGED="$(find composeApp/build -path '*merged_manifest*' -name 'AndroidManifest.xml' -print -quit 2>/dev/null || true)"
  if [ -n "$MERGED" ]; then
    cp "$MERGED" "$MANIFEST_TXT"
    ok "回退使用 AGP 合并清单: $MERGED"
  else
    bad "拿不到明文 manifest, 无法做悬空引用检查"
    : > "$MANIFEST_TXT"
  fi
fi

# --------------------------------------------------------------------------
sec "4. apkanalyzer apk summary"
# --------------------------------------------------------------------------
if [ -n "$APKANALYZER" ]; then
  SUMMARY="$("$APKANALYZER" apk summary "$APK" 2>&1 | tail -n1)"
  echo "  summary: $SUMMARY"
  case "$SUMMARY" in
    "$PACKAGE"*) ok "apkanalyzer 报告的包名一致" ;;
    *) bad "apkanalyzer 报告的包名不一致: $SUMMARY" ;;
  esac
fi

# --------------------------------------------------------------------------
sec "5. 对齐 (zipalign -c -v 4)"
# --------------------------------------------------------------------------
if [ -x "$ZIPALIGN" ]; then
  if "$ZIPALIGN" -c -v 4 "$APK" > "$OUT_DIR/zipalign.txt" 2>&1; then
    ok "zipalign 校验通过"
  else
    bad "zipalign 校验失败:"; tail -n 20 "$OUT_DIR/zipalign.txt"
  fi
fi

# --------------------------------------------------------------------------
sec "6. 签名 (apksigner verify)"
# --------------------------------------------------------------------------
if [ -x "$APKSIGNER" ]; then
  if "$APKSIGNER" verify --verbose --print-certs "$APK" > "$OUT_DIR/apksigner.txt" 2>&1; then
    grep -E "^(Verifies|Verified using|Signer #1 certificate DN)" "$OUT_DIR/apksigner.txt" || true
    ok "签名有效"
  else
    bad "签名校验失败:"; cat "$OUT_DIR/apksigner.txt"
  fi
fi

# --------------------------------------------------------------------------
sec "7. apkcheck.py —— dex 合法性 + 清单<->DEX 悬空引用 + 对齐/压缩"
# --------------------------------------------------------------------------
if command -v python3 >/dev/null 2>&1; then
  PY=python3
else
  PY=python
fi
"$PY" tools/apkcheck.py "$APK" \
  --manifest "$MANIFEST_TXT" \
  --expect-package "$PACKAGE" \
  --expect-activity "$LAUNCHER_CLASS" 2>&1 | tee "$OUT_DIR/apkcheck.txt"
# tee 会把 python 的退出码吃掉, 所以看 PIPESTATUS
[ "${PIPESTATUS[0]}" -eq 0 ] && ok "apkcheck.py 通过" || bad "apkcheck.py 失败"

# --------------------------------------------------------------------------
sec "8. apkanalyzer dex packages 交叉确认 launcher 类"
# --------------------------------------------------------------------------
if [ -n "$APKANALYZER" ]; then
  if "$APKANALYZER" dex packages --defined-only "$APK" > "$OUT_DIR/dex-packages.txt" 2>&1; then
    if grep -qE "^C d .* $LAUNCHER_CLASS\$" "$OUT_DIR/dex-packages.txt"; then
      ok "dex 里确实定义了 $LAUNCHER_CLASS"
    else
      bad "dex 里找不到 $LAUNCHER_CLASS (与 apkcheck.py 结论不一致时以本条为准)"
    fi
  else
    echo "  [--] apkanalyzer dex packages 不可用, 跳过 (apkcheck.py 已覆盖)"
  fi
fi

# --------------------------------------------------------------------------
sec "结果"
# --------------------------------------------------------------------------
{
  echo "package=$PACKAGE"
  echo "launcher=$EXPECTED_ACTIVITY"
  echo "apk=$APK"
  echo "bytes=$(stat -c%s "$APK")"
  echo "sha256=$(sha256sum "$APK" | cut -d' ' -f1)"
  echo "result=$([ "$FAILED" -eq 0 ] && echo PASS || echo FAIL)"
} | tee "$OUT_DIR/summary.txt"

if [ "$FAILED" -eq 0 ]; then
  echo
  echo "APK VERIFICATION PASSED: 容器 / dex / 对齐 / 签名 / 清单引用全部通过。"
else
  echo
  echo "::error title=APK verification::APK 完整性检查未通过, 见上面 [!!] 行"
fi
exit "$FAILED"
