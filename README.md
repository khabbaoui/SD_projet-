Projet de Ville Intelligente – Smart City IoT
Ce projet simule une ville intelligente (Smart City) qui centralise les données générées par plusieurs capteurs IoT simulés. Il permet de suivre en temps réel les mesures suivantes :

🚗 Le trafic routier

⚡ La consommation d’énergie

💧 La consommation d’eau

Les données sont générées par des producteurs Kafka, stockées dans InfluxDB via un consumer Kafka, puis visualisées grâce à Grafana sur des tableaux de bord dynamiques.

📌 Objectifs
Centraliser les données de capteurs simulés

Utiliser Kafka pour gérer les flux de données

Stocker les données dans InfluxDB

Visualiser les données avec Grafana

🧱 Architecture du projet
mermaid
graph TD
  A[Capteurs simulés<br>(Producers Kafka)] -->|Envoi données| B[Topics Kafka]
  B --> C[Consumer Kafka]
  C -->|Insertion| D[InfluxDB]
  D --> E[Tableaux de bord<br>Grafana]
🛠️ Technologies utilisées
Apache Kafka & Zookeeper : pour le streaming des données

InfluxDB : base de données de séries temporelles

Grafana : visualisation des données

Java (Maven) : pour l’implémentation des producteurs et du consommateur

Docker Compose : pour orchestrer les conteneurs

📁 Structure du projet
bash
smart-city-project/
│
├── docker-compose.yml         # Déploiement de Kafka, InfluxDB, Grafana
├── producer/                  # Code des producteurs Kafka (simulateurs de capteurs)
├── consumer/                  # Code du consommateur Kafka
├── pom.xml                    # Dépendances Maven
└── README.md                  # Fichier d'explication du projet
🚀 Lancer le projet
1. Cloner le projet
bash
git clone https://github.com/ton-utilisateur/smart-city-project.git
cd smart-city-project
2. Démarrer les services avec Docker Compose
bash
docker-compose up -d
Cela lance :

Zookeeper (nécessaire à Kafka)

Kafka

InfluxDB

Grafana

3. Installer les dépendances Java
Dans le dossier du projet :

bash
mvn clean install
4. Exécuter les producteurs et le consommateur
Exécuter les producteurs qui simulent les capteurs :

bash
java -jar producer/target/producer.jar
Exécuter le consommateur Kafka :

bash
java -jar consumer/target/consumer.jar
Tu verras dans le terminal :

La génération des données pour les 3 capteurs

Le consommateur qui lit et insère dans InfluxDB

📊 Accéder à Grafana
Ouvre Grafana via http://localhost:3000

Connecte-toi avec les identifiants par défaut :

Username: admin

Password: admin

Ajoute une data source InfluxDB

Crée des tableaux de bord pour visualiser :

Le trafic

La consommation d’énergie

La consommation d’eau

📦 Kafka – Topics utilisés
Les producteurs envoient les données dans les topics suivants :

traffic-sensor

energy-sensor

water-sensor

📚 Exemple de données générées
json
{
  "sensorId": "sensor-1",
  "type": "energy",
  "value": 56.2,
  "timestamp": "2025-05-22T10:21:00Z"
}
✅ À faire
 Simuler des capteurs

 Connecter Kafka à InfluxDB

 Visualiser les données sur Grafana

 Ajouter une interface utilisateur Web (optionnel)

 Ajouter l’authentification Grafana et sécuriser les accès

📄 Licence
Ce projet est sous licence MIT. Tu peux l’utiliser, le modifier et le distribuer librement.
