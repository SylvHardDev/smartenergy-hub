// Exécuté une seule fois par l'image mongo, au premier démarrage du volume.
const smartenergy = db.getSiblingDB('smartenergy');
smartenergy.sensors.insertMany([
  { _id: 'SENSOR-001', name: 'Compteur salle serveur', building: 'Bâtiment A', floor: 1, maxPowerKw: 8.0, active: true, tags: ['critical', 'server-room'] },
  { _id: 'SENSOR-002', name: 'Climatisation étage 2', building: 'Bâtiment A', floor: 2, maxPowerKw: 8.0, active: true, tags: ['hvac'] },
  { _id: 'SENSOR-003', name: 'Atelier production', building: 'Bâtiment B', floor: 0, maxPowerKw: 8.0, active: true, tags: ['critical', 'production'] },
  { _id: 'SENSOR-004', name: 'Bureaux open space', building: 'Bâtiment B', floor: 1, maxPowerKw: 8.0, active: true, tags: ['office'] },
  { _id: 'SENSOR-005', name: 'Éclairage parking', building: 'Bâtiment C', floor: -1, maxPowerKw: 8.0, active: true, tags: ['lighting'] }
]);
smartenergy.daily_summaries.createIndex({ sensorId: 1, date: -1 });
