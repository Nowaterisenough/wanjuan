"""Temporarily route an emulator audit through a measured HTTP proxy relay."""
from contextlib import contextmanager
import select
import socket
import socketserver
import subprocess
import threading
from urllib.parse import urlparse


class ProxyRelay(socketserver.ThreadingTCPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, upstream):
        parsed = urlparse(upstream)
        if parsed.scheme != "http" or not parsed.hostname or not parsed.port:
            raise ValueError("Audit proxy must be an explicit http://host:port URL")
        if parsed.username or parsed.password:
            raise ValueError("Authenticated audit proxies are not supported")
        self.upstream = (parsed.hostname, parsed.port)
        self.lock = threading.Lock()
        self.connections = 0
        self.sent = 0
        self.received = 0
        super().__init__(("127.0.0.1", 0), ProxyConnection)

    def snapshot(self):
        with self.lock:
            return {"connections": self.connections, "sentBytes": self.sent,
                    "receivedBytes": self.received}


class ProxyConnection(socketserver.BaseRequestHandler):
    def handle(self):
        try:
            with socket.create_connection(self.server.upstream, timeout=15) as upstream:
                upstream.settimeout(15)
                self.request.settimeout(15)
                with self.server.lock:
                    self.server.connections += 1
                sockets = (self.request, upstream)
                while True:
                    readable, _, _ = select.select(sockets, [], [], 70)
                    if not readable:
                        return
                    for stream in readable:
                        data = stream.recv(65536)
                        if not data:
                            return
                        target = upstream if stream is self.request else self.request
                        target.sendall(data)
                        with self.server.lock:
                            if stream is self.request:
                                self.server.sent += len(data)
                            else:
                                self.server.received += len(data)
        except OSError:
            # Cancellations and the per-source watchdog intentionally close sockets.
            return


@contextmanager
def emulator_proxy(adb, upstream):
    if not upstream:
        yield None, None
        return
    previous = subprocess.check_output(
        adb + ["shell", "settings", "get", "global", "http_proxy"], text=True
    ).strip()
    with ProxyRelay(upstream) as relay:
        port = relay.server_address[1]
        mapping = f"tcp:{port}"
        device = f"127.0.0.1:{port}"
        worker = threading.Thread(target=relay.serve_forever, daemon=True)
        worker.start()
        forwarded = False
        try:
            subprocess.run(adb + ["reverse", mapping, mapping], check=True, capture_output=True)
            forwarded = True
            subprocess.run(adb + ["shell", "settings", "put", "global", "http_proxy", device],
                           check=True, capture_output=True)
            yield relay, {"upstream": upstream, "deviceProxy": device,
                          "transport": "adb reverse + measured TCP relay"}
        finally:
            try:
                command = ["delete", "global", "http_proxy"] if previous == "null" else [
                    "put", "global", "http_proxy", previous]
                subprocess.run(adb + ["shell", "settings", *command], check=True,
                               capture_output=True)
            finally:
                if forwarded:
                    subprocess.run(adb + ["reverse", "--remove", mapping], check=True,
                                   capture_output=True)
                relay.shutdown()
                worker.join(timeout=5)
