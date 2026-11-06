# SmartEnergy Hub

Plateforme distribuée de supervision énergétique : collecte, stockage, analyse et restitution de mesures de capteurs.
Spring Boot, Redis Streams, Cassandra, MongoDB et Docker Compose.

## Prérequis

- Docker Desktop (Docker Compose inclus), 5 Go de mémoire alloués
- Python 3 pour le simulateur et les scripts de test (bibliothèque standard uniquement)

Le JDK et Maven ne sont pas nécessaires sur la machine : la compilation se fait dans les images Docker (Java 17).

## Démarrage

```bash
cp .env.example .env              # puis changer les mots de passe
docker compose up -d --build      # quelques minutes la première fois (téléchargements et compilation)
python simulator/simulator.py --backfill-hours 6
```

La dernière commande envoie 6 heures d'historique, puis continue en temps réel.
Le simulateur peut aussi tourner dans Docker : `docker compose --profile sim up -d --build simulator`.

| Adresse | Contenu |
|---|---|
| http://localhost:8083 | Tableau de bord |
| http://localhost:8083/architecture.html | Schéma d'architecture |
| http://localhost:8083/swagger-ui.html | API de consultation |
| http://localhost:8081/swagger-ui.html | API de collecte |
| http://localhost:8082/api/pipeline/status | État du flux Redis |
| http://localhost:808x/actuator/health | Santé de chaque service (8081, 8082, 8083) |

Arrêt : `docker compose --profile sim down`. Ajouter `-v` pour effacer aussi les données.

## Simulateur

```bash
python simulator/simulator.py --sensors 5 --interval 2 --duration 120
python simulator/simulator.py --anomaly-sensor SENSOR-001 --anomaly-every 60 --anomaly-duration 10 --anomaly-factor 3
```

`python simulator/simulator.py --help` liste tous les paramètres. Chacun a aussi une variable d'environnement.

## Tests

```bash
# Tests unitaires d'un service, sans JDK local
docker run --rm -v "$PWD/collector-service:/src" -w /src maven:3.9-eclipse-temurin-17 mvn test

# Scénarios de bout en bout (plateforme lancée, simulateur arrêté)
python scripts/e2e_test.py        # écrit docs/test-results.md

# Expérience de charge
python scripts/load_test.py       # écrit docs/load-results.md
```

Le scénario 7 arrête Cassandra et le scénario 11 redémarre toute la plateforme : compter environ 2 minutes 30.

## Inspecter les bases

```bash
docker compose exec redis redis-cli XLEN energy-measurements
docker compose exec redis redis-cli XINFO GROUPS energy-measurements
docker compose exec cassandra cqlsh -e "SELECT * FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='SENSOR-001' AND day='2026-11-06' LIMIT 5;"
docker compose exec mongo mongosh -u smartenergy -p change-me-mongo --authenticationDatabase admin smartenergy --quiet --eval "db.daily_summaries.find().limit(3)"
```

Le jour Cassandra est en UTC. Les mots de passe sont ceux du fichier `.env`.

## Organisation

```
collector-service/   réception, validation, publication dans Redis
storage-service/     consumer Redis, persistance Cassandra, agrégats MongoDB, cache
analytics-service/   API de consultation, indicateurs, anomalies, prévision, tableau de bord
simulator/           capteurs simulés
scripts/             initialisation des bases, tests de bout en bout, charge
docs/                rapport, schéma JSON, requêtes HTTP, preuves de tests
```

Le rapport technique est dans [docs/rapport.md](docs/rapport.md).
