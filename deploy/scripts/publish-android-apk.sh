#!/usr/bin/env bash
# 安卓 APP 远程更新 - APK 发布脚本
#
# 作用：把一个新构建好的 APK 发布到服务器的更新目录，并原子生成 latest.json。
# APP 会请求 <服务器>/updates/android/latest.json 判断是否有新版本。
#
# 用法：
#   ./deploy/scripts/publish-android-apk.sh <APK路径> <versionCode> <versionName> ["更新说明"]
#
# 示例：
#   ./deploy/scripts/publish-android-apk.sh \
#     ./upload/APTIV扫码枪-v1.3.1-debug.apk \
#     6 \
#     1.3.1 \
#     "优化扫码速度并修复显示问题"
set -euo pipefail

# 更新目录基准路径（项目部署在 /opt/apps/material-pull）
UPDATES_DIR="${UPDATES_DIR:-/opt/apps/material-pull/apk-updates}"
# APP 通过 nginx 访问的公开前缀（同源、同端口，仅路径不同）
PUBLIC_PREFIX="/updates/android"

err() { echo "错误：$*" >&2; exit 1; }

# ---- 参数校验 ----
if [[ $# -lt 3 ]]; then
  err "用法：$0 <APK路径> <versionCode> <versionName> [\"更新说明\"]"
fi

APK_SRC="$1"
VERSION_CODE="$2"
VERSION_NAME="$3"
RELEASE_NOTES="${4:-}"

[[ -f "$APK_SRC" ]] || err "找不到 APK 文件：$APK_SRC"
[[ "$VERSION_CODE" =~ ^[0-9]+$ ]] || err "versionCode 必须是正整数，收到：$VERSION_CODE"
[[ -n "$VERSION_NAME" ]] || err "versionName 不能为空"

command -v sha256sum >/dev/null 2>&1 || err "缺少 sha256sum 命令"

mkdir -p "$UPDATES_DIR"

# ---- 计算发布文件名（以 versionName 命名，稳定可读）----
APK_NAME="aptiv-${VERSION_NAME}.apk"
APK_DEST="$UPDATES_DIR/$APK_NAME"

echo "== 复制 APK =="
echo "  源:   $APK_SRC"
echo "  目标: $APK_DEST"
cp -f "$APK_SRC" "$APK_DEST"

# ---- 计算校验信息 ----
SHA256="$(sha256sum "$APK_DEST" | awk '{print $1}')"
FILE_SIZE="$(stat -c '%s' "$APK_DEST")"
PUBLISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "== 校验信息 =="
echo "  versionCode: $VERSION_CODE"
echo "  versionName: $VERSION_NAME"
echo "  fileSize:    $FILE_SIZE"
echo "  sha256:      $SHA256"

# ---- 原子生成 latest.json ----
# 先写临时文件再 mv，避免设备恰好读到写了一半的文件。
TMP_JSON="$(mktemp "$UPDATES_DIR/.latest.json.XXXXXX")"
trap 'rm -f "$TMP_JSON"' EXIT

# 更新说明做最小 JSON 转义（反斜杠、双引号、换行）
escape_json() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/\\n}"
  printf '%s' "$s"
}
NOTES_ESCAPED="$(escape_json "$RELEASE_NOTES")"

cat > "$TMP_JSON" <<JSON
{
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION_NAME",
  "apkUrl": "$PUBLIC_PREFIX/$APK_NAME",
  "fileName": "$APK_NAME",
  "fileSize": $FILE_SIZE,
  "sha256": "$SHA256",
  "releaseNotes": "$NOTES_ESCAPED",
  "publishedAt": "$PUBLISHED_AT"
}
JSON

mv -f "$TMP_JSON" "$UPDATES_DIR/latest.json"
trap - EXIT

# ---- 放行 nginx(www-data) 读取权限 ----
chmod o+rX "$UPDATES_DIR/latest.json" "$APK_DEST" 2>/dev/null || true

echo
echo "发布完成。"
echo "  latest.json -> $UPDATES_DIR/latest.json"
echo "  APK         -> $APK_DEST"
echo
echo "自测："
echo "  curl -fsS http://127.0.0.1$PUBLIC_PREFIX/latest.json"
echo "  curl -I   http://127.0.0.1$PUBLIC_PREFIX/$APK_NAME"
