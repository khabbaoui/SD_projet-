
#  Smart City – Système Distribué de Supervision des Ressources Urbaines

##  Description

Ce projet met en œuvre un système distribué temps réel permettant de simuler, analyser, stocker et visualiser les données provenant de capteurs urbains dans le contexte d'une ville intelligente. Il gère plusieurs types de ressources : eau, énergie, etc.

Les données sont simulées via un code Java, transmises via Apache Kafka, analysées par un service centralisé, stockées dans InfluxDB et visualisées avec Grafana.

---

##  Architecture du Projet

```
[ Capteurs Simulés (Java) ]
          │
     → Apache Kafka
          │
     → Service REST (JAX-RS)
          │
     → Analyse + Alertes
          │
     ├──→ InfluxDB (stockage)
     └──→ Grafana (visualisation)
```
![architecture](https://github.com/user-attachments/assets/73c5f0ff-a34b-497b-8c78-aa0bcd276f48)

---
![Description de l'image](assets/images/mon-image.png)


##  Fonctionnalités

-  Simulation de capteurs (eau, énergie, trafic)
-  Envoi des données vers Kafka (format JSON)
-  API REST pour démarrer les consommateurs Kafka
-  Génération d'alertes en cas d'anomalie
-  Stockage dans InfluxDB
-  Dashboard interactif via Grafana

---

## Technologies Utilisées

- Java 21
- Apache Kafka
- Jersey (JAX-RS)
- InfluxDB
- Grafana
- Maven
- Jetty (serveur embarqué)

---
---

### Étapes :
### Prérequis

- Java 21
- Maven
- Kafka & Zookeeper installés
- InfluxDB installé et en cours d'exécution
- Grafana configuré avec InfluxDB comme datasource

##  Lancement du Projet
###  Avec Docker Compose 

 1. tu peux lancer tous les services (Zookeeper, Kafka, InfluxDB, Grafana) à l'aide de Docker Compose.

2. Créer les topics :
   ```bash
   kafka-topics.sh --create --topic consommation-eau --bootstrap-server localhost:9092
   kafka-topics.sh --create --topic energy-usage --bootstrap-server localhost:9092
   kafka-topics.sh --create --topic traffic-data --bootstrap-server localhost:9092
   ```

3. Lancer le projet :
   ```bash
   mvn clean install
   mvn jetty:run
   ```

4. Appeler l’API REST pour démarrer les consommateurs :
   ```
   GET http://localhost:8080/api/sensor/start-consumer
   ```

##  Auteur

- **Nom :** KHABBAOUI Othmane-Imrane RABAH -HAFID Haitam
- **Projet :** Mini-projet - Systèmes Distribués
- **Encadrante :** M. GUERMAH Hatim

---

##  Licence

Projet académique réalisé dans le cadre d’un cours universitaire. Usage libre à des fins pédagogiques.
