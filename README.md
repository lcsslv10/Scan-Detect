# Scan & Detect

Outil de découverte d'hôtes sur un sous-réseau `/24` et de scan de ports TCP en Java (mode console et interface graphique Swing), pensé pour comprendre les mécanismes réseau et tester des machines en environnement local ou de laboratoire (homelab).

## Utilisation responsable

Cet outil est conçu uniquement à des fins pédagogiques, d'administration système et de tests sur vos propres équipements, réseaux locaux ou environnements virtualisés isolés (ex. VirtualBox / homelab). N'utilisez jamais ce programme pour scanner ou sonder des machines, adresses IP ou réseaux sans autorisation explicite de leur propriétaire.

## Fonctionnalités

- **Découverte de machines réseau (`/24`)** : test des adresses `.1` à `.254` via `InetAddress.isReachable()` et sondage TCP rapide sur quelques ports courants, avec récupération du nom d'hôte lorsque disponible.
- **Scan de ports TCP multi-threadé** : utilisation d'un `ExecutorService` pour scanner des plages de ports (de `1` à `65535`) avec timeout configurable et interruption propre (`Arrêter`).
- **Identification de service & bannière** : détection des services courants et lecture d'une bannière basique quand un port est ouvert.
- **Interface graphique Swing** : tableau des machines actives (clic pour sélectionner la cible), barre de progression, journal des résultats et bouton d'effacement.

## Prérequis

- **Java (JDK 17+)** installé sur la machine.

## Compilation et exécution

Depuis le dossier `src/` :

```bash
javac PortScanner.java ScannerGUI.java
java ScannerGUI