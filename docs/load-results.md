# Expérience de charge

Exécution : 06/11/2026 11:07, 2000 requêtes par palier, sur un poste de développement.
Il s'agit d'un ordre de grandeur, pas d'un benchmark : le client Python partage la machine avec la plateforme.

| Clients simultanés | Débit (req/s) | Latence p50 (ms) | Latence p95 (ms) | Erreurs | File Redis en fin d'envoi | Absorption (s) | Lignes lues | Lecture historique (ms) |
|---|---|---|---|---|---|---|---|---|
| 1 | 70 | 13.7 | 28.6 | 0 | 0 | 0.3 | 2000 | 212 |
| 10 | 226 | 38.2 | 91.9 | 0 | 0 | 0.2 | 2000 | 111 |
| 50 | 402 | 116.9 | 235.8 | 0 | 1016 | 2.2 | 2000 | 102 |
