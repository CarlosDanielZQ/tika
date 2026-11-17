#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""IFT3913 - Tache 3 : mise en forme des rapports JSON de Semgrep (Markdown).

    semgrep_report.py summary  REPORT.json
    semgrep_report.py compare  BEFORE.json BEFORE_ROOT AFTER.json AFTER_ROOT

`compare` identifie une alerte par (regle, fichier, code source vise) plutot que par
numero de ligne : une alerte deja presente dont la ligne s'est simplement deplacee
n'est donc pas comptee comme nouvelle. Le code est relu depuis l'arbre de fichiers,
car Semgrep sans compte ne renvoie plus le champ extra.lines ("requires login").
"""
import argparse
import json
import os
import sys
from collections import Counter


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def short_rule(check_id):
    return check_id.rsplit(".", 1)[-1]


def cwe(result):
    values = result.get("extra", {}).get("metadata", {}).get("cwe", [])
    if isinstance(values, str):
        values = [values]
    return ", ".join(v.split(":")[0] for v in values) or "-"


def snippet(root, result):
    path = os.path.join(root, result["path"])
    start, end = result["start"]["line"], result["end"]["line"]
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            lines = f.read().splitlines()[start - 1:end]
    except OSError:
        lines = []
    return " ".join(" ".join(line.split()) for line in lines)


def key(root, result):
    return (result["check_id"], result["path"].replace("\\", "/"), snippet(root, result))


def table(results, root=None):
    out = ["| # | Regle | Severite | CWE | Emplacement |", "|---|---|---|---|---|"]
    ordered = sorted(results, key=lambda r: (r["path"], r["start"]["line"], r["check_id"]))
    for i, r in enumerate(ordered, 1):
        out.append("| {} | `{}` | {} | {} | `{}:{}` |".format(
            i, short_rule(r["check_id"]), r.get("extra", {}).get("severity", "?"),
            cwe(r), r["path"].replace("\\", "/"), r["start"]["line"]))
    return "\n".join(out)


def summary(args):
    data = load(args.report)
    results = data.get("results", [])
    print("## Semgrep : nouvelles alertes\n")
    if not results:
        print("Aucune nouvelle alerte par rapport au commit de reference. :white_check_mark:\n")
    else:
        print("**{} nouvelle(s) alerte(s)** : le controle echoue et le build n'est pas lance. :x:\n".format(len(results)))
        print(table(results))
        print()
    if data.get("errors"):
        print("_{} erreur(s) Semgrep (fichiers non analysables, etc.) : voir l'artefact JSON._\n".format(len(data["errors"])))


def compare(args):
    before, after = load(args.before), load(args.after)
    b_res, a_res = before.get("results", []), after.get("results", [])
    b_keys = Counter(key(args.before_root, r) for r in b_res)
    a_keys = Counter(key(args.after_root, r) for r in a_res)

    remaining = Counter(b_keys)
    new = []
    for r in a_res:
        k = key(args.after_root, r)
        if remaining[k] > 0:
            remaining[k] -= 1
        else:
            new.append(r)
    remaining = Counter(a_keys)
    removed = []
    for r in b_res:
        k = key(args.before_root, r)
        if remaining[k] > 0:
            remaining[k] -= 1
        else:
            removed.append(r)

    def scanned(d):
        return len(d.get("paths", {}).get("scanned", []))

    print("## Semgrep : comparaison des scans complets\n")
    print("| | {} | {} |".format(args.before_label, args.after_label))
    print("|---|---|---|")
    print("| Fichiers analyses | {} | {} |".format(scanned(before), scanned(after)))
    print("| Alertes | {} | {} |".format(len(b_res), len(a_res)))
    print("| Nouvelles alertes | - | **{}** |".format(len(new)))
    print("| Alertes disparues | - | {} |".format(len(removed)))
    print()

    print("### Alertes nouvellement introduites ({})\n".format(len(new)))
    print(table(new) if new else "_Aucune._")
    print()
    if removed:
        print("### Alertes disparues ({})\n".format(len(removed)))
        print(table(removed))
        print()

    print("### Nombre d'alertes par regle\n")
    rules_b = Counter(r["check_id"] for r in b_res)
    rules_a = Counter(r["check_id"] for r in a_res)
    print("| Regle | Avant | Apres | Difference |")
    print("|---|---|---|---|")
    for rule in sorted(set(rules_b) | set(rules_a)):
        diff = rules_a[rule] - rules_b[rule]
        print("| `{}` | {} | {} | {} |".format(short_rule(rule), rules_b[rule], rules_a[rule],
                                             "+{}".format(diff) if diff > 0 else diff))
    print()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("summary")
    s.add_argument("report")
    s.set_defaults(func=summary)
    c = sub.add_parser("compare")
    c.add_argument("before")
    c.add_argument("before_root")
    c.add_argument("after")
    c.add_argument("after_root")
    c.add_argument("--before-label", default="avant")
    c.add_argument("--after-label", default="apres")
    c.set_defaults(func=compare)
    args = parser.parse_args()
    args.func(args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
