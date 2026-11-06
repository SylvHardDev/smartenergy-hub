# Preuves de tests de bout en bout

Exécution : 06/11/2026 11:17, avec `python scripts/e2e_test.py`.

| N° | Scénario | Résultat attendu | Résultat obtenu | Statut |
|---|---|---|---|---|
| 1 | Envoi d'une mesure valide | HTTP 202 + événement Redis + ligne Cassandra | HTTP 202, +1 événement, stocké=True | OK |
| 2 | Mesure sans sensorId | HTTP 400, aucun événement | HTTP 400, +0 événement, message : sensorId est obligatoire | OK |
| 3 | Lecture de l'historique | Liste triée du plus récent au plus ancien | 10 mesures, ordre décroissant | OK |
| 4 | Injection d'un pic | Anomalie détectée | pic de 15 kW pour un seuil de 8 kW, détecté=True | OK |
| 5 | Arrêt du Storage Service | Messages conservés dans Redis | 50 mesures acceptées, 50 en attente dans Redis | OK |
| 6 | Redémarrage du Storage Service | Reprise, aucune mesure perdue | 50/50 mesures retrouvées, lag=0 | OK |
| 7 | Panne de Cassandra pendant un traitement | Pas d'accusé, message rejoué après retour | 1 message en attente pendant la panne, rejoué=True, file vide=True | OK |
| 8 | Livraison en double (idempotence) | Une seule ligne, agrégat inchangé | 3 envois, 1 ligne Cassandra, measurementCount MongoDB=1 | OK |
| 9 | Événement corrompu dans le stream | Isolé en dead letter, le consumer continue | +1 en dead letter, mesure suivante traitée=True | OK |
| 10 | Agrégat MongoDB après passage dans la chaîne | Résumé quotidien cohérent avec Cassandra | MongoDB=50, Cassandra=50 | OK |
| 11 | Redémarrage complet de la plateforme | Données conservées (volumes Docker) | 10/10 mesures, 5 capteurs | OK |
| 12 | Santé et métriques | Actuator UP sur les 3 services, métriques disponibles | santé=['UP', 'UP', 'UP'], métriques HTTP 200/200/200 | OK |
