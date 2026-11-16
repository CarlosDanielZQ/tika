# Tâche 3 — Automatisation des tests de sécurité avec Semgrep

Réalisé par David Correa Ruiz et Carlos Daniel Zelaya Quinteros.

## 1. Objectif et intégration de Semgrep dans GitHub Actions

L'objectif de cette tâche est d'intégrer un outil d'analyse statique de sécurité, Semgrep, au processus d'intégration continue du projet Apache Tika. Cette intégration vise à détecter les vulnérabilités introduites par de nouvelles modifications et à provoquer l'échec du contrôle de sécurité lorsqu'une nouvelle vulnérabilité bloquante est détectée.

### 1.1. Présentation

Afin d'automatiser la détection des vulnérabilités de sécurité dans Apache Tika, nous avons intégré Semgrep aux workflows GitHub Actions du projet.

Semgrep est un outil d'analyse statique de code (SAST) permettant d'identifier des constructions potentiellement vulnérables sans exécuter l'application. Notre intégration utilise les règles Java du registre Semgrep et un mécanisme d'analyse différentielle pour distinguer les vulnérabilités nouvellement introduites de celles déjà présentes dans le projet.

Deux workflows ont été utilisés :

- `.github/workflows/main-jdk17-build.yml` : workflow principal du projet, responsable de la compilation Maven, des tests et du contrôle Semgrep.
- `.github/workflows/semgrep-validation.yml` : workflow indépendant créé pour valider la détection différentielle sans relancer les tests Maven du projet complet.

Le développement et les validations ont été réalisés sur la branche `tache3-semgrep` du dépôt [CarlosDanielZQ/tika](https://github.com/CarlosDanielZQ/tika).

### 1.2. Modification du workflow principal

Le workflow `main-jdk17-build.yml` contenait initialement les jobs `build` et `e2e-tests`, chargés respectivement de la compilation et des tests Maven, puis des tests de bout en bout.

Nous avons ajouté un job nommé `semgrep`, destiné à exécuter une analyse statique de sécurité.

Le job utilise `ubuntu-latest` et comporte trois étapes principales :

1. Récupération du dépôt avec `actions/checkout@v6`.
2. Installation de Semgrep avec `python -m pip install semgrep`.
3. Analyse du code avec `semgrep scan` et comparaison avec le commit Git de référence.

Le paramètre `fetch-depth: 0` a été ajouté à l'étape de récupération du dépôt pour disposer de l'historique Git complet, nécessaire à la détermination du commit de référence.

Les permissions du jeton GitHub ont été limitées à `contents: read`, conformément au principe du moindre privilège.

Le workflow dispose également du déclencheur `workflow_dispatch`, permettant son lancement manuel. Il peut être déclenché par les pushes vers `main` et par les Pull Requests ciblant `main` ou `tache3-semgrep`, à l'exception des modifications portant uniquement sur les chemins ignorés sous `docs/**`.

### 1.3. Mise en place de l'analyse différentielle

La commande de sécurité utilisée est la suivante :

```bash
semgrep scan \
  --config p/java \
  --baseline-commit "$(git merge-base HEAD origin/main)" \
  --error \
  --metrics=off \
  .
```

Les paramètres remplissent les fonctions suivantes :

| Paramètre                         | Justification                                                                              |
| --------------------------------- | ------------------------------------------------------------------------------------------ |
| `--config p/java`                 | Charger les règles d'analyse Java du registre Semgrep.                                     |
| `--baseline-commit`               | Comparer l'analyse actuelle avec celle d'un commit de référence.                           |
| `git merge-base HEAD origin/main` | Déterminer l'ancêtre commun entre le commit analysé et la branche principale.              |
| `--error`                         | Retourner un code de sortie non nul lorsque de nouveaux résultats bloquants sont détectés. |
| `--metrics=off`                   | Désactiver la collecte des métriques Semgrep.                                              |
| `.`                               | Utiliser le répertoire courant comme cible de l'analyse.                                   |

L'utilisation de `git merge-base` permet de sélectionner un commit commun comme référence plutôt que de comparer arbitrairement le nouveau code au dernier état de `main`.

Cette approche évite que les résultats déjà présents dans la version de référence provoquent à eux seuls l'échec du contrôle.

Une limite demeure : cette référence représente l'ancêtre commun des branches et pas nécessairement le parent immédiat de chaque nouveau commit. Le contrôle identifie donc les nouveaux résultats par rapport à cette référence Git, et non exclusivement ceux introduits par le dernier commit isolé.

### 1.4. Création d'un workflow de validation indépendant

Pour éviter de relancer le processus Maven complet à chaque expérimentation, nous avons créé le fichier `.github/workflows/semgrep-validation.yml`.

Ce workflow est déclenché par les pushes vers `tache3-semgrep` et contient uniquement le job `Semgrep Differential Scan`.

Il reprend les éléments essentiels du contrôle de sécurité du workflow principal : récupération du dépôt avec son historique complet, installation de Semgrep, utilisation des règles `p/java`, comparaison différentielle et retour d'un code d'échec en présence de nouveaux résultats bloquants.

Cette séparation présente plusieurs avantages :

- Réduction du temps nécessaire pour valider les modifications du contrôle Semgrep.
- Isolation des erreurs de sécurité des erreurs Maven indépendantes.
- Facilitation de l'analyse des journaux GitHub Actions.
- Possibilité de vérifier progressivement chaque vulnérabilité introduite.

Le workflow indépendant constitue un environnement de validation du mécanisme de sécurité, tandis que le workflow principal conserve les tâches de compilation et de tests d'Apache Tika.

### 1.5. Validation expérimentale

#### 1.5.1. Analyse initiale

Nous avons d'abord exécuté Semgrep sur le dépôt Tika avant l'introduction des vulnérabilités de démonstration.

L'analyse a produit les résultats suivants :

| Indicateur          | Résultat |
| ------------------- | -------- |
| Règles exécutées    | 60       |
| Fichiers analysés   | 1 468    |
| Alertes détectées   | 11       |
| Résultats bloquants | 11       |

Lors de cette première exécution, l'option `--error` avait été retirée temporairement pour observer les alertes préexistantes sans provoquer l'échec du job Semgrep.

Cette analyse a confirmé la présence de résultats historiques dans le projet et justifié l'utilisation d'une comparaison différentielle.

Voir : [première exécution GitHub Actions](https://github.com/CarlosDanielZQ/tika/actions/runs/43746199713).

#### 1.5.2. Test sans nouvelle vulnérabilité détectée

Nous avons ensuite exécuté le workflow `Semgrep Validation` avant d'introduire des vulnérabilités dans les fichiers Java.

Semgrep a terminé son analyse sans résultat bloquant, et le job s'est terminé avec succès.

Ce test a permis de valider le comportement du contrôle dans un scénario ne présentant aucune nouvelle alerte détectée.

Il convient toutefois de préciser que l'analyse différentielle ne comportait alors aucun fichier Java admissible à analyser. Ce test valide donc principalement le fonctionnement initial du workflow et l'absence de faux blocage dans ce scénario.

Voir : [exécution réussie](https://github.com/CarlosDanielZQ/tika/actions/runs/43807537450).

#### 1.5.3. Test avec introduction d'une vulnérabilité

Nous avons créé la classe `SemgrepSecurityDemo.java` dans le module `tika-core`, puis introduit volontairement une socket réseau non chiffrée.

Semgrep a détecté la construction `new Socket()` et signalé un nouveau résultat bloquant.

Grâce à `--error`, la commande Semgrep s'est terminée avec le code de sortie `1`, provoquant l'échec du job GitHub Actions.

Ce résultat confirme que le contrôle différentiel peut bloquer une modification introduisant une vulnérabilité détectée.

#### 1.5.4. Validation progressive de sept vulnérabilités

Nous avons poursuivi les expérimentations en ajoutant progressivement plusieurs constructions volontairement vulnérables.

Les résultats ont augmenté au fur et à mesure des modifications validées, jusqu'à atteindre sept alertes correspondant à sept règles Semgrep distinctes.

Le dernier rapport présente :

| Indicateur         | Résultat |
| ------------------ | -------- |
| Règles exécutées   | 60       |
| Fichiers analysés  | 1        |
| Alertes détectées  | 7        |
| Alertes bloquantes | 7        |
| Résultat du job    | Échec    |
| Code de sortie     | 1        |

Les sept alertes correspondent à une socket non chiffrée, à MD5, DES, SHA-1, une injection SQL, RC4 et Blowfish.

Voir : [exécution avec sept alertes bloquantes](https://github.com/CarlosDanielZQ/tika/actions/runs/43940925801).

### 1.6 Difficultés rencontrées et limites

Lors des premières expérimentations, le job Maven du workflow principal a échoué dans le module `tika-pipes-s3-integration-tests`. Les journaux indiquaient notamment une difficulté à récupérer une image Docker MinIO via Testcontainers.

Cet échec est distinct des résultats Semgrep. Il a motivé l'utilisation d'un workflow de validation indépendant afin de poursuivre les tests de sécurité sans dépendre des tests d'intégration S3.

Certaines constructions volontairement vulnérables, telles que la désérialisation Java native, l'utilisation de `java.util.Random`, la validation TLS désactivée et l'exécution de commandes système à partir d'entrées externes, n'ont pas produit de résultats supplémentaires avec les règles `p/java` utilisées.

Ces expérimentations soulignent que l'absence d'alerte Semgrep ne garantit pas l'absence de vulnérabilité.

Enfin, le contrôle différentiel a été validé dans le workflow indépendant. Le workflow principal nécessite encore une validation de sa configuration finale et de la dépendance entre le contrôle de sécurité et le job de compilation si l'on souhaite empêcher explicitement le lancement du build Maven lorsque Semgrep échoue.


## 2. Choix du fichier modifié

Le fichier `tika-core/src/main/java/org/apache/tika/SemgrepSecurityDemo.java` a été retenu pour centraliser les exemples de vulnérabilités.

Ce choix permet :

- D'isoler les modifications de sécurité du fonctionnement principal de Tika.
- De regrouper les sept exemples dans une seule classe Java.
- De faciliter la comparaison des résultats Semgrep.
- D'associer chaque vulnérabilité à une instruction identifiable et à une règle de détection spécifique.

## 3. Les 7 vulnérabilités introduites et explications

### 3.1. Socket réseau non chiffrée

**Code :** `new Socket()`

**Règle :** `java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket`

La création d'une socket Java ordinaire n'assure pas le chiffrement des communications. Si des données sensibles sont transmises par cette connexion sans protection supplémentaire, elles peuvent être interceptées.

**Résultat :** Semgrep détecte l'utilisation d'une socket non chiffrée et produit une alerte bloquante.

### 3.2. Utilisation de MD5

**Code :** `MessageDigest.getInstance("MD5")`

**Règle :** `java.lang.security.audit.crypto.use-of-md5.use-of-md5`

MD5 est une fonction de hachage cryptographique dont la résistance aux collisions est compromise. Elle ne convient plus aux applications exigeant une protection contre les collisions, notamment certaines utilisations dans les signatures numériques.

**Résultat :** Semgrep signale l'utilisation de MD5 comme une faiblesse cryptographique.

### 3.3. Utilisation de DES

**Code :** `Cipher.getInstance("DES/ECB/PKCS5Padding")`

**Règle :** `java.lang.security.audit.crypto.des-is-deprecated.des-is-deprecated`

DES utilise une clé effective de 56 bits, insuffisante pour résister aux attaques modernes par force brute. L'exemple utilise également le mode ECB, qui peut révéler des motifs répétitifs dans les données chiffrées.

**Résultat :** Semgrep détecte l'utilisation de DES et recommande une solution cryptographique moderne.

### 3.4. Utilisation de SHA-1

**Code :** `MessageDigest.getInstance("SHA-1")`

**Règle :** `java.lang.security.audit.crypto.use-of-sha1.use-of-sha1`

SHA-1 est une fonction de hachage présentant des vulnérabilités connues aux attaques par collision. Son utilisation est déconseillée lorsque la résistance aux collisions constitue une exigence de sécurité.

**Résultat :** Semgrep détecte SHA-1 et produit une alerte bloquante.

### 3.5. Injection SQL

**Code :** `statement.executeQuery(query)`, où `query` contient une entrée utilisateur concaténée.

**Règle :** `java.lang.security.audit.formatted-sql-string.formatted-sql-string`

Une requête SQL construite par concaténation avec une entrée non fiable peut permettre à un attaquant de modifier sa structure. Cette pratique expose l'application aux injections SQL.

**Résultat :** Semgrep identifie la construction dangereuse de la requête et recommande l'utilisation de `PreparedStatement`.

### 3.6. Utilisation de RC4

**Code :** `Cipher.getInstance("RC4")`

**Règle :** `java.lang.security.audit.crypto.use-of-rc4.use-of-rc4`

RC4 est un algorithme de chiffrement par flot présentant des biais statistiques connus. Ces faiblesses peuvent compromettre la confidentialité des données selon le protocole et les conditions d'utilisation.

**Résultat :** Semgrep détecte l'utilisation de RC4 et produit une alerte bloquante.

### 3.7. Utilisation de Blowfish

**Code :** `Cipher.getInstance("Blowfish")`

**Règle :** `java.lang.security.audit.crypto.use-of-blowfish.use-of-blowfish`

Blowfish est un algorithme de chiffrement par blocs utilisant des blocs de 64 bits. Cette taille relativement faible présente des limites de sécurité lors du chiffrement de grandes quantités de données, notamment en raison du risque de répétition des blocs.

**Résultat :** Semgrep détecte l'utilisation de Blowfish et produit une alerte bloquante.

## 4. Justification des différences entre les vulnérabilités

Les sept exemples sont associés à sept règles Semgrep distinctes. Certains appartiennent néanmoins à une même famille générale de sécurité.

MD5 et SHA-1 sont deux fonctions de hachage cryptographiques différentes. MD5 produit une empreinte de 128 bits, tandis que SHA-1 produit une empreinte de 160 bits. Les deux présentent des faiblesses de résistance aux collisions, mais reposent sur des constructions cryptographiques différentes.

DES, RC4 et Blowfish présentent également des différences fondamentales. DES est un algorithme de chiffrement par blocs dont la principale faiblesse est la taille insuffisante de sa clé. RC4 est un chiffrement par flot dont la sécurité est compromise par des biais statistiques. Blowfish est un chiffrement par blocs dont la taille de bloc de 64 bits limite la sécurité lors du traitement de volumes importants de données.

Les autres exemples concernent des mécanismes distincts : la confidentialité des communications réseau pour la socket non chiffrée et la construction non sécurisée de requêtes pour l'injection SQL.

Ainsi, les sept exemples illustrent des mécanismes techniques différents et déclenchent sept règles Semgrep distinctes.

## 5. Comparaison des résultats Semgrep

### 5.1. Analyse initiale

L'analyse initiale du projet Tika a produit 11 alertes bloquantes avec 60 règles Semgrep exécutées sur 1 468 fichiers.

Ces résultats constituent la référence historique avant l'introduction des vulnérabilités de démonstration.

### 5.2. Analyse après modifications

Après l'introduction des sept exemples dans `SemgrepSecurityDemo.java`, le rapport Semgrep affiche :

- 7 résultats, tous bloquants.
- 60 règles exécutées.
- 1 fichier analysé dans le scan différentiel.
- Un code de sortie égal à 1.

Les sept alertes correspondent aux règles `unencrypted-socket`, `use-of-md5`, `des-is-deprecated`, `use-of-sha1`, `formatted-sql-string`, `use-of-rc4` et `use-of-blowfish`.

### 5.3. Interprétation

La comparaison démontre que les nouvelles modifications introduisent sept détections dans le fichier de démonstration.

Les résultats initiaux et finaux ne doivent pas être comparés comme deux totaux équivalents, puisque l'analyse initiale couvrait un ensemble de fichiers beaucoup plus large que l'analyse différentielle.

## 6. Validation expérimentale

La validation s'est déroulée progressivement.

Une première exécution du workflow différentiel, sans vulnérabilité détectée dans les fichiers concernés, s'est terminée avec succès.

Une socket non chiffrée a ensuite été introduite dans la classe de démonstration. Semgrep a identifié une nouvelle vulnérabilité bloquante et le job a échoué avec le code de sortie 1.

Les vulnérabilités suivantes ont été introduites progressivement. À chaque étape réussie, le nombre d'alertes est passé de 1 à 2, puis à 3, 4, 5, 6 et finalement 7 (voir les actions Github exécutées).

Des exemples supplémentaires, notamment la désérialisation Java non sécurisée, l'utilisation de `java.util.Random`, la désactivation de la validation TLS et l'injection de commandes système, ont également été essayés, sans produire de nouvelles alertes dans la configuration utilisée.

Ces expériences montrent que l'outil détecte les constructions couvertes par ses règles, mais que toute vulnérabilité potentielle n'est pas nécessairement signalée.

## 7. Conclusion

L'intégration de Semgrep à GitHub Actions a permis de détecter sept constructions Java vulnérables au moyen de sept règles distinctes.

Le workflow différentiel a démontré sa capacité à provoquer l'échec du contrôle de sécurité lorsqu'une nouvelle vulnérabilité bloquante est introduite, tout en évitant de traiter les alertes historiques comme des nouveautés.

Cette démarche démontre l'intérêt de l'analyse statique automatisée dans un processus d'intégration continue, ainsi que ses limites liées à la couverture des règles et à la configuration de l'analyse.
