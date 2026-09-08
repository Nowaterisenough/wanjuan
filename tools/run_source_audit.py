#!/usr/bin/env python3
"""Run the opt-in Release instrumentation audit sequentially with a host watchdog.

Requires a signed Release instrumentation build and its matching test APK installed.
The watchdog bounds blocking source JavaScript that coroutine timeouts cannot stop.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

from source_audit_proxy import emulator_proxy


ROOT = Path(__file__).resolve().parents[1]
TEST_CLASS = "io.wanjuan.app.TestAllSourcesAuditInstrumented"
REMOTE = "/sdcard/Android/data/io.wanjuan.app/files/source-audit"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    parser.add_argument("--indices", help="Comma-separated zero-based indices; default is every source")
    parser.add_argument("--output", type=Path, default=ROOT / "app/build/outputs/source-audit")
    parser.add_argument("--timeout", type=int, default=115)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--entry-pattern")
    parser.add_argument("--keyword")
    parser.add_argument("--proxy", help="HTTP proxy used temporarily for the entire emulator audit")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    sources = []
    manifests = [ROOT / "tests/shareBookSource.json", ROOT / "app/src/main/assets/defaultData/bookSources.json"]
    for path in manifests:
        for source in json.loads(path.read_text()):
            if not any(s["bookSourceUrl"] == source["bookSourceUrl"] for s in sources):
                sources.append(source)
    indices = [int(value) for value in args.indices.split(",")] if args.indices else range(len(sources))
    adb = ["adb", "-s", args.serial]
    metadata = {"serial": args.serial, "sourceCount": len(sources), "hostTimeoutSeconds": args.timeout,
                "inputs": {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in manifests}}

    def instrument(method, extra=()):
        return adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS + "#" + method,
                      *extra, "io.wanjuan.app.test/androidx.test.runner.AndroidJUnitRunner"]

    with emulator_proxy(adb, args.proxy) as (relay, proxy_info):
        metadata["network"] = proxy_info or {"mode": "system-default"}
        (args.output / "metadata.json").write_text(json.dumps(metadata, indent=2))
        try:
            for index in indices:
                source = sources[index]
                destination = args.output / f"{index:03d}.json"
                if args.resume and destination.exists():
                    continue
                started = time.monotonic()
                before_proxy = relay.snapshot() if relay else None
                print(f"START {index:03d} {source['bookSourceName']}", flush=True)
                subprocess.run(adb + ["shell", "am", "force-stop", "io.wanjuan.app"], check=True, capture_output=True)
                # Delete only this diagnostic's stale report, so a crash cannot be mistaken for a prior pass.
                subprocess.run(adb + ["shell", "rm", "-f", f"{REMOTE}/{index:03d}.json"], check=True, capture_output=True)
                extra = ["-e", "sourceIndex", str(index), "-e", "sourceTimeout", str((args.timeout - 15) * 1000)]
                if args.entry_pattern:
                    import shlex
                    extra += ["-e", "entryPattern", shlex.quote(args.entry_pattern)]
                if args.keyword:
                    import shlex
                    extra += ["-e", "keyword", shlex.quote(args.keyword)]
                if proxy_info:
                    extra += ["-e", "auditProxy", proxy_info["deviceProxy"]]
                timed_out = False
                try:
                    completed = subprocess.run(instrument("auditOneSource", extra), capture_output=True,
                                               text=True, timeout=args.timeout)
                    transcript = completed.stdout + completed.stderr
                except subprocess.TimeoutExpired:
                    timed_out = True
                    transcript = "Host watchdog deadline reached"
                    subprocess.run(adb + ["shell", "am", "force-stop", "io.wanjuan.app"], check=True, capture_output=True)
                (args.output / f"{index:03d}.instrumentation.txt").write_text(transcript)
                pulled = subprocess.run(adb + ["exec-out", "cat", f"{REMOTE}/{index:03d}.json"], capture_output=True, text=True)
                try:
                    report = json.loads(pulled.stdout)
                except (ValueError, TypeError):
                    report = {"index": index, "source": source["bookSourceName"], "status": "harness_error"}
                if relay:
                    after_proxy = relay.snapshot()
                    report["proxy"] = {**proxy_info, "traffic": {
                        key: after_proxy[key] - before_proxy[key] for key in after_proxy}}
                report["hostElapsedSeconds"] = round(time.monotonic() - started, 1)
                report["hostTimedOut"] = timed_out
                report["instrumentationCompleted"] = "OK (1 test)" in transcript
                if timed_out or report.get("status") == "running":
                    report["status"] = "timeout" if timed_out else "process_failed"
                destination.write_text(json.dumps(report, ensure_ascii=False, indent=2))
                print(f"END   {index:03d} {report['status']} {report['hostElapsedSeconds']}s stage={report.get('activeStage', '-')}", flush=True)
        finally:
            cleanup = subprocess.run(instrument("restoreAuditSource"), capture_output=True, text=True, timeout=30)
            (args.output / "cleanup.txt").write_text(cleanup.stdout + cleanup.stderr)
            if "OK (1 test)" not in cleanup.stdout:
                raise RuntimeError("Audit source restoration did not complete; inspect cleanup.txt")


if __name__ == "__main__":
    main()
