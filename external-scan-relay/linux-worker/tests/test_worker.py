import importlib.util
import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "worker.py"
spec = importlib.util.spec_from_file_location("external_scan_worker", MODULE_PATH)
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)


class StubHandler(BaseHTTPRequestHandler):
    requests = []
    claim_payload = None
    internal_status = 200

    def log_message(self, *_args):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        self.__class__.requests.append((self.path, dict(self.headers), body))
        if self.path.startswith("/aliyun-relay/worker/commands/claim"):
            self.respond(200, {"success": True, "data": self.__class__.claim_payload})
        elif self.path.startswith("/api/scan/"):
            if self.__class__.internal_status == 200:
                self.respond(200, {"success": True, "data": {"taskNo": "TASK-001"}})
            else:
                self.respond(self.__class__.internal_status, {"success": False, "message": "internal_error"})
        else:
            self.respond(200, {"success": True})

    def respond(self, status, value):
        data = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class WorkerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), StubHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        base = f"http://127.0.0.1:{cls.server.server_port}"
        worker.RELAY_URL = base + "/aliyun-relay"
        worker.INTERNAL_API = base + "/api"
        worker.RELAY_TOKEN = "relay-token-abcdefghijklmnopqrstuvwxyz"
        worker.INTERNAL_DEVICE_KEY = "device-key-abcdefghijklmnopqrstuvwxyz"
        worker.WORKER_ID = "worker-test"
        worker.LONG_POLL_SECONDS = 1
        worker.RELAY_TIMEOUT_SECONDS = 3
        worker.INTERNAL_TIMEOUT_SECONDS = 3

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)

    def setUp(self):
        StubHandler.requests = []
        StubHandler.internal_status = 200
        StubHandler.claim_payload = {
            "requestId": "APP-SCAN-worker-test", "action": "empty", "attemptCount": 1,
            "deviceNo": "M71-001", "employeeNo": "10001",
            "payload": {"scanCode": "13799816", "requestQty": 2, "requestUnit": "个"}
        }

    def test_successful_command(self):
        self.assertTrue(worker.process_one())
        paths = [item[0] for item in StubHandler.requests]
        self.assertIn("/api/scan/empty", paths)
        self.assertIn("/aliyun-relay/worker/commands/APP-SCAN-worker-test/complete", paths)
        internal = next(item for item in StubHandler.requests if item[0] == "/api/scan/empty")
        self.assertEqual("APP-SCAN-worker-test", internal[1]["X-Idempotency-Key"])
        self.assertEqual("APP-SCAN-worker-test", internal[2]["idempotencyKey"])
        self.assertEqual("device-key-abcdefghijklmnopqrstuvwxyz", internal[1]["X-Device-Key"])

    def test_server_error_is_retryable(self):
        StubHandler.internal_status = 503
        self.assertTrue(worker.process_one())
        failed = next(item for item in StubHandler.requests if item[0].endswith("/fail"))
        self.assertTrue(failed[2]["retryable"])

    def test_invalid_action_is_permanent(self):
        StubHandler.claim_payload["action"] = "delete"
        self.assertTrue(worker.process_one())
        failed = next(item for item in StubHandler.requests if item[0].endswith("/fail"))
        self.assertFalse(failed[2]["retryable"])
        self.assertFalse(any(item[0].startswith("/api/scan/") for item in StubHandler.requests))

    def test_empty_queue(self):
        StubHandler.claim_payload = None
        self.assertFalse(worker.process_one())


if __name__ == "__main__":
    unittest.main()
