"""Loopback receiver and dashboard for Village Quest gameplay statistics."""
import json
import re
import sqlite3
import time
from contextlib import closing
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

DAY = 86400
REPORT_ID = r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
ALLOWED = frozenset(json.loads(Path(__file__).with_name("metrics.json").read_text(encoding="utf-8")))


def matches(pattern, value):
    return isinstance(value, str) and re.fullmatch(pattern, value) is not None


def validate(report, today):
    if not isinstance(report, dict) or set(report) != {"id", "day", "version", "mode", "counts"}:
        raise ValueError("Unexpected report fields")
    if (not matches(REPORT_ID, report["id"])
            or type(report["day"]) is not int or not today - 6 <= report["day"] <= today
            or not matches(r"[0-9]+\.[0-9]+\.[0-9]+(?:-unreleased\.[0-9]+)?", report["version"])
            or report["mode"] not in ("single", "multi")):
        raise ValueError("Invalid report metadata")
    counts = report["counts"]
    if not isinstance(counts, dict) or not 1 <= len(counts) <= 256:
        raise ValueError("Invalid counters")
    if any(key not in ALLOWED or type(value) is not int or not 1 <= value <= 10000
           for key, value in counts.items()):
        raise ValueError("Invalid counter")
    return report


class Store:
    def __init__(self, path):
        self.path = path
        self.sessions = {}
        with closing(sqlite3.connect(path)) as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS feedback (id TEXT PRIMARY KEY, day INTEGER, line TEXT, version TEXT, target TEXT, message TEXT);
                CREATE TABLE IF NOT EXISTS batches (id TEXT PRIMARY KEY, day INTEGER);
                CREATE TABLE IF NOT EXISTS totals (
                    day INTEGER, version TEXT, mode TEXT, metric TEXT, count INTEGER,
                    PRIMARY KEY (day, version, mode, metric));
            """)

    def add(self, report, today):
        with closing(sqlite3.connect(self.path)) as db, db:
            db.execute("DELETE FROM batches WHERE day < ?", (today - 6,))
            db.execute("DELETE FROM totals WHERE day < ?", (today - 89,))
            if not db.execute("INSERT OR IGNORE INTO batches VALUES (?, ?)",
                              (report["id"], report["day"])).rowcount:
                return
            db.executemany("""
                INSERT INTO totals VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(day, version, mode, metric) DO UPDATE SET count = count + excluded.count
                """, ((report["day"], report["version"], report["mode"], k, v)
                      for k, v in report["counts"].items()))

    def heartbeat(self, session, now):
        self.expire(now)
        if len(self.sessions) < 10000 or session in self.sessions:
            self.sessions[session] = now + 150

    def expire(self, now):
        self.sessions = {key: until for key, until in self.sessions.items() if until > now}

    def snapshot(self, now):
        self.expire(now)
        with closing(sqlite3.connect(self.path)) as db:
            counts = db.execute("SELECT * FROM totals ORDER BY day DESC, version, mode, metric").fetchall()
            db.execute("DELETE FROM feedback WHERE day < ?",(int(time.time()//DAY)-29,));db.commit()
            messages=db.execute("SELECT day,line,version,target,message FROM feedback ORDER BY day DESC,rowid DESC LIMIT 200").fetchall()
        return {"feedback":messages,"feedbackTruncated":len(messages)==200,"activeSessions": len(self.sessions), "counts": [[r[0], "26.2", *r[1:]] for r in counts], "truncated": False}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def reply(self, status, body=b"", content_type="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def parse_request(self):
        if not super().parse_request():
            return False
        if self.headers.get("Host") not in ("127.0.0.1:8765", "localhost:8765"):
            self.reply(403)
            return False
        return True

    def do_GET(self):
        if self.path == "/":
            self.reply(200, Path(__file__).with_name("dashboard.html").read_bytes(), "text/html; charset=utf-8")
        elif self.path in ("/dashboard.js", "/style.css"):
            name = self.path[1:]
            self.reply(200, Path(__file__).with_name(name).read_bytes(), "text/javascript; charset=utf-8" if name.endswith(".js") else "text/css; charset=utf-8")
        elif self.path == "/stats":
            self.reply(200, json.dumps(self.server.store.snapshot(time.monotonic())).encode())
        else:
            self.reply(404)

    def do_POST(self):
        if self.path not in ("/report", "/feedback", "/heartbeat", "/leave") or self.headers.get("Origin"):
            self.reply(404)
            return
        self.connection.settimeout(5)
        try:
            size = int(self.headers["Content-Length"])
            if not 1 <= size <= 32768 or self.headers.get_content_type() != "application/json":
                raise ValueError("Invalid body")
            data = json.loads(self.rfile.read(size))
            if self.path == "/feedback":
                today=int(time.time()//DAY)
                if not isinstance(data,dict) or set(data)!={"id","day","line","version","target","message"}: raise ValueError("fields")
                if not matches(REPORT_ID,data["id"]) or type(data["day"]) is not int or not today-1<=data["day"]<=today or data["line"] not in ("1.21.11","26.2","26.3") or not matches(r"2\.6\.5(?:-unreleased\.[0-9]+)?",data["version"]) or data["target"] not in "general daily weekly story special town commission freight shared afterstory".split(): raise ValueError("metadata")
                text=data["message"]
                if not isinstance(text,str) or not text.strip() or len(text.encode("utf-16-le"))//2>500 or re.search(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]",text): raise ValueError("text")
                with closing(sqlite3.connect(self.server.store.path)) as db,db:
                    db.execute("INSERT OR IGNORE INTO feedback VALUES (?,?,?,?,?,?)",(data["id"],data["day"],data["line"],data["version"],data["target"],text.strip()))
            elif self.path in ("/heartbeat", "/leave"):
                if not isinstance(data, dict) or set(data) != {"session"} or not matches(REPORT_ID, data["session"]):
                    raise ValueError("Invalid session")
                if self.path == "/leave": self.server.store.sessions.pop(data["session"], None)
                else: self.server.store.heartbeat(data["session"], time.monotonic())
            else:
                today = int(time.time() // DAY)
                self.server.store.add(validate(data, today), today)
        except (ValueError, TypeError, TimeoutError, RecursionError):
            self.reply(400)
            return
        self.reply(204)


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("database", type=Path)
    args = parser.parse_args()
    with HTTPServer(("127.0.0.1", 8765), Handler) as server:
        server.store = Store(args.database)
        server.serve_forever()
