#!/usr/bin/env python3
"""Company Linux worker for external Android scan commands."""

import json
import os
import signal
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

RELAY_URL = os.environ.get("SCAN_RELAY_URL", "http://10.243.111.152/aliyun-relay").rstrip("/")
RELAY_TOKEN = os.environ.get("SCAN_RELAY_TOKEN", "")
INTERNAL_API = os.environ.get("SCAN_INTERNAL_API", "http://127.0.0.1:8080/api").rstrip("/")
INTERNAL_DEVICE_KEY = os.environ.get("SCAN_INTERNAL_DEVICE_KEY", "")
WORKER_ID = os.environ.get("SCAN_WORKER_ID", socket.gethostname())[:100]
LONG_POLL_SECONDS = min(max(int(os.environ.get("SCAN_LONG_POLL_SECONDS", "15")), 1), 15)
RELAY_TIMEOUT_SECONDS = max(int(os.environ.get("SCAN_RELAY_TIMEOUT_SECONDS", "20")), LONG_POLL_SECONDS + 3)
INTERNAL_TIMEOUT_SECONDS = min(max(int(os.environ.get("SCAN_INTERNAL_TIMEOUT_SECONDS", "20")), 5), 25)
IDLE_ERROR_SECONDS = min(max(int(os.environ.get("SCAN_ERROR_RETRY_SECONDS", "5")), 1), 60)
VALID_ACTIONS = {"preview", "empty", "receive", "exception"}
STOP_REQUESTED = False


def log(event, **fields):
    record = {"time": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "event": event}
    record.update(fields)
    print(json.dumps(record, ensure_ascii=False, separators=(",", ":")), flush=True)


def http_json(method, url, body, headers, timeout):
    encoded = None if body is None else json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode()
    request = urllib.request.Request(url, data=encoded, method=method)
    request.add_header("Accept", "application/json")
    request.add_header("X-Request-Id", "WORKER-" + str(int(time.time() * 1000)))
    if encoded is not None:
        request.add_header("Content-Type", "application/json; charset=utf-8")
    for name, value in headers.items():
        if value:
            request.add_header(name, value)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            return response.status, json.loads(raw.decode()) if raw else {}
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            payload = json.loads(raw.decode()) if raw else {}
        except (UnicodeDecodeError, json.JSONDecodeError):
            payload = {"error": "invalid_error_response"}
        raise RemoteHttpError(error.code, payload) from error


class RemoteHttpError(Exception):
    def __init__(self, status, payload):
        self.status = status
        self.payload = payload
        message = payload.get("message") or payload.get("error") or f"HTTP {status}"
        super().__init__(str(message))


def relay_headers():
    return {"X-Relay-Token": RELAY_TOKEN, "X-Worker-Id": WORKER_ID}


def claim_command():
    query = urllib.parse.urlencode({"waitSeconds": LONG_POLL_SECONDS})
    _, response = http_json("POST", f"{RELAY_URL}/worker/commands/claim?{query}", {}, relay_headers(), RELAY_TIMEOUT_SECONDS)
    if not response.get("success"):
        raise RuntimeError(response.get("error", "claim_failed"))
    return response.get("data")


def execute_internal(command):
    action = str(command.get("action", "")).lower()
    request_id = str(command.get("requestId", ""))
    payload = command.get("payload")
    if action not in VALID_ACTIONS or not request_id or not isinstance(payload, dict):
        raise PermanentCommandError("invalid_command_payload")
    body = dict(payload)
    body["idempotencyKey"] = request_id
    headers = {
        "X-Idempotency-Key": request_id,
        "X-Device-Key": INTERNAL_DEVICE_KEY,
        "X-Device-No": str(command.get("deviceNo", "")),
        "X-Employee-No": str(command.get("employeeNo", "")),
    }
    try:
        _, response = http_json("POST", f"{INTERNAL_API}/scan/{action}", body, headers, INTERNAL_TIMEOUT_SECONDS)
        return response
    except RemoteHttpError as error:
        message = error.payload.get("message") or error.payload.get("error") or str(error)
        if error.status == 429 or error.status >= 500:
            raise RetryableCommandError(str(message)) from error
        raise PermanentCommandError(str(message)) from error
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise RetryableCommandError(type(error).__name__) from error


class RetryableCommandError(Exception):
    pass


class PermanentCommandError(Exception):
    pass


def report(command_id, operation, body):
    url = f"{RELAY_URL}/worker/commands/{urllib.parse.quote(command_id, safe='')}/{operation}"
    http_json("POST", url, body, relay_headers(), RELAY_TIMEOUT_SECONDS)


def report_with_retry(command_id, operation, body):
    last_error = None
    for attempt in range(1, 4):
        try:
            report(command_id, operation, body)
            return
        except (RemoteHttpError, urllib.error.URLError, TimeoutError, OSError) as error:
            last_error = error
            log("result_report_retry", requestId=command_id, operation=operation, attempt=attempt,
                error=type(error).__name__)
            if attempt < 3:
                time.sleep(attempt)
    raise last_error


def process_one():
    command = claim_command()
    if not command:
        return False
    request_id = str(command.get("requestId", ""))
    action = str(command.get("action", ""))
    log("command_claimed", requestId=request_id, action=action, attempt=command.get("attemptCount"))
    try:
        result = execute_internal(command)
        report_with_retry(request_id, "complete", {"result": result})
        log("command_succeeded", requestId=request_id, action=action)
    except RetryableCommandError as error:
        report_with_retry(request_id, "fail", {"error": str(error), "retryable": True})
        log("command_retryable_failure", requestId=request_id, action=action, error=str(error))
    except PermanentCommandError as error:
        report_with_retry(request_id, "fail", {"error": str(error), "retryable": False})
        log("command_failed", requestId=request_id, action=action, error=str(error))
    return True


def validate_config():
    if len(RELAY_TOKEN) < 32:
        raise SystemExit("SCAN_RELAY_TOKEN must contain at least 32 characters")
    if len(INTERNAL_DEVICE_KEY) < 32:
        raise SystemExit("SCAN_INTERNAL_DEVICE_KEY must contain at least 32 characters")
    if not RELAY_URL.startswith("http://") and not RELAY_URL.startswith("https://"):
        raise SystemExit("SCAN_RELAY_URL must be HTTP or HTTPS")
    if not INTERNAL_API.startswith("http://127.0.0.1:") and not INTERNAL_API.startswith("http://localhost:"):
        raise SystemExit("SCAN_INTERNAL_API must use loopback HTTP")


def request_stop(signum, _frame):
    global STOP_REQUESTED
    STOP_REQUESTED = True
    log("shutdown_requested", signal=signum)


def main():
    validate_config()
    signal.signal(signal.SIGTERM, request_stop)
    signal.signal(signal.SIGINT, request_stop)
    log("worker_started", workerId=WORKER_ID, relayUrl=RELAY_URL, internalApi=INTERNAL_API)
    while not STOP_REQUESTED:
        try:
            process_one()
        except RemoteHttpError as error:
            log("relay_http_error", status=error.status, error=str(error))
            time.sleep(IDLE_ERROR_SECONDS)
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            log("relay_connection_error", error=type(error).__name__)
            time.sleep(IDLE_ERROR_SECONDS)
        except Exception as error:
            log("unexpected_error", error=type(error).__name__)
            time.sleep(IDLE_ERROR_SECONDS)
    log("worker_stopped")


if __name__ == "__main__":
    main()
