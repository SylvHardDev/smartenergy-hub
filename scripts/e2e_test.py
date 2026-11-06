#!/usr/bin/env python3
"""Test de bout en bout : déroule les scénarios obligatoires sur la plateforme lancée.

Prérequis : docker compose up -d --build, simulateur arrêté.
Usage     : python scripts/e2e_test.py
Résultat  : affichage console et docs/test-results.md
"""
import json
import pathlib
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

COLLECTOR = "http://localhost:8081"
STORAGE = "http://localhost:8082"
ANALYTICS = "http://localhost:8083"
STREAM = "energy-measurements"
DLQ = "energy-measurements-dlq"
ROOT = pathlib.Path(__file__).resolve().parent.parent
RUN = datetime.now(timezone.utc).strftime("%H%M%S")
results = []


def compose(*args):
    return subprocess.run(["docker", "compose", *args], cwd=ROOT, capture_output=True, text=True, encoding="utf-8")


def redis(*args):
    return compose("exec", "-T", "redis", "redis-cli", *args).stdout.strip()


def cql_count(sensor, day):
    query = f"SELECT count(*) FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='{sensor}' AND day='{day}';"
    out = compose("exec", "-T", "cassandra", "cqlsh", "-e", query).stdout
    return int(re.search(r"-+\s+(\d+)", out).group(1))


def http(method, url, body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else None
    except (urllib.error.URLError, OSError):
        return 0, None


def iso(moment):
    return moment.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def measure(sensor, moment=None, power=3.5):
    moment = moment or datetime.now(timezone.utc)
    return {"sensorId": sensor, "timestamp": iso(moment), "powerKw": power, "voltage": 230.0, "temperature": 25.0}


def post(payload):
    return http("POST", f"{COLLECTOR}/api/measurements", payload)[0]


def wait_until(condition, timeout=60):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            if condition():
                return True
        except Exception:
            pass
        time.sleep(1)
    return False


def history(sensor):
    code, rows = http("GET", f"{ANALYTICS}/api/measurements/{sensor}")
    return rows if code == 200 else []


def stream_length():
    return int(redis("XLEN", STREAM))


def lag():
    fields = redis("XINFO", "GROUPS", STREAM).split()
    return int(fields[fields.index("lag") + 1])


def scenario(number, name, expected):
    def decorator(function):
        started = time.time()
        try:
            passed, obtained = function()
        except Exception as error:  # un scénario en échec ne doit pas masquer les suivants
            passed, obtained = False, f"exception : {error}"
        results.append((number, name, expected, obtained, passed))
        print(f"[{'OK' if passed else 'ECHEC'}] {number}. {name} : {obtained} ({time.time() - started:.1f} s)")
        return function
    return decorator


sensor = f"E2E-{RUN}"


@scenario(1, "Envoi d'une mesure valide", "HTTP 202 + événement Redis + ligne Cassandra")
def valid():
    before = stream_length()
    code = post(measure(sensor))
    added = stream_length() - before
    stored = wait_until(lambda: len(history(sensor)) == 1, 15)
    return code == 202 and added == 1 and stored, f"HTTP {code}, +{added} événement, stocké={stored}"


@scenario(2, "Mesure sans sensorId", "HTTP 400, aucun événement")
def missing_sensor():
    before = stream_length()
    payload = measure(sensor)
    del payload["sensorId"]
    code, body = http("POST", f"{COLLECTOR}/api/measurements", payload)
    added = stream_length() - before
    return code == 400 and added == 0, f"HTTP {code}, +{added} événement, message : {body['details'][0]}"


@scenario(3, "Lecture de l'historique", "Liste triée du plus récent au plus ancien")
def read_history():
    start = datetime.now(timezone.utc) - timedelta(minutes=10)
    for i in range(1, 10):
        post(measure(sensor, start + timedelta(seconds=i)))
    wait_until(lambda: len(history(sensor)) == 10, 15)
    dates = [row["timestamp"] for row in history(sensor)]
    return len(dates) == 10 and dates == sorted(dates, reverse=True), f"{len(dates)} mesures, ordre décroissant"


@scenario(4, "Injection d'un pic", "Anomalie détectée")
def spike():
    spike_sensor = "SENSOR-003"
    moment = datetime.now(timezone.utc) + timedelta(seconds=1)
    post(measure(spike_sensor, moment, power=15.0))

    def found():
        code, rows = http("GET", f"{ANALYTICS}/api/analytics/{spike_sensor}/anomalies")
        return code == 200 and any(r["powerKw"] == 15.0 and "THRESHOLD" in r["reasons"] for r in rows)
    detected = wait_until(found, 15)
    return detected, f"pic de 15 kW pour un seuil de 8 kW, détecté={detected}"


outage_sensor = f"OUTAGE-{RUN}"
OUTAGE_COUNT = 50


@scenario(5, "Arrêt du Storage Service", "Messages conservés dans Redis")
def storage_down():
    compose("stop", "storage-service")
    start = datetime.now(timezone.utc) - timedelta(minutes=5)
    accepted = sum(post(measure(outage_sensor, start + timedelta(seconds=i))) == 202 for i in range(OUTAGE_COUNT))
    backlog = lag()
    return accepted == OUTAGE_COUNT and backlog >= OUTAGE_COUNT, f"{accepted} mesures acceptées, {backlog} en attente dans Redis"


@scenario(6, "Redémarrage du Storage Service", "Reprise, aucune mesure perdue")
def storage_restart():
    compose("start", "storage-service")
    done = wait_until(lambda: len(history(outage_sensor)) == OUTAGE_COUNT and lag() == 0, 60)
    count = len(history(outage_sensor))
    return done and count == OUTAGE_COUNT, f"{count}/{OUTAGE_COUNT} mesures retrouvées, lag={lag()}"


@scenario(7, "Panne de Cassandra pendant un traitement", "Pas d'accusé, message rejoué après retour")
def cassandra_down():
    target = f"CASS-{RUN}"
    compose("stop", "cassandra")
    post(measure(target))
    pending = wait_until(lambda: int(redis("XPENDING", STREAM, "storage-group").split()[0]) > 0, 30)
    still_pending = redis("XPENDING", STREAM, "storage-group").split()[0]
    compose("start", "cassandra")
    recovered = wait_until(lambda: len(history(target)) == 1, 180)
    drained = wait_until(lambda: int(redis("XPENDING", STREAM, "storage-group").split()[0]) == 0, 60)
    return pending and recovered and drained, f"{still_pending} message en attente pendant la panne, rejoué={recovered}, file vide={drained}"


@scenario(8, "Livraison en double (idempotence)", "Une seule ligne, agrégat inchangé")
def duplicate():
    target = f"DUP-{RUN}"
    payload = measure(target)
    post(payload)
    post(payload)
    post(payload)
    wait_until(lambda: lag() == 0, 15)
    time.sleep(12)  # laisse passer un cycle de recalcul de l'agrégat
    count = len(history(target))
    code, summary = http("GET", f"{ANALYTICS}/api/sensors/{target}/daily-summary")
    aggregated = summary["measurementCount"] if code == 200 else None
    return count == 1 and aggregated == 1, f"3 envois, {count} ligne Cassandra, measurementCount MongoDB={aggregated}"


@scenario(9, "Événement corrompu dans le stream", "Isolé en dead letter, le consumer continue")
def poison():
    target = f"POISON-{RUN}"
    before = int(redis("XLEN", DLQ) or 0)
    redis("XADD", STREAM, "*", "sensorId", target, "powerKw", "pas-un-nombre")
    post(measure(target))
    processed = wait_until(lambda: len(history(target)) == 1, 15)
    added = int(redis("XLEN", DLQ) or 0) - before
    return processed and added == 1, f"+{added} en dead letter, mesure suivante traitée={processed}"


@scenario(10, "Agrégat MongoDB après passage dans la chaîne", "Résumé quotidien cohérent avec Cassandra")
def aggregate():
    day = datetime.now(timezone.utc).date().isoformat()
    time.sleep(11)
    code, summary = http("GET", f"{ANALYTICS}/api/sensors/{outage_sensor}/daily-summary")
    # La fenêtre de 5 min peut chevaucher minuit UTC : on compare à toutes les lignes de la journée.
    rows = cql_count(outage_sensor, day)
    return code == 200 and summary["measurementCount"] == rows, f"MongoDB={summary['measurementCount']}, Cassandra={rows}"


@scenario(11, "Redémarrage complet de la plateforme", "Données conservées (volumes Docker)")
def full_restart():
    before = len(history(sensor))
    compose("stop")
    compose("up", "-d")
    ready = wait_until(lambda: http("GET", f"{ANALYTICS}/actuator/health")[0] == 200, 180)
    after = len(history(sensor))
    code, sensors = http("GET", f"{ANALYTICS}/api/sensors")
    return ready and after == before and code == 200 and len(sensors) >= 5, f"{after}/{before} mesures, {len(sensors)} capteurs"


@scenario(12, "Santé et métriques", "Actuator UP sur les 3 services, métriques disponibles")
def observability():
    statuses = [http("GET", f"{url}/actuator/health")[1]["status"] for url in (COLLECTOR, STORAGE, ANALYTICS)]
    received = http("GET", f"{COLLECTOR}/actuator/metrics/smartenergy.measurements.received")[0]
    rejected = http("GET", f"{COLLECTOR}/actuator/metrics/smartenergy.measurements.rejected")[0]
    pending = http("GET", f"{STORAGE}/actuator/metrics/smartenergy.redis.pending")[0]
    ok = statuses == ["UP"] * 3 and {received, rejected, pending} == {200}
    return ok, f"santé={statuses}, métriques HTTP {received}/{rejected}/{pending}"


lines = [
    "# Preuves de tests de bout en bout",
    "",
    f"Exécution : {datetime.now().strftime('%d/%m/%Y %H:%M')}, avec `python scripts/e2e_test.py`.",
    "",
    "| N° | Scénario | Résultat attendu | Résultat obtenu | Statut |",
    "|---|---|---|---|---|",
]
for number, name, expected, obtained, passed in results:
    lines.append(f"| {number} | {name} | {expected} | {obtained} | {'OK' if passed else 'ÉCHEC'} |")
(ROOT / "docs").mkdir(exist_ok=True)
(ROOT / "docs" / "test-results.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

failed = sum(1 for result in results if not result[4])
print(f"\n{len(results) - failed}/{len(results)} scénarios réussis")
sys.exit(1 if failed else 0)
