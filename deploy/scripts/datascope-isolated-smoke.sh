#!/usr/bin/env sh
# DataScope 隔离环境只读烟雾验收：认证、Session、角色接口和旧扫码协议。
# 除登录创建测试 Session 外不执行业务写操作。
set -eu

: "${DATASCOPE_SMOKE_CONFIRM:?请设置 DATASCOPE_SMOKE_CONFIRM=I_AM_USING_AN_ISOLATED_ENVIRONMENT}"
if [ "$DATASCOPE_SMOKE_CONFIRM" != "I_AM_USING_AN_ISOLATED_ENVIRONMENT" ]; then
  echo "拒绝执行：必须明确确认使用隔离环境" >&2
  exit 2
fi

: "${DATASCOPE_BASE_URL:?需要设置 DATASCOPE_BASE_URL，例如 http://127.0.0.1:18080/api}"
: "${DATASCOPE_TEST_USERNAME:?需要设置隔离环境测试账号 DATASCOPE_TEST_USERNAME}"
: "${DATASCOPE_TEST_PASSWORD:?需要设置 DATASCOPE_TEST_PASSWORD}"

command -v curl >/dev/null 2>&1 || { echo "缺少 curl" >&2; exit 127; }
command -v jq >/dev/null 2>&1 || { echo "缺少 jq" >&2; exit 127; }

BASE_URL=${DATASCOPE_BASE_URL%/}
TMP_DIR=$(mktemp -d)
cleanup() {
  rm -f "$TMP_DIR/ready.json" "$TMP_DIR/no-token.json" "$TMP_DIR/login.json" \
    "$TMP_DIR/tasks.json" "$TMP_DIR/device.json" "$TMP_DIR/scan-preview.json"
  rmdir "$TMP_DIR" 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM

request_code() {
  method=$1
  url=$2
  output=$3
  shift 3
  curl --silent --show-error --connect-timeout 5 --max-time 20 \
    --request "$method" --output "$output" --write-out '%{http_code}' "$@" "$url"
}

expect_code() {
  actual=$1
  expected=$2
  label=$3
  if [ "$actual" != "$expected" ]; then
    echo "失败：$label，期望 HTTP $expected，实际 $actual" >&2
    exit 4
  fi
  echo "通过：$label (HTTP $actual)"
}

READY_BODY="$TMP_DIR/ready.json"
code=$(request_code GET "$BASE_URL/health/ready" "$READY_BODY")
expect_code "$code" 200 "健康检查"

NO_TOKEN_BODY="$TMP_DIR/no-token.json"
code=$(request_code GET "$BASE_URL/tasks" "$NO_TOKEN_BODY")
expect_code "$code" 401 "业务接口无 Token 拒绝"

LOGIN_BODY="$TMP_DIR/login.json"
login_payload=$(jq -cn --arg username "$DATASCOPE_TEST_USERNAME" --arg password "$DATASCOPE_TEST_PASSWORD" \
  '{username:$username,password:$password}')
code=$(request_code POST "$BASE_URL/auth/login" "$LOGIN_BODY" \
  --header 'Content-Type: application/json' --data "$login_payload")
expect_code "$code" 200 "测试账号登录"
token=$(jq -r '.data.token // empty' "$LOGIN_BODY")
if [ -z "$token" ]; then
  echo "失败：登录响应没有 data.token" >&2
  exit 4
fi

TASK_BODY="$TMP_DIR/tasks.json"
code=$(request_code GET "$BASE_URL/tasks" "$TASK_BODY" --header "Authorization: Bearer $token")
expect_code "$code" 200 "Session 恢复与范围任务列表"

DEVICE_BODY="$TMP_DIR/device.json"
device_payload='{"deviceNo":"DATASCOPE-SMOKE","deviceModel":"smoke-test","employeeNo":"TEST"}'
code=$(request_code POST "$BASE_URL/device/login" "$DEVICE_BODY" \
  --header 'Content-Type: application/json' --data "$device_payload")
expect_code "$code" 200 "旧设备登记免 Token"

# 使用不存在的测试码做 preview，只验证设备认证链没有返回 401/403，不创建 Task。
SCAN_BODY="$TMP_DIR/scan-preview.json"
scan_payload='{"scanCode":"__DATASCOPE_SMOKE_NON_EXISTENT__","deviceNo":"DATASCOPE-SMOKE"}'
code=$(request_code POST "$BASE_URL/scan/preview" "$SCAN_BODY" \
  --header 'Content-Type: application/json' \
  --header 'X-Employee-No: TEST' \
  --header 'X-Device-No: DATASCOPE-SMOKE' \
  --data "$scan_payload")
if [ "$code" = "401" ] || [ "$code" = "403" ]; then
  echo "失败：旧扫码协议被认证链拒绝 (HTTP $code)" >&2
  exit 4
fi
echo "通过：旧扫码协议进入业务解析链 (HTTP $code)"

echo "DataScope 隔离环境烟雾验收通过；未执行业务写操作"
