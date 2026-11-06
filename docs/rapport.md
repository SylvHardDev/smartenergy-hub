# SmartEnergy Hub : rapport technique

Projet tutoré, Master 1 MIAGE, ESMIA. Réalisation individuelle. Remise du 6 novembre 2026.

## 1. Introduction et contexte

Les compteurs connectés produisent un flux continu de mesures. Il faut les recevoir sans jamais bloquer l'émetteur, conserver un historique volumineux, répondre vite à des lectures fréquentes et transformer ces données en indicateurs. Une base unique sert mal ces besoins, car ils tirent dans des directions opposées.

SmartEnergy Hub répond à ce problème avec une chaîne distribuée de trois services Spring Boot et trois bases spécialisées. Le projet a privilégié un MVP complet et vérifiable, puis deux extensions : la prévision et un tableau de bord Web.

## 2. Cahier des charges

La plateforme doit répondre à cinq questions : quelle est la dernière valeur d'un capteur, sa consommation moyenne entre deux dates, quels capteurs dépassent un seuil, quelle consommation est prévue, et quels événements sont anormaux.

| Exigence du MVP | Réalisation |
|---|---|
| Simulateur de capteurs | `simulator/simulator.py`, paramétrable, avec injection d'anomalies |
| Service de collecte | `collector-service`, validation et réponse HTTP 202 |
| Redis Streams | Stream `energy-measurements`, groupe `storage-group` |
| Service de stockage | `storage-service`, accusé après persistance |
| Cassandra | Table `measurements_by_sensor_day` |
| MongoDB | Collections `sensors` et `daily_summaries` |
| API REST de consultation | `analytics-service` |
| Moyenne et dépassements | `SummaryCalculator`, `AnomalyDetector` |
| Docker Compose | `docker-compose.yml`, compilation incluse dans les images |
| Swagger/OpenAPI | Sur les trois services |
| Preuve de bon fonctionnement | `scripts/e2e_test.py`, 12 scénarios |

Extensions réalisées : prévision par régression, tableau de bord Web, dead letter stream, métriques au format Prometheus exposées par Actuator.

Extensions non réalisées : API Gateway, JWT, Grafana, Kafka, Kubernetes, LSTM. Le sujet précise qu'ajouter une technologie n'est pas un objectif en soi : ces points n'étaient pas nécessaires à la démonstration, et le temps a été consacré à tester les scénarios de panne.

## 3. Architecture et justification des choix

```
Simulateur -> Collector -> Redis Streams -> Storage -> Cassandra (mesures)
                                               |-----> MongoDB (agrégats)
                                               `-----> Redis (dernière mesure)
Tableau de bord / Swagger -> Analytics -> Cassandra, MongoDB, Redis
```

Le schéma complet est disponible sur `http://localhost:8083/architecture.html`.

| Besoin | Choix | Justification | Alternative écartée |
|---|---|---|---|
| Découpler réception et stockage | Redis Streams | Journal persistant, groupes de consommateurs, accusés, très faible latence | Appel HTTP direct : une panne du stockage ferait perdre des mesures |
| Historique volumineux | Cassandra | Écritures rapides, lecture séquentielle dans une partition | MongoDB : possible, mais moins adapté à un flux d'écritures continu |
| Documents évolutifs | MongoDB | Schéma souple pour les capteurs (tags, étage) et les agrégats | Cassandra : il faudrait une table par requête |
| Dernière valeur | Cache Redis | Lecture fréquente, donnée petite, déjà dans l'infrastructure | Lecture Cassandra systématique, conservée comme repli |

Le découpage suit les responsabilités : le Collector ne connaît que Redis, le Storage est le seul à écrire, et Analytics ne fait que lire (hormis le CRUD des capteurs). Un service peut ainsi tomber sans arrêter les autres.

## 4. Modélisation des données

### Contrat de mesure

Le DTO `EnergyMeasurementRequest` porte les contraintes Jakarta Validation et le schéma se trouve dans `docs/measurement.schema.json`.

| Champ | Règle |
|---|---|
| `sensorId` | Obligatoire, 1 à 64 caractères alphanumériques, `-` ou `_` |
| `timestamp` | Obligatoire, ISO-8601, pas plus de 7 jours dans le passé ni 5 minutes dans le futur |
| `powerKw` | Obligatoire, positif ou nul |
| `voltage` | Obligatoire, entre 80 et 480 V |
| `temperature` | Facultative, entre -50 et 100 °C |

**Pourquoi un horodatage UTC ?** Il évite les ambiguïtés de fuseau et de changement d'heure. Deux capteurs de pays différents restent comparables, et le découpage en partitions journalières ne dépend pas du lieu.

**Que faire d'une mesure envoyée deux fois ?** Elle est acceptée, puis absorbée au stockage. La clé primaire Cassandra rend la seconde écriture identique à la première.

**Faut-il accepter une mesure très ancienne ?** Oui dans une limite de 7 jours, car un capteur peut avoir été hors ligne et renvoyer son tampon. Au-delà, il s'agit plus probablement d'une horloge déréglée. Cette limite est configurable.

**Erreur de format ou anomalie énergétique ?** Une erreur de format rend la donnée inexploitable : elle est refusée avec un HTTP 400. Une anomalie est une donnée valide au comportement inhabituel : elle doit être conservée, car c'est précisément ce qu'on veut analyser. Un pic à 15 kW n'est donc jamais rejeté par le Collector.

### Cassandra

La table est conçue à partir de la requête principale, l'historique d'un capteur entre deux dates.

```sql
PRIMARY KEY ((sensor_id, day), measured_at)
WITH CLUSTERING ORDER BY (measured_at DESC)
```

- `(sensor_id, day)` est la clé de partition. Avec `sensor_id` seul, une partition grossirait sans limite pendant des années. Le jour borne sa taille : à une mesure toutes les 2 secondes, une partition contient au plus 43 200 lignes.
- `measured_at` trie les lignes dans la partition, donc la lecture d'une plage est séquentielle et la plus récente arrive en tête.
- Une lecture sur plusieurs jours fait une requête par partition (`MeasurementQueryService.history`), jamais un scan de table. La période est limitée à 31 jours.

### MongoDB

`sensors` utilise `sensorId` comme `_id`. `daily_summaries` utilise `sensorId|date` comme `_id`, si bien que réécrire un agrégat remplace la version précédente.

## 5. Conception des services

**Collector Service.** Il valide, applique la politique de dates, publie avec `XADD`, puis répond 202. Le code 202 dit que la mesure est acceptée pour traitement, pas qu'elle est stockée. Si Redis est indisponible, il répond 503 pour que l'émetteur réessaie. Les erreurs de validation renvoient un 400 avec un message lisible par champ.

**Storage Service.** Il consomme le stream, écrit dans Cassandra, met à jour le cache et marque l'agrégat à recalculer, puis accuse le message.

**Analytics Service.** Il expose l'API de consultation, les calculs et le tableau de bord. Les calculs (`SummaryCalculator`, `AnomalyDetector`, `Forecaster`) sont des fonctions pures, sans accès aux bases, ce qui les rend simples à tester.

## 6. Gestion du flux Redis

Le thread `StreamConsumer` lit par lots de 100 avec `XREADGROUP`. La règle centrale est que `XACK` n'arrive qu'après une persistance réussie. Trois cas sont distingués :

| Cas | Exemple | Traitement |
|---|---|---|
| Succès | Mesure normale | `XACK` |
| Erreur définitive | Champ manquant, nombre illisible | Copie dans `energy-measurements-dlq`, puis `XACK` : rejouer ne servirait à rien et bloquerait le flux |
| Erreur temporaire | Cassandra indisponible | Pas d'`XACK` : le message reste dans la liste d'attente |

Les messages en attente sont relus au démarrage, puis toutes les 15 secondes, avec l'offset `0`. L'option `appendonly` de Redis et son volume protègent aussi le flux contre un redémarrage de Redis.

## 7. Persistance et idempotence

La livraison Redis est de type « au moins une fois » : un message traité dont l'accusé est perdu sera rejoué. La stratégie repose sur une conception tolérante aux doublons, à deux niveaux.

1. **Cassandra** : la clé `(capteur, jour, instant)` transforme un rejeu en réécriture de la même ligne.
2. **MongoDB** : l'agrégat n'est jamais incrémenté. Il est recalculé depuis Cassandra, la source de vérité, toutes les 10 secondes pour les couples capteur et jour modifiés. Un compteur incrémenté compterait deux fois un message rejoué, alors qu'un recalcul donne toujours le même résultat.

Le coût est un agrégat en retard de 10 secondes au plus et une relecture de la partition du jour. C'est acceptable pour un résumé quotidien, et ce serait à revoir avec des milliers de capteurs. Le scénario 8 envoie trois fois la même mesure et vérifie une seule ligne et `measurementCount = 1`.

## 8. Analytics et IA

**Indicateurs.** Moyenne, minimum, maximum, nombre de mesures, dépassements et énergie, avec `E = Σ Pi × Δti`. Le `Δti` est l'écart réel entre deux mesures, en heures. Un écart de plus de 5 minutes est traité comme un trou dans les données et n'ajoute pas d'énergie, sinon une panne de capteur serait comptée comme une consommation continue.

**Anomalies.** Deux méthodes sont combinées : le seuil `P > Pmax` propre au capteur, et le score `z = (P - µ) / σ` avec `|z| > 3`. Le seuil de 3 correspond à environ 0,3 % de faux positifs pour un signal gaussien.

Cette détection a une limite observée : µ et σ sont calculés sur la période demandée, pics inclus. Beaucoup de pics augmentent σ et diminuent le score de chaque pic : sur le tableau de bord, un pic à 15 kW obtient z ≈ 3,6 sur une heure. De plus, un signal périodique a un σ élevé par nature. Une médiane glissante serait plus robuste.

**Prévision.** Le modèle est une régression harmonique par moindres carrés :

`P(t) = b0 + b1·t + b2·sin(2πt/T) + b3·cos(2πt/T)`

| Point demandé | Choix |
|---|---|
| Fenêtre d'entrée | La période demandée, 24 h par défaut (6 h sur le tableau de bord) |
| Variables | Temps uniquement : tendance et composantes périodiques |
| Valeurs manquantes | Aucun rééchantillonnage : la régression accepte des points irréguliers |
| Normalisation | Temps divisé par la période T ; pas de normalisation de la puissance, inutile ici |
| Préparation | Les anomalies détectées sont retirées de l'apprentissage |
| Séparation | Chronologique : 80 % d'entraînement, 20 % de test. Un découpage aléatoire laisserait le modèle voir le futur |
| Évaluation | MAE et RMSE sur le test, renvoyées par l'API |

Résultat observé sur SENSOR-003 : MAE = 0,134 kW et RMSE = 0,169 kW. Le bruit du simulateur a un écart-type de 0,15 kW, ce qui donne une MAE minimale théorique d'environ 0,12 kW : le modèle est donc proche du mieux possible.

Il faut lire ce résultat avec prudence : le modèle a la même forme que le simulateur et connaît sa période T. Il ne prouve rien sur de vraies données, où plusieurs cycles se superposent (jour, semaine, météo). Il faudrait alors plusieurs harmoniques ou un LSTM, exporté en ONNX et chargé avec DJL. Cette voie n'a pas été réalisée, car la régression ne demande ni dépendance ni phase d'entraînement séparée.

## 9. Conteneurisation et déploiement

Chaque service a un Dockerfile en deux étapes : compilation dans une image Maven, exécution dans une image JRE. Il suffit donc de Docker pour lancer le projet, sans JDK local.

L'ordre de démarrage est piloté par des healthchecks. Le service `cassandra-init` exécute `scripts/init-cassandra.cql` puis s'arrête, et le Storage attend sa réussite (`service_completed_successfully`). Les données vivent dans trois volumes. La mémoire des JVM est limitée à 256 Mo et celle de Cassandra à 768 Mo, pour tenir dans les 5 Go alloués à Docker.

## 10. Tests et résultats

**Tests unitaires** (JUnit, MockMvc, AssertJ) : validation du contrat et politique de dates, transformation d'un événement en entité, calculs d'énergie, agrégats, anomalies et prévision.

**Tests de bout en bout** : `scripts/e2e_test.py` pilote la vraie plateforme, y compris les pannes, et produit le tableau de preuves `docs/test-results.md`. Il couvre les liaisons API → Redis, Redis → Storage, Storage → Cassandra, Storage → MongoDB et la lecture par API. Ces tests remplacent des tests d'intégration Testcontainers, qui n'ont pas été écrits.

**Dernier résultat : 12 scénarios réussis sur 12.** Le tableau complet est dans `docs/test-results.md`.

**Test du simulateur** : 5 capteurs pendant 2 minutes ont produit 290 mesures, toutes acceptées. On pouvait en attendre 300 à 2 secondes d'intervalle ; l'écart vient du temps d'envoi, qui s'ajoute à l'attente entre deux tours.

**Expérience de charge** (`docs/load-results.md`, 2000 requêtes par palier) :

| Clients | Débit | Latence p50 | Latence p95 | Erreurs | File Redis | Absorption |
|---|---|---|---|---|---|---|
| 1 | 70 req/s | 13,7 ms | 28,6 ms | 0 | 0 | 0,3 s |
| 10 | 226 req/s | 38,2 ms | 91,9 ms | 0 | 0 | 0,2 s |
| 50 | 402 req/s | 116,9 ms | 235,8 ms | 0 | 1016 | 2,2 s |

Le débit augmente avec la concurrence, mais la latence aussi : le système approche de sa limite sur ce poste. À 50 clients, le Collector va plus vite que le Storage, et la file Redis atteint 1016 messages avant d'être absorbée en 2,2 secondes, sans erreur. C'est exactement le rôle de tampon attendu. La lecture de 2000 lignes d'historique prend de 100 à 210 ms. Ces chiffres sont un ordre de grandeur : le client Python partage la machine avec la plateforme, et chaque requête ouvre une connexion.

## 11. Sécurité, limites et difficultés

**Note de sécurité**

| Élément | Mesure |
|---|---|
| Secrets | Mots de passe Redis et MongoDB dans `.env`, exclu de Git ; `.env.example` ne contient que des valeurs factices |
| Configuration | Variables d'environnement, aucune valeur réelle dans le code |
| Entrées | Validation au Collector et à l'API, rejet des événements corrompus au Storage |
| Bases de données | Aucun port publié sur l'hôte, accès uniquement par le réseau Docker |
| Accès exposés | 8081, 8082, 8083 |

Endpoints sensibles, **non protégés dans cette version** : `POST /api/measurements`, `POST` et `PUT /api/sensors`, ainsi que `/actuator`. Cassandra fonctionne aussi sans authentification, protégée uniquement par le réseau Docker. Il n'y a ni JWT ni HTTPS : en production, il faudrait Spring Security, des rôles ADMIN et VIEWER, et un reverse proxy TLS.

**Limites connues**

- Le stream Redis n'est pas purgé : il grossit jusqu'à la mémoire disponible. Il faudrait un `XTRIM` borné par le dernier message accusé.
- Il n'y a qu'un consommateur. Le passage à plusieurs instances demande `XAUTOCLAIM`, pour reprendre les messages d'une instance tombée, et un nom de consommateur par instance.
- Cassandra tourne sur un seul nœud avec un facteur de réplication de 1 : aucune tolérance à la perte du nœud.
- Si le Storage s'arrête juste après l'écriture, avant le recalcul, l'agrégat n'est rafraîchi qu'à la prochaine mesure de ce capteur et de ce jour.
- Les classes `MeasurementEntity` et `MeasurementKey` sont dupliquées entre Storage et Analytics, de même que le calcul d'énergie. C'est un compromis pour garder des services indépendants, à remplacer par une bibliothèque partagée si le projet grandit.
- Deux mesures d'un même capteur à la même milliseconde mais de valeurs différentes s'écrasent.

**Difficultés rencontrées**

- La mémoire : Cassandra et trois JVM sur 5 Go ont imposé de limiter chaque composant.
- L'ordre de démarrage : le schéma Cassandra doit exister avant le Storage, d'où le service d'initialisation et les healthchecks.
- L'idempotence des agrégats : un compteur incrémenté était plus simple, mais faux en cas de rejeu.

## 12. Améliorations possibles

1. Purger le stream et gérer plusieurs consommateurs.
2. Ajouter Spring Security avec JWT, puis une API Gateway comme point d'entrée unique.
3. Connecter Prometheus et Grafana aux métriques déjà exposées.
4. Rendre la détection robuste avec une médiane glissante.
5. Comparer la régression à un LSTM sur de vraies données.
6. Envoyer une notification en cas d'anomalie.

## 13. Conclusion

La chaîne complète fonctionne et résiste aux pannes testées : aucune mesure n'est perdue quand le Storage ou Cassandra s'arrête, et les doublons ne faussent pas les résultats. L'enseignement principal du projet est que chaque base a un rôle précis, et que la fiabilité vient moins des outils que de quelques décisions : accuser après la persistance, concevoir la clé Cassandra à partir de la requête, et recalculer plutôt qu'incrémenter.
