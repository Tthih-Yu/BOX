#!/usr/bin/env bash
set -euo pipefail

: "${RELAY_URL:=http://10.243.111.152/aliyun-relay}"
: "${RELAY_TOKEN:?RELAY_TOKEN is required}"

if curl --help all 2>/dev/null | grep -q -- "--fail-with-body"; then
  CURL_FAIL="--fail-with-body"
else
  CURL_FAIL="--fail"
fi

command_name="${1:-}"
case "$command_name" in
  health)
    curl "$CURL_FAIL" --silent --show-error --max-time 10 \
      -H "X-Relay-Token: $RELAY_TOKEN" \
      "$RELAY_URL/health"
    ;;
  send)
    json_file="${2:?Usage: relay-client.sh send payload.json}"
    curl "$CURL_FAIL" --silent --show-error --max-time 20 \
      -H "X-Relay-Token: $RELAY_TOKEN" \
      -H "Content-Type: application/json" \
      --data-binary "@$json_file" \
      "$RELAY_URL/data"
    ;;
  tasks)
    curl "$CURL_FAIL" --silent --show-error --max-time 20 \
      -H "X-Relay-Token: $RELAY_TOKEN" \
      "$RELAY_URL/tasks"
    ;;
  result)
    task_id="${2:?Usage: relay-client.sh result TASK_ID result.json}"
    json_file="${3:?Usage: relay-client.sh result TASK_ID result.json}"
    curl "$CURL_FAIL" --silent --show-error --max-time 20 \
      -H "X-Relay-Token: $RELAY_TOKEN" \
      -H "Content-Type: application/json" \
      --data-binary "@$json_file" \
      "$RELAY_URL/tasks/$task_id/result"
    ;;
  *)
    echo "Usage: $0 {health|send FILE|tasks|result TASK_ID FILE}" >&2
    exit 2
    ;;
esac
echo
