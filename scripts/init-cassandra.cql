CREATE KEYSPACE IF NOT EXISTS smartenergy
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

-- Table conçue à partir de la requête : "historique d'un capteur entre deux dates".
-- (sensor_id, day) = clé de partition : borne la taille d'une partition à une journée.
-- measured_at = clé de clustering : tri DESC, et rend l'écriture idempotente (upsert).
CREATE TABLE IF NOT EXISTS smartenergy.measurements_by_sensor_day (
    sensor_id   text,
    day         date,
    measured_at timestamp,
    power_kw    double,
    voltage     double,
    temperature double,
    PRIMARY KEY ((sensor_id, day), measured_at)
) WITH CLUSTERING ORDER BY (measured_at DESC);
