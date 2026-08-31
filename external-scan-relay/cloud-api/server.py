#!/usr/bin/env python3
"""Lightweight external Android scan relay backed by SQLite."""

import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import threading
import time
import uuid
from contextlib import closing
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HOST = os.environ.get("RELAY_API_HOST", "127.0.0.1")
PORT = int(os.environ.get("RELAY_API_PORT", "18082"))
API_TOKEN = os.environ.get("RELAY_API_TOKEN", "")
ENROLLMENT_KEY = os.environ.get("RELAY_APP_ENROLLMENT_KEY", "")
DB_PATH = os.environ.get("RELAY_API_DB", "/var/lib/aliyun-api-relay/relay.db")
MAX_BODY = int(os.environ.get("RELAY_API_MAX_BODY", "65536"))
RESULT_MAX_BODY = int(os.environ.get("RELAY_API_RESULT_MAX_BODY", "262144"))
LEASE_SECONDS = min(max(int(os.environ.get("RELAY_COMMAND_LEASE_SECONDS", "30")), 10), 300)
COMMAND_TTL_SECONDS = min(max(int(os.environ.get("RELAY_COMMAND_TTL_SECONDS", "120")), 30), 3600)
MAX_ATTEMPTS = min(max(int(os.environ.get("RELAY_COMMAND_MAX_ATTEMPTS", "3")), 1), 10)
MAX_LONG_POLL_SECONDS = min(max(int(os.environ.get("RELAY_MAX_LONG_POLL_SECONDS", "15")), 1), 15)
DEVICE_RATE_LIMIT = min(max(int(os.environ.get("RELAY_DEVICE_RATE_LIMIT", "30")), 1), 600)
DB_LOCK = threading.RLock()
COMMAND_AVAILABLE = threading.Condition()
RATE_LOCK = threading.Lock()
RATE_BUCKETS = {}
VALID_ACTIONS = {"preview", "empty", "receive", "exception"}
REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$")


def utc_now():
    return datetime.now(timezone.utc)


def to_iso(value):
    return value.astimezone(timezone.utc).isoformat(timespec="milliseconds")


def now_iso():
    return to_iso(utc_now())


def token_hash(token):
    return hashlib.sha256(token.encode()).hexdigest()


def secure_equals(left, right):
    return bool(left and right) and hmac.compare_digest(left.encode(), right.encode())


def json_text(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def parse_json(value):
    return json.loads(value) if value else None


def connect_db():
    db = sqlite3.connect(DB_PATH, timeout=5.0)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA busy_timeout=5000")
    db.execute("PRAGMA foreign_keys=ON")
    return db


def init_db():
    os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)
    with DB_LOCK, closing(connect_db()) as db:
        db.execute("PRAGMA journal_mode=WAL")
        db.executescript("""
        CREATE TABLE IF NOT EXISTS received_data (
          id TEXT PRIMARY KEY, received_at TEXT NOT NULL, source TEXT NOT NULL, payload TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS tasks (
          id TEXT PRIMARY KEY, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
          status TEXT NOT NULL, payload TEXT NOT NULL, result TEXT
        );
        CREATE TABLE IF NOT EXISTS device_credentials (
          device_no TEXT PRIMARY KEY, token_hash TEXT NOT NULL, device_model TEXT NOT NULL DEFAULT '',
          employee_no TEXT NOT NULL DEFAULT '', enabled INTEGER NOT NULL DEFAULT 1,
          created_at TEXT NOT NULL, updated_at TEXT NOT NULL, last_seen_at TEXT
        );
        CREATE TABLE IF NOT EXISTS scan_commands (
          request_id TEXT PRIMARY KEY, device_no TEXT NOT NULL, employee_no TEXT NOT NULL DEFAULT '',
          action TEXT NOT NULL, payload TEXT NOT NULL, status TEXT NOT NULL,
          result TEXT, error TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
          expires_at TEXT NOT NULL, lease_owner TEXT, lease_until TEXT,
          attempt_count INTEGER NOT NULL DEFAULT 0, completed_at TEXT,
          FOREIGN KEY(device_no) REFERENCES device_credentials(device_no)
        );
        CREATE INDEX IF NOT EXISTS idx_scan_queue ON scan_commands(status, created_at);
        CREATE INDEX IF NOT EXISTS idx_scan_device ON scan_commands(device_no, created_at DESC);
        CREATE INDEX IF NOT EXISTS idx_scan_lease ON scan_commands(status, lease_until);
        """)
        db.commit()


def command_view(row, include_payload=False):
    value = {
        "requestId": row["request_id"], "action": row["action"], "status": row["status"],
        "attemptCount": row["attempt_count"], "createdAt": row["created_at"],
        "updatedAt": row["updated_at"], "expiresAt": row["expires_at"],
        "result": parse_json(row["result"]), "error": row["error"],
    }
    if include_payload:
        value.update({"deviceNo": row["device_no"], "employeeNo": row["employee_no"],
                      "payload": parse_json(row["payload"]), "leaseUntil": row["lease_until"]})
    return value


def expire_and_requeue(db, current):
    db.execute("UPDATE scan_commands SET status='EXPIRED',updated_at=?,lease_owner=NULL,lease_until=NULL "
               "WHERE status IN ('QUEUED','LEASED') AND expires_at<=?", (current, current))
    db.execute("UPDATE scan_commands SET status='QUEUED',updated_at=?,lease_owner=NULL,lease_until=NULL "
               "WHERE status='LEASED' AND lease_until<=? AND expires_at>? AND attempt_count<?",
               (current, current, current, MAX_ATTEMPTS))
    db.execute("UPDATE scan_commands SET status='FAILED',updated_at=?,completed_at=?,"
               "error=COALESCE(error,'maximum_attempts_exceeded'),lease_owner=NULL,lease_until=NULL "
               "WHERE status='LEASED' AND lease_until<=? AND attempt_count>=?",
               (current, current, current, MAX_ATTEMPTS))


def claim_command(worker_id):
    timestamp = utc_now()
    current = to_iso(timestamp)
    lease_until = to_iso(timestamp + timedelta(seconds=LEASE_SECONDS))
    with DB_LOCK, closing(connect_db()) as db:
        db.execute("BEGIN IMMEDIATE")
        expire_and_requeue(db, current)
        row = db.execute("SELECT request_id FROM scan_commands WHERE status='QUEUED' AND expires_at>? "
                         "AND attempt_count<? ORDER BY created_at LIMIT 1", (current, MAX_ATTEMPTS)).fetchone()
        if row is None:
            db.commit()
            return None
        changed = db.execute("UPDATE scan_commands SET status='LEASED',lease_owner=?,lease_until=?,"
                             "attempt_count=attempt_count+1,updated_at=? WHERE request_id=? AND status='QUEUED'",
                             (worker_id, lease_until, current, row["request_id"]))
        if changed.rowcount != 1:
            db.rollback()
            return None
        claimed = db.execute("SELECT * FROM scan_commands WHERE request_id=?", (row["request_id"],)).fetchone()
        db.commit()
        return claimed


def rate_allowed(device_no):
    minute = int(time.time() // 60)
    with RATE_LOCK:
        saved_minute, count = RATE_BUCKETS.get(device_no, (minute, 0))
        if saved_minute != minute:
            count = 0
        if count >= DEVICE_RATE_LIMIT:
            return False
        RATE_BUCKETS[device_no] = (minute, count + 1)
        return True


class Handler(BaseHTTPRequestHandler):
    server_version = "ExternalScanRelay/2.0"

    def log_message(self, fmt, *args):
        print(json_text({"time": now_iso(), "remote": self.client_address[0], "message": fmt % args}), flush=True)

    def send_json(self, status, value, request_id=None, headers=None):
        body = json_text(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Request-Id", request_id or str(uuid.uuid4()))
        for name, header_value in (headers or {}).items():
            self.send_header(name, header_value)
        self.end_headers()
        self.wfile.write(body)

    def read_json(self, limit=None):
        raw_length = self.headers.get("Content-Length")
        if raw_length is None:
            raise ValueError("content_length_required")
        try:
            length = int(raw_length)
        except ValueError as exc:
            raise ValueError("invalid_content_length") from exc
        if length < 0 or length > (limit or MAX_BODY):
            raise ValueError("body_too_large")
        value = json.loads(self.rfile.read(length).decode())
        if not isinstance(value, dict):
            raise ValueError("json_object_required")
        return value

    def bearer(self):
        value = self.headers.get("Authorization", "")
        return value[7:] if value.startswith("Bearer ") else ""

    def require_worker(self, request_id):
        if secure_equals(self.bearer(), API_TOKEN):
            return True
        self.send_json(401, {"success": False, "error": "unauthorized"}, request_id)
        return False

    def authenticate_device(self, request_id):
        device_no, token = self.headers.get("X-Device-No", "").strip(), self.bearer()
        with DB_LOCK, closing(connect_db()) as db:
            row = db.execute("SELECT device_no,employee_no,token_hash FROM device_credentials "
                             "WHERE device_no=? AND enabled=1", (device_no,)).fetchone() if device_no else None
            if row is None or not secure_equals(token_hash(token), row["token_hash"]):
                self.send_json(401, {"success": False, "error": "device_unauthorized"}, request_id)
                return None
            db.execute("UPDATE device_credentials SET last_seen_at=? WHERE device_no=?", (now_iso(), device_no))
            db.commit()
        if not rate_allowed(device_no):
            self.send_json(429, {"success": False, "error": "rate_limited"}, request_id, {"Retry-After": "60"})
            return None
        return row

    def do_GET(self):
        parsed, request_id = urlparse(self.path), self.headers.get("X-Request-Id") or str(uuid.uuid4())
        if parsed.path == "/health":
            self.send_json(200, {"success": True, "status": "UP", "service": "external-scan-relay", "time": now_iso()}, request_id)
        elif parsed.path.startswith("/app/v1/commands/"):
            device = self.authenticate_device(request_id)
            if device is not None:
                self.get_command(parsed.path[len("/app/v1/commands/"):], device, request_id)
        elif parsed.path == "/app/v1/commands":
            device = self.authenticate_device(request_id)
            if device is not None:
                self.list_commands(parsed, device, request_id)
        elif parsed.path == "/api/tasks" and self.require_worker(request_id):
            self.legacy_tasks(parsed, request_id)
        else:
            self.send_json(404, {"success": False, "error": "not_found"}, request_id)

    def do_POST(self):
        parsed, request_id = urlparse(self.path), self.headers.get("X-Request-Id") or str(uuid.uuid4())
        if parsed.path == "/app/v1/device/register":
            self.register_device(request_id)
        elif parsed.path == "/app/v1/commands":
            device = self.authenticate_device(request_id)
            if device is not None:
                self.create_command(device, request_id)
        elif parsed.path == "/worker/v1/commands/claim" and self.require_worker(request_id):
            self.claim(parsed, request_id)
        else:
            match = re.fullmatch(r"/worker/v1/commands/([^/]+)/(renew|complete|fail)", parsed.path)
            if match and self.require_worker(request_id):
                self.worker_update(match.group(1), match.group(2), request_id)
            elif (parsed.path in ("/api/data", "/api/tasks") or
                  (parsed.path.startswith("/api/tasks/") and parsed.path.endswith("/result"))) and self.require_worker(request_id):
                self.legacy_post(parsed.path, request_id)
            elif not match:
                self.send_json(404, {"success": False, "error": "not_found"}, request_id)

    def body_or_error(self, request_id, limit=None):
        try:
            return self.read_json(limit)
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            self.send_json(400, {"success": False, "error": str(exc)}, request_id)
            return None

    def register_device(self, request_id):
        if not secure_equals(self.headers.get("X-Enrollment-Key", ""), ENROLLMENT_KEY):
            self.send_json(401, {"success": False, "error": "invalid_enrollment_key"}, request_id)
            return
        body = self.body_or_error(request_id)
        if body is None:
            return
        device_no = str(body.get("deviceNo", "")).strip()
        if not device_no or len(device_no) > 100:
            self.send_json(400, {"success": False, "error": "invalid_device_no"}, request_id)
            return
        token, current = secrets.token_urlsafe(32), now_iso()
        with DB_LOCK, closing(connect_db()) as db:
            db.execute("INSERT INTO device_credentials(device_no,token_hash,device_model,employee_no,enabled,created_at,updated_at) "
                       "VALUES(?,?,?,?,1,?,?) ON CONFLICT(device_no) DO UPDATE SET token_hash=excluded.token_hash,"
                       "device_model=excluded.device_model,employee_no=excluded.employee_no,enabled=1,updated_at=excluded.updated_at",
                       (device_no, token_hash(token), str(body.get("deviceModel", ""))[:200],
                        str(body.get("employeeNo", ""))[:50], current, current))
            db.commit()
        self.send_json(201, {"success": True, "data": {"deviceNo": device_no, "deviceToken": token}}, request_id)

    def create_command(self, device, request_id):
        body = self.body_or_error(request_id)
        if body is None:
            return
        command_id = str(body.get("requestId", "")).strip()
        action, scan_code = str(body.get("action", "")).lower().strip(), str(body.get("scanCode", ""))
        if not REQUEST_ID_PATTERN.fullmatch(command_id):
            self.send_json(400, {"success": False, "error": "invalid_request_id"}, request_id); return
        if action not in VALID_ACTIONS:
            self.send_json(400, {"success": False, "error": "invalid_action"}, request_id); return
        if not scan_code.strip() or len(scan_code) > 4096:
            self.send_json(400, {"success": False, "error": "invalid_scan_code"}, request_id); return
        payload = dict(body)
        payload.update({"requestId": command_id, "action": action, "deviceNo": device["device_no"],
                        "employeeNo": device["employee_no"]})
        encoded, timestamp = json_text(payload), utc_now()
        current, expires = to_iso(timestamp), to_iso(timestamp + timedelta(seconds=COMMAND_TTL_SECONDS))
        with DB_LOCK, closing(connect_db()) as db:
            existing = db.execute("SELECT * FROM scan_commands WHERE request_id=?", (command_id,)).fetchone()
            if existing:
                if existing["device_no"] != device["device_no"] or existing["action"] != action or existing["payload"] != encoded:
                    self.send_json(409, {"success": False, "error": "idempotency_conflict"}, request_id); return
                self.send_json(200, {"success": True, "data": command_view(existing)}, request_id); return
            db.execute("INSERT INTO scan_commands(request_id,device_no,employee_no,action,payload,status,created_at,updated_at,expires_at) "
                       "VALUES(?,?,?,?,?,'QUEUED',?,?,?)", (command_id, device["device_no"], device["employee_no"],
                       action, encoded, current, current, expires))
            db.commit()
            row = db.execute("SELECT * FROM scan_commands WHERE request_id=?", (command_id,)).fetchone()
        with COMMAND_AVAILABLE:
            COMMAND_AVAILABLE.notify_all()
        self.send_json(202, {"success": True, "data": command_view(row)}, request_id)

    def get_command(self, command_id, device, request_id):
        with DB_LOCK, closing(connect_db()) as db:
            row = db.execute("SELECT * FROM scan_commands WHERE request_id=? AND device_no=?",
                             (command_id, device["device_no"])).fetchone()
        self.send_json(200, {"success": True, "data": command_view(row)}, request_id) if row else \
            self.send_json(404, {"success": False, "error": "command_not_found"}, request_id)

    def list_commands(self, parsed, device, request_id):
        try:
            limit = min(max(int(parse_qs(parsed.query).get("limit", ["20"])[0]), 1), 100)
        except ValueError:
            self.send_json(400, {"success": False, "error": "invalid_limit"}, request_id); return
        with DB_LOCK, closing(connect_db()) as db:
            rows = db.execute("SELECT * FROM scan_commands WHERE device_no=? ORDER BY created_at DESC LIMIT ?",
                              (device["device_no"], limit)).fetchall()
        self.send_json(200, {"success": True, "data": [command_view(row) for row in rows]}, request_id)

    def claim(self, parsed, request_id):
        try:
            wait_seconds = min(max(int(parse_qs(parsed.query).get("waitSeconds", ["0"])[0]), 0), MAX_LONG_POLL_SECONDS)
        except ValueError:
            self.send_json(400, {"success": False, "error": "invalid_wait_seconds"}, request_id); return
        worker_id = self.headers.get("X-Worker-Id", "company-linux-worker").strip()[:100] or "company-linux-worker"
        deadline = time.monotonic() + wait_seconds
        while True:
            row = claim_command(worker_id)
            if row:
                self.send_json(200, {"success": True, "data": command_view(row, True)}, request_id); return
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                self.send_json(200, {"success": True, "data": None}, request_id); return
            with COMMAND_AVAILABLE:
                COMMAND_AVAILABLE.wait(timeout=min(remaining, 1.0))

    def worker_update(self, command_id, operation, request_id):
        body = self.body_or_error(request_id, RESULT_MAX_BODY)
        if body is None:
            return
        worker_id = self.headers.get("X-Worker-Id", "company-linux-worker").strip()[:100] or "company-linux-worker"
        timestamp, current = utc_now(), now_iso()
        with DB_LOCK, closing(connect_db()) as db:
            row = db.execute("SELECT * FROM scan_commands WHERE request_id=?", (command_id,)).fetchone()
            if not row:
                self.send_json(404, {"success": False, "error": "command_not_found"}, request_id); return
            if row["status"] in ("SUCCEEDED", "FAILED"):
                self.send_json(200, {"success": True, "data": command_view(row)}, request_id); return
            if row["status"] != "LEASED" or row["lease_owner"] != worker_id:
                self.send_json(409, {"success": False, "error": "lease_not_owned"}, request_id); return
            if operation == "renew":
                db.execute("UPDATE scan_commands SET lease_until=?,updated_at=? WHERE request_id=?",
                           (to_iso(timestamp + timedelta(seconds=LEASE_SECONDS)), current, command_id))
            elif operation == "complete":
                db.execute("UPDATE scan_commands SET status='SUCCEEDED',result=?,error=NULL,updated_at=?,completed_at=?,"
                           "lease_owner=NULL,lease_until=NULL WHERE request_id=?",
                           (json_text(body.get("result", body)), current, current, command_id))
            else:
                error = str(body.get("error", "worker_execution_failed"))[:4000]
                retryable = bool(body.get("retryable")) and row["attempt_count"] < MAX_ATTEMPTS and row["expires_at"] > current
                status = "QUEUED" if retryable else "FAILED"
                db.execute("UPDATE scan_commands SET status=?,error=?,updated_at=?,completed_at=?,lease_owner=NULL,lease_until=NULL "
                           "WHERE request_id=?", (status, error, current, None if retryable else current, command_id))
            db.commit()
            updated = db.execute("SELECT * FROM scan_commands WHERE request_id=?", (command_id,)).fetchone()
        if updated["status"] == "QUEUED":
            with COMMAND_AVAILABLE:
                COMMAND_AVAILABLE.notify_all()
        self.send_json(200, {"success": True, "data": command_view(updated)}, request_id)

    def legacy_tasks(self, parsed, request_id):
        try:
            limit = min(max(int(parse_qs(parsed.query).get("limit", ["20"])[0]), 1), 100)
        except ValueError:
            self.send_json(400, {"success": False, "error": "invalid_limit"}, request_id); return
        with DB_LOCK, closing(connect_db()) as db:
            rows = db.execute("SELECT * FROM tasks WHERE status='pending' ORDER BY created_at LIMIT ?", (limit,)).fetchall()
        self.send_json(200, {"success": True, "tasks": [{"id": r["id"], "createdAt": r["created_at"],
                       "updatedAt": r["updated_at"], "payload": parse_json(r["payload"])} for r in rows]}, request_id)

    def legacy_post(self, path, request_id):
        body = self.body_or_error(request_id)
        if body is None:
            return
        if path == "/api/data":
            item_id = str(uuid.uuid4())
            with DB_LOCK, closing(connect_db()) as db:
                db.execute("INSERT INTO received_data VALUES(?,?,?,?)", (item_id, now_iso(),
                           str(body.get("source", "company-linux"))[:128], json_text(body))); db.commit()
            self.send_json(202, {"success": True, "id": item_id}, request_id)
        elif path == "/api/tasks":
            task_id, current = str(uuid.uuid4()), now_iso()
            with DB_LOCK, closing(connect_db()) as db:
                db.execute("INSERT INTO tasks VALUES(?,?,?,'pending',?,NULL)", (task_id, current, current, json_text(body))); db.commit()
            self.send_json(201, {"success": True, "id": task_id}, request_id)
        else:
            task_id = path[len("/api/tasks/"):-len("/result")]
            with DB_LOCK, closing(connect_db()) as db:
                changed = db.execute("UPDATE tasks SET status='completed',updated_at=?,result=? WHERE id=? AND status='pending'",
                                     (now_iso(), json_text(body), task_id)); db.commit()
            self.send_json(200, {"success": True, "id": task_id}, request_id) if changed.rowcount else \
                self.send_json(404, {"success": False, "error": "task_not_found_or_completed"}, request_id)


def validate_config():
    if len(API_TOKEN) < 32:
        raise SystemExit("RELAY_API_TOKEN must contain at least 32 characters")
    if len(ENROLLMENT_KEY) < 32:
        raise SystemExit("RELAY_APP_ENROLLMENT_KEY must contain at least 32 characters")


def main():
    validate_config()
    init_db()
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"External scan relay listening on http://{HOST}:{PORT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
