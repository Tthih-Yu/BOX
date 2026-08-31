import importlib.util
import json
import os
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "server.py"
spec = importlib.util.spec_from_file_location("external_scan_server", MODULE_PATH)
relay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(relay)


class RelayApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory()
        relay.DB_PATH = os.path.join(cls.temp_dir.name, "relay.db")
        relay.API_TOKEN = "worker-token-abcdefghijklmnopqrstuvwxyz"
        relay.ENROLLMENT_KEY = "enrollment-key-abcdefghijklmnopqrstuvwxyz"
        relay.DEVICE_RATE_LIMIT = 1000
        relay.RATE_BUCKETS.clear()
        relay.init_db()
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), relay.Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base_url = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)
        cls.temp_dir.cleanup()

    def request(self, method, path, body=None, headers=None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base_url + path, data=data, method=method)
        for name, value in (headers or {}).items():
            request.add_header(name, value)
        if data is not None:
            request.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(request, timeout=3) as response:
                return response.status, json.loads(response.read())
        except urllib.error.HTTPError as error:
            return error.code, json.loads(error.read())

    def register(self, device_no="M71-001"):
        status, body = self.request("POST", "/app/v1/device/register", {
            "deviceNo": device_no, "deviceModel": "M71", "employeeNo": "10001"
        }, {"X-Enrollment-Key": relay.ENROLLMENT_KEY})
        self.assertEqual(201, status)
        return body["data"]["deviceToken"]

    @staticmethod
    def device_headers(token, device_no="M71-001"):
        return {"Authorization": f"Bearer {token}", "X-Device-No": device_no}

    @staticmethod
    def worker_headers(worker_id="worker-a"):
        return {"Authorization": f"Bearer {relay.API_TOKEN}", "X-Worker-Id": worker_id}

    def test_health_and_authentication(self):
        status, body = self.request("GET", "/health")
        self.assertEqual(200, status)
        self.assertEqual("UP", body["status"])
        status, body = self.request("POST", "/app/v1/device/register", {"deviceNo": "bad"})
        self.assertEqual(401, status)
        self.assertEqual("invalid_enrollment_key", body["error"])

    def test_command_lifecycle_and_idempotency(self):
        token = self.register()
        command = {
            "requestId": "APP-SCAN-test-0001", "action": "preview",
            "scanCode": "13799816,物料架-01-F03,备用", "format": "QR_CODE"
        }
        headers = self.device_headers(token)
        status, created = self.request("POST", "/app/v1/commands", command, headers)
        self.assertEqual(202, status)
        self.assertEqual("QUEUED", created["data"]["status"])

        status, duplicate = self.request("POST", "/app/v1/commands", command, headers)
        self.assertEqual(200, status)
        self.assertEqual("APP-SCAN-test-0001", duplicate["data"]["requestId"])

        conflict = dict(command, scanCode="different")
        status, body = self.request("POST", "/app/v1/commands", conflict, headers)
        self.assertEqual(409, status)
        self.assertEqual("idempotency_conflict", body["error"])

        status, claimed = self.request("POST", "/worker/v1/commands/claim?waitSeconds=0", {}, self.worker_headers())
        self.assertEqual(200, status)
        self.assertEqual("LEASED", claimed["data"]["status"])
        self.assertEqual("10001", claimed["data"]["employeeNo"])

        status, body = self.request("POST", "/worker/v1/commands/APP-SCAN-test-0001/complete",
                                    {"result": {"success": True, "data": {"materialCode": "13799816"}}},
                                    self.worker_headers("wrong-worker"))
        self.assertEqual(409, status)
        self.assertEqual("lease_not_owned", body["error"])

        status, completed = self.request("POST", "/worker/v1/commands/APP-SCAN-test-0001/complete",
                                         {"result": {"success": True, "data": {"materialCode": "13799816"}}},
                                         self.worker_headers())
        self.assertEqual(200, status)
        self.assertEqual("SUCCEEDED", completed["data"]["status"])

        status, queried = self.request("GET", "/app/v1/commands/APP-SCAN-test-0001", headers=headers)
        self.assertEqual(200, status)
        self.assertEqual("13799816", queried["data"]["result"]["data"]["materialCode"])

    def test_retryable_failure_returns_to_queue(self):
        token = self.register("M71-002")
        command = {"requestId": "APP-SCAN-test-0002", "action": "empty", "scanCode": "10002"}
        self.assertEqual(202, self.request("POST", "/app/v1/commands", command,
                                         self.device_headers(token, "M71-002"))[0])
        claimed = self.request("POST", "/worker/v1/commands/claim", {}, self.worker_headers())[1]
        self.assertEqual("APP-SCAN-test-0002", claimed["data"]["requestId"])
        status, failed = self.request("POST", "/worker/v1/commands/APP-SCAN-test-0002/fail",
                                      {"error": "temporary_network_error", "retryable": True}, self.worker_headers())
        self.assertEqual(200, status)
        self.assertEqual("QUEUED", failed["data"]["status"])
        claimed_again = self.request("POST", "/worker/v1/commands/claim", {}, self.worker_headers())[1]
        self.assertEqual(2, claimed_again["data"]["attemptCount"])

    def test_legacy_task_api_remains_available(self):
        headers = {"Authorization": f"Bearer {relay.API_TOKEN}"}
        status, created = self.request("POST", "/api/tasks", {"type": "echo"}, headers)
        self.assertEqual(201, status)
        status, tasks = self.request("GET", "/api/tasks?limit=10", headers=headers)
        self.assertEqual(200, status)
        self.assertTrue(any(item["id"] == created["id"] for item in tasks["tasks"]))


if __name__ == "__main__":
    unittest.main()
