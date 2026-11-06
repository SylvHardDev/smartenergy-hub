#!/usr/bin/env python3
"""Expérience de charge : latence du Collector, délai d'absorption et temps de lecture de l'historique.

Usage : python scripts/load_test.py   (résultat : console et docs/load-results.md)
"""
import json
import pathlib
import statistics
import subprocess
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone

COLLECTOR = "http://localhost:8081/api/measurements"
ANALYTICS = "http://localhost:8083"
ROOT = pathlib.Path(__file__).resolve().parent.parent
REQUESTS = 2000
RUN = datetime.now(timezone.utc).strftime("%H%M%S")


def lag():
    out = subprocess.run(["docker", "compose", "exec", "-T", "redis", "redis-cli", "XINFO", "GROUPS", "energy-measurements"],
                         cwd=ROOT, capture_output=True, text=True).stdout.split()
    return int(out[out.index("lag") + 1])


def post(args):
    sensor, moment = args
    body = json.dumps({"sensorId": sensor, "timestamp": moment.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z",
                       "powerKw": 3.5, "voltage": 230.0, "temperature": 25.0}).encode()
    request = urllib.request.Request(COLLECTOR, data=body, headers={"Content-Type": "application/json"}, method="POST")
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            status = response.status
    except urllib.error.HTTPError as error:
        status = error.code
    except OSError:
        status = 0
    return (time.perf_counter() - started) * 1000, status


def percentile(values, ratio):
    return sorted(values)[int(len(values) * ratio) - 1]


def run(workers):
    sensor = f"LOAD-{RUN}-{workers}"
    base = datetime.now(timezone.utc) - timedelta(minutes=30)
    jobs = [(sensor, base + timedelta(milliseconds=i)) for i in range(REQUESTS)]
    started = time.perf_counter()
    with ThreadPoolExecutor(workers) as pool:
        outcomes = list(pool.map(post, jobs))
    elapsed = time.perf_counter() - started
    backlog = lag()
    drain_started = time.perf_counter()
    while lag() > 0:
        time.sleep(0.2)
    drain = time.perf_counter() - drain_started
    latencies = [latency for latency, _ in outcomes]
    errors = sum(1 for _, status in outcomes if status != 202)

    read_started = time.perf_counter()
    with urllib.request.urlopen(f"{ANALYTICS}/api/measurements/{sensor}", timeout=30) as response:
        rows = len(json.load(response))
    read_ms = (time.perf_counter() - read_started) * 1000
    return {"workers": workers, "rate": REQUESTS / elapsed, "p50": statistics.median(latencies),
            "p95": percentile(latencies, 0.95), "errors": errors, "backlog": backlog, "drain": drain,
            "rows": rows, "read_ms": read_ms}


lines = [
    "# Expérience de charge",
    "",
    f"Exécution : {datetime.now().strftime('%d/%m/%Y %H:%M')}, {REQUESTS} requêtes par palier, sur un poste de développement.",
    "Il s'agit d'un ordre de grandeur, pas d'un benchmark : le client Python partage la machine avec la plateforme.",
    "",
    "| Clients simultanés | Débit (req/s) | Latence p50 (ms) | Latence p95 (ms) | Erreurs | File Redis en fin d'envoi | Absorption (s) | Lignes lues | Lecture historique (ms) |",
    "|---|---|---|---|---|---|---|---|---|",
]
for workers in (1, 10, 50):
    r = run(workers)
    print(r)
    lines.append(f"| {r['workers']} | {r['rate']:.0f} | {r['p50']:.1f} | {r['p95']:.1f} | {r['errors']} | {r['backlog']} "
                 f"| {r['drain']:.1f} | {r['rows']} | {r['read_ms']:.0f} |")
(ROOT / "docs" / "load-results.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
