#!/usr/bin/env python3
"""Run a read-only preview command through the deployed external scan chain."""

import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid


def load_env(path):
    values = {}
    with open(path, encoding="utf-8") as stream:
        for raw in stream:
            line = raw.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip()
    return values


def request(method, url, body=None, headers=None):
    encoded = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=encoded, method=method)
    req.add_header("Accept", "application/json")
    if encoded is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    for name, value in (headers or {}).items():
        req.add_header(name, value)
    try:
        with urllib.request.urlopen(req, timeout=8) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode("utf-8"))


def main():
    env_path = os.environ.get("RELAY_ENV_FILE", "/etc/aliyun-api-relay/api.env")
    values = load_env(env_path)
    enrollment_key = values.get("RELAY_APP_ENROLLMENT_KEY", "")
    if not enrollment_key:
        raise SystemExit("RELAY_APP_ENROLLMENT_KEY is missing")

    base_url = os.environ.get("RELAY_TEST_URL", "http://127.0.0.1:18082")
    device_no = "E2E-LINUX-01"
    status, registered = request("POST", base_url + "/app/v1/device/register", {
        "deviceNo": device_no,
        "deviceModel": "deployment-smoke-test",
        "employeeNo": "E2E",
    }, {"X-Enrollment-Key": enrollment_key})
    if status != 201:
        raise SystemExit("device registration failed: " + json.dumps(registered, ensure_ascii=False))

    device_token = registered["data"]["deviceToken"]
    headers = {"Authorization": "Bearer " + device_token, "X-Device-No": device_no}
    request_id = "APP-SCAN-E2E-" + uuid.uuid4().hex[:16]
    status, created = request("POST", base_url + "/app/v1/commands", {
        "requestId": request_id,
        "action": "preview",
        "scanCode": "13799816,物料架-01-F03,备用",
        "format": "QR_CODE",
    }, headers)
    if status != 202:
        raise SystemExit("command creation failed: " + json.dumps(created, ensure_ascii=False))

    deadline = time.monotonic() + 60
    latest = created
    while time.monotonic() < deadline:
        _, latest = request("GET", base_url + "/app/v1/commands/" + request_id, headers=headers)
        state = latest.get("data", {}).get("status")
        if state in {"SUCCEEDED", "FAILED", "EXPIRED"}:
            print(json.dumps({"requestId": request_id, "status": state,
                              "result": latest.get("data", {}).get("result"),
                              "error": latest.get("data", {}).get("error")}, ensure_ascii=False))
            return 0 if state == "SUCCEEDED" else 1
        time.sleep(1)
    print(json.dumps({"requestId": request_id, "status": "TIMEOUT"}))
    return 1


if __name__ == "__main__":
    sys.exit(main())
