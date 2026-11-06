#!/usr/bin/env python3
"""Simulateur de capteurs énergétiques (bibliothèque standard uniquement).

Modèle : P(t) = Pbase + A * sin(2*pi*t / T) + bruit
Anomalie : puissance multipliée par --anomaly-factor sur un capteur, à intervalle régulier.

Exemples :
  python simulator.py --sensors 5 --interval 2 --duration 120
  python simulator.py --backfill-hours 6            # historique rapide, puis temps réel
  python simulator.py --anomaly-every 60 --anomaly-duration 10
"""
import argparse
import json
import math
import os
import random
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone


def env(name, default):
    return os.environ.get(name, default)


def parse_args():
    p = argparse.ArgumentParser(description="Simulateur SmartEnergy Hub")
    p.add_argument("--url", default=env("COLLECTOR_URL", "http://localhost:8081/api/measurements"))
    p.add_argument("--sensors", type=int, default=int(env("SENSORS", 5)))
    p.add_argument("--interval", type=float, default=float(env("INTERVAL", 2)), help="secondes entre deux tours")
    p.add_argument("--duration", type=float, default=float(env("DURATION", 0)), help="secondes, 0 = sans fin")
    p.add_argument("--base", type=float, default=float(env("P_BASE", 3.5)), help="Pbase en kW")
    p.add_argument("--amplitude", type=float, default=float(env("AMPLITUDE", 1.2)), help="A en kW")
    p.add_argument("--period", type=float, default=float(env("PERIOD", 3600)), help="T en secondes")
    p.add_argument("--noise", type=float, default=float(env("NOISE", 0.15)), help="écart-type du bruit en kW")
    p.add_argument("--anomaly-sensor", default=env("ANOMALY_SENSOR", "SENSOR-003"))
    p.add_argument("--anomaly-every", type=float, default=float(env("ANOMALY_EVERY", 120)), help="secondes, 0 = désactivé")
    p.add_argument("--anomaly-duration", type=float, default=float(env("ANOMALY_DURATION", 10)))
    p.add_argument("--anomaly-factor", type=float, default=float(env("ANOMALY_FACTOR", 3)))
    p.add_argument("--backfill-hours", type=float, default=float(env("BACKFILL_HOURS", 0)))
    p.add_argument("--backfill-step", type=float, default=float(env("BACKFILL_STEP", 60)), help="secondes simulées")
    return p.parse_args()


def power(args, index, epoch, anomalous):
    # Chaque capteur a son propre niveau et son propre déphasage.
    base = args.base + 0.4 * index
    phase = index * math.pi / 5
    value = base + args.amplitude * math.sin(2 * math.pi * epoch / args.period + phase) + random.gauss(0, args.noise)
    value = max(value, 0.0)
    return value * args.anomaly_factor if anomalous else value


def is_anomalous(args, sensor_id, epoch):
    if sensor_id != args.anomaly_sensor or args.anomaly_every <= 0:
        return False
    return epoch % args.anomaly_every < args.anomaly_duration


def measurement(args, index, moment, backfilling=False):
    sensor_id = f"SENSOR-{index + 1:03d}"
    epoch = moment.timestamp()
    if backfilling:  # pas large : une règle périodique pourrait toucher un point sur deux
        anomalous = sensor_id == args.anomaly_sensor and random.random() < 0.01
    else:
        anomalous = is_anomalous(args, sensor_id, epoch)
    return {
        "sensorId": sensor_id,
        "timestamp": moment.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z",
        "powerKw": round(power(args, index, epoch, anomalous), 3),
        "voltage": round(random.gauss(230, 2), 1),
        "temperature": round(24 + 4 * math.sin(2 * math.pi * epoch / args.period) + random.gauss(0, 0.3), 1),
    }


def send(url, payload):
    request = urllib.request.Request(
        url, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code
    except (urllib.error.URLError, OSError):
        return 0


def wait_for_collector(url):
    while send(url, {}) == 0:  # un corps vide renvoie 400 : le service répond et rien n'est publié
        print("En attente du Collector...")
        time.sleep(2)


def backfill(args):
    now = datetime.now(timezone.utc)
    moment = now - timedelta(hours=args.backfill_hours)
    sent = 0
    while moment < now:
        for index in range(args.sensors):
            if send(args.url, measurement(args, index, moment, backfilling=True)) == 202:
                sent += 1
        moment += timedelta(seconds=args.backfill_step)
    print(f"Historique envoyé : {sent} mesures")


def main():
    args = parse_args()
    wait_for_collector(args.url)
    if args.backfill_hours > 0:
        backfill(args)
    started = time.monotonic()
    accepted = rejected = failed = 0
    while args.duration == 0 or time.monotonic() - started < args.duration:
        moment = datetime.now(timezone.utc)
        for index in range(args.sensors):
            status = send(args.url, measurement(args, index, moment))
            if status == 202:
                accepted += 1
            elif status == 0 or status >= 500:
                failed += 1
            else:
                rejected += 1
        print(f"{moment:%H:%M:%S} acceptées={accepted} rejetées={rejected} échecs={failed}")
        time.sleep(args.interval)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
