# Présentation vidéo : flux et script (10 minutes)

## Flux

| Temps | Durée | Partie | Écran |
|---|---|---|---|
| 0:00 | 0:40 | 1. Introduction | Tableau de bord |
| 0:40 | 2:00 | 2. Architecture | `architecture.html`, puis arborescence du projet |
| 2:40 | 1:50 | 3. Démonstration | Terminal, tableau de bord, Swagger, terminal |
| 4:30 | 1:15 | 4. Panne en direct | Terminal, tableau de bord |
| 5:45 | 3:00 | 5. Code (5 extraits) | Éditeur |
| 8:45 | 0:40 | 6. Tests et charge | `docs/test-results.md` |
| 9:25 | 0:35 | 7. Limites et conclusion | Tableau de bord |

## Préparation (hors enregistrement)

Terminal 1, dans `smartenergy-hub` :

```powershell
python simulator/simulator.py --backfill-hours 1
```

`--backfill-hours 1` comble le trou depuis ton dernier essai. Ne mets pas plus : ta base contient déjà l'historique depuis ce matin. Si plus d'une heure s'est écoulée depuis le dernier arrêt du simulateur, augmente la valeur d'autant.

Laisse-le tourner pendant toute la vidéo et attends au moins 3 minutes pour voir des pics en direct.

Ouvre aussi :

- Terminal 2 dans `smartenergy-hub`, avec les commandes ci-dessous prêtes à copier
- Onglet 1 : `localhost:8083/architecture.html`
- Onglet 2 : `localhost:8083`, capteur SENSOR-003, période 1 heure
- Onglet 3 : `localhost:8081/swagger-ui.html`, avec `POST /api/measurements` déplié et « Try it out » activé
- Éditeur, zoom augmenté, avec les 7 fichiers de la partie 5 déjà ouverts dans l'ordre

Commandes à garder sous la main :

```powershell
docker compose ps
docker compose exec cassandra cqlsh -e "SELECT * FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='SENSOR-003' AND day='2026-11-06' LIMIT 5;"
docker compose exec mongo mongosh -u smartenergy -p change-me-mongo --authenticationDatabase admin smartenergy --quiet --eval "db.daily_summaries.findOne({sensorId:'SENSOR-003'})"
docker compose stop storage-service
docker compose exec redis redis-cli XINFO GROUPS energy-measurements
docker compose start storage-service
```

Si quelque chose bloque : `docker compose ps`, puis `docker compose logs storage-service --tail 20`.

## 1. Introduction (0:00 - 0:40)

**Écran : tableau de bord.**

> Bonjour, je suis [ton nom], et je vous présente SmartEnergy Hub, mon projet de Master 1. C'est une plateforme de supervision énergétique : elle collecte en continu les mesures de capteurs, les stocke, les analyse et les affiche.
>
> Le défi, c'est qu'un flux de mesures ne s'arrête jamais. Il faut le recevoir sans bloquer, garder un gros historique, répondre vite, et ne rien perdre si un composant tombe. Une seule base de données répond mal à tous ces besoins en même temps. Je vais vous montrer l'architecture que j'ai construite, la faire tourner, provoquer une panne, puis expliquer le code qui la rend fiable.

## 2. Architecture (0:40 - 2:40)

**Écran : `architecture.html`. Suis chaque bloc à la souris, de gauche à droite.**

> Commençons par le chemin d'écriture, en haut du schéma.
>
> Le simulateur joue le rôle de cinq capteurs. Il envoie ses mesures en HTTP au Collector Service, écrit en Spring Boot. Le Collector fait une seule chose : valider la mesure et la publier. Il répond alors 202, qui veut dire « accepté pour traitement » et non « stocké ». C'est volontaire : le capteur n'attend jamais les bases de données.
>
> La mesure entre ensuite dans Redis Streams. C'est le point clé de l'architecture : Redis sert de tampon entre la réception et le stockage. Si le stockage ralentit ou tombe, les mesures attendent ici, sans être perdues.
>
> Le Storage Service consomme ce flux à travers un consumer group, puis il écrit dans les bases.

**Descends vers les trois bases.**

> J'ai trois bases, parce que j'ai trois besoins différents.
>
> Cassandra stocke les séries temporelles : elle encaisse un flux continu d'écritures et lit très vite l'historique d'un capteur. MongoDB stocke les capteurs et les agrégats quotidiens, qui sont des documents à la structure souple. Et Redis sert aussi de cache pour la dernière valeur de chaque capteur, la donnée la plus souvent lue.

**Descends vers Analytics.**

> Côté lecture, l'Analytics Service expose l'API REST, calcule les indicateurs, détecte les anomalies et fait la prévision. Il alimente le tableau de bord et Swagger.

**Écran : arborescence du projet dans l'éditeur.**

> Dans le dépôt, on retrouve exactement ce découpage : un dossier par service, le simulateur, les scripts d'initialisation des bases, et la documentation. Chaque service a son Dockerfile, et un seul fichier Docker Compose démarre tout. La compilation se fait dans les images : il suffit donc de Docker pour lancer le projet, sans installer Java.

## 3. Démonstration (2:40 - 4:30)

**Écran : terminal 2. Lance `docker compose ps`.**

> Voici la plateforme en marche : les trois bases et les trois services Spring Boot. Le simulateur tourne dans un autre terminal.

**Écran : tableau de bord.**

> Le tableau de bord se rafraîchit toutes les 5 secondes. Pour le capteur 3, je vois la dernière mesure, la moyenne, le maximum, l'énergie estimée et le nombre de dépassements.
>
> La courbe verte, ce sont les mesures. La ligne rouge, c'est le seuil de 8 kilowatts de ce capteur. Les points rouges sont les anomalies : le simulateur multiplie volontairement la puissance par trois de temps en temps. En pointillés bleus, c'est la prévision, et son erreur moyenne est affichée en bas à droite.

**Écran : Swagger. Clique sur Execute avec l'exemple.**

> J'envoie maintenant une mesure à la main. Elle est valide : réponse 202.

**Supprime la ligne `sensorId`, puis Execute.**

> Sans identifiant de capteur, j'obtiens une erreur 400 avec un message clair, et rien n'entre dans Redis.

**Remets `sensorId`, mets `"powerKw": -5`, puis Execute.**

> Une puissance négative est refusée aussi. Je fais une distinction importante : une valeur impossible est une erreur de format, donc je la refuse. Un pic à 15 kilowatts est une anomalie : la donnée est valide, et je la garde, parce que c'est justement ce qu'on veut analyser.

**Écran : terminal. Lance la commande `cqlsh`, puis `mongosh`.**

> Dans Cassandra, je retrouve les mesures, de la plus récente à la plus ancienne. Et dans MongoDB, l'agrégat du jour pour ce capteur.

## 4. Panne en direct (4:30 - 5:45)

**Écran : terminal. Lance `docker compose stop storage-service`.**

> Voici maintenant le test le plus important : j'arrête le service de stockage pendant que les capteurs continuent d'émettre.

**Lance `XINFO GROUPS` et montre la ligne sous `lag`.**

> Le champ `lag` compte les messages que le groupe n'a pas encore lus.

**Parle pendant 15 secondes, puis relance `XINFO GROUPS`.**

> Du point de vue des capteurs, rien n'a changé : le Collector répond toujours 202, car il ne dépend que de Redis. C'est tout l'intérêt du découplage. Et on voit que le `lag` a augmenté : les mesures s'accumulent dans Redis.

**Lance `docker compose start storage-service`.**

> Je relance le service. Il reprend là où il s'était arrêté.

**Parle pendant 15 à 20 secondes, puis relance `XINFO GROUPS`.**

> Le service met quelques secondes à démarrer, puis il rattrape son retard. Le `lag` est revenu à zéro : tout a été traité.

**Écran : tableau de bord.**

> Et sur la courbe, il n'y a aucun trou : aucune mesure n'a été perdue. Voyons maintenant comment le code garantit cela.

## 5. Code (5:45 - 8:45)

### Extrait 1 (35 s) : la réception

**Écran : `collector-service/.../api/EnergyMeasurementRequest.java`, puis `MeasurementController.java`, lignes 32 à 35.**

> Le contrat de données est un record Java, avec les règles de validation directement sur les champs : identifiant obligatoire, puissance positive, tension dans une plage plausible.
>
> Le contrôleur est très court. L'annotation `@Valid` déclenche la validation, je vérifie que la date est plausible, je publie dans Redis, et je réponds 202. Il n'y a aucune logique de stockage ici : c'est ce qui rend la réception rapide.

### Extrait 2 (55 s) : le cœur du projet

**Écran : `storage-service/.../consumer/StreamConsumer.java`, ligne 119, méthode `handleOne`.**

> Voici la méthode la plus importante du projet. La règle est simple : j'accuse réception du message seulement après la persistance. Regardez l'ordre, ligne 121 je traite, ligne 122 j'accuse. Si le service plante entre les deux, Redis considère le message comme non traité et il sera rejoué.
>
> Ensuite, je distingue deux types d'erreurs. Un événement corrompu ne guérira jamais : le rejouer bloquerait le flux, donc je l'isole dans un stream de dead letter, puis je l'accuse. En revanche, une panne de Cassandra est temporaire : je n'accuse pas, le message reste en attente, et il est relu toutes les 15 secondes. J'ai testé ce cas en arrêtant Cassandra : le message est bien rejoué à son retour.

### Extrait 3 (30 s) : la modélisation Cassandra

**Écran : `scripts/init-cassandra.cql`, ligne 14.**

> Avec Cassandra, on conçoit la table à partir de la requête, ici « l'historique d'un capteur entre deux dates ». La clé de partition combine le capteur et le jour : avec le capteur seul, une partition grossirait sans limite pendant des années.
>
> Cette clé apporte aussi l'idempotence : la même mesure donne la même clé, donc rejouer un message réécrit simplement la même ligne.

### Extrait 4 (30 s) : l'agrégat MongoDB

**Écran : `storage-service/.../service/DailyAggregationService.java`, ligne 46, méthode `refresh`.**

> Reste un piège : puisqu'un message peut être rejoué, un compteur incrémenté compterait certaines mesures deux fois. Je n'incrémente donc jamais. Toutes les 10 secondes, je recalcule l'agrégat depuis Cassandra, qui est la source de vérité. Le résultat est toujours le même, au prix de 10 secondes de retard, ce qui est acceptable pour un résumé quotidien.

### Extrait 5 (30 s) : l'analyse

**Écran : `analytics-service/.../service/AnomalyDetector.java`, ligne 23, puis `Forecaster.java`, ligne 27.**

> Pour les anomalies, je combine deux méthodes : le seuil propre à chaque capteur, et un score z, qui mesure l'écart à la moyenne en nombre d'écarts-types. Le second détecte un comportement inhabituel même sous le seuil.
>
> La prévision est une régression avec une tendance et une composante périodique. Je sépare les données dans l'ordre chronologique, 80 % pour l'entraînement et 20 % pour le test, parce qu'un découpage aléatoire laisserait le modèle voir le futur.

## 6. Tests et charge (8:45 - 9:25)

**Écran : `docs/test-results.md` en aperçu.**

> En plus des tests unitaires, j'ai écrit un script qui pilote la vraie plateforme, y compris les pannes. Les 12 scénarios passent : l'arrêt du Storage, l'arrêt de Cassandra, les doublons, un message corrompu, et le redémarrage complet avec conservation des données.

**Écran : `docs/load-results.md`.**

> J'ai aussi fait monter la charge. À 50 clients simultanés, on atteint environ 400 requêtes par seconde sans erreur. La file Redis monte à environ 1000 messages, puis elle se vide en 2 secondes : le tampon joue son rôle. Ce sont des ordres de grandeur, mesurés sur mon poste.

## 7. Limites et conclusion (9:25 - 10:00)

**Écran : tableau de bord.**

> Le projet a des limites, que je détaille dans le rapport : les API ne sont pas encore authentifiées, le stream Redis n'est pas purgé, et il n'y a qu'un consommateur et un seul nœud Cassandra. Ma prévision marche bien aussi parce qu'elle a la même forme que le simulateur : sur de vraies données, il faudrait un modèle plus riche.
>
> Ce que je retiens, c'est que la fiabilité vient de quelques décisions précises : accuser après la persistance, concevoir la clé à partir de la requête, et recalculer plutôt qu'incrémenter. Merci de votre attention.

## Si tu dépasses le temps

1. Coupe l'extrait 5.
2. Coupe la commande `mongosh`.
3. Raccourcis la partie 6 à la phrase sur les 12 scénarios.

Garde toujours l'architecture, la panne et `handleOne`.

## Questions à savoir expliquer

- **Pourquoi 202 et pas 201 ?** Rien n'est encore stocké au moment de la réponse.
- **Que se passe-t-il si Redis tombe ?** Le Collector répond 503 et l'émetteur doit réessayer. Le fichier AOF conserve le flux.
- **Différence entre `lag` et messages en attente ?** Le `lag` compte les messages jamais livrés au groupe. Les messages en attente ont été livrés mais pas accusés.
- **Pourquoi ne pas tout mettre dans MongoDB ?** C'est possible à petite échelle, mais chaque base a ici un rôle : Cassandra est faite pour un flux continu d'écritures et des lectures par clé.
- **Pourquoi pas de LSTM ?** La régression suffit pour un signal périodique et s'évalue honnêtement. Un LSTM reste une amélioration possible.
