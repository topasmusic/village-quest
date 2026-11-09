import http.client
import json
import tempfile
import threading
import time
import unittest
from pathlib import Path

from http.server import HTTPServer
from stats import Handler, Store, validate


class StatsTest(unittest.TestCase):
    def test_retries_and_expiry(self):
        with tempfile.TemporaryDirectory() as path:
            store = Store(Path(path) / "stats.db")
            report = validate({"id": "11111111-1111-4111-8111-111111111111",
                               "day": 20000, "version": "2.6.5-unreleased.1",
                               "mode": "single", "counts": {"story.started.failing_harvest_1": 2}}, 20000)
            store.add(report, 20000)
            store.add(report, 20000)
            self.assertEqual(store.snapshot(0)["counts"][0][-1], 2)
            with self.assertRaises(ValueError):
                validate(report, 20007)

    def test_rejects_identity_fields(self):
        with self.assertRaises(ValueError):
            validate({"ip": "127.0.0.1"}, 20000)

    def test_http_transport_and_active_sessions(self):
        with tempfile.TemporaryDirectory() as path, HTTPServer(("127.0.0.1", 0), Handler) as server:
            server.store = Store(Path(path) / "stats.db")
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            client = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=2)
            try:
                session = "11111111-1111-4111-8111-111111111111"
                headers = {"Host": "127.0.0.1:8765", "Content-Type": "application/json"}
                client.request("POST", "/heartbeat", json.dumps({"session": session}), headers)
                response = client.getresponse()
                self.assertEqual(response.status, 204)
                response.read()
                client.request("GET", "/stats", headers=headers)
                self.assertEqual(json.loads(client.getresponse().read())["activeSessions"], 1)
                self.assertEqual(server.store.snapshot(time.monotonic() + 151)["activeSessions"], 0)
                client.request("GET", "/", headers={"Host": "foreign.test"})
                response = client.getresponse()
                self.assertEqual(response.status, 403)
                response.read()
            finally:
                client.close()
                server.shutdown()
                thread.join()
