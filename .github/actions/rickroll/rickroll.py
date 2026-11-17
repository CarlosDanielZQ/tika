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
"""IFT3913 - Tache 3 : rickroll quand un cas de test JUnit echoue.

Variables d'environnement (fournies par action.yml) :
  RICKROLL_REPORTS    motifs glob des rapports JUnit XML, separes par des espaces
  RICKROLL_VIDEO_URL  lien de la video
Sorties GitHub Actions : failed-tests, rickrolled.
"""
import glob
import os
import xml.etree.ElementTree as ET

VIDEO_ID = "dQw4w9WgXcQ"
MAX_LISTED = 50

BANNER = r"""
  +------------------------------------------------------------+
  |                                                            |
  |     _____  _      _                 _  _            _  _   |
  |    |  __ \(_)    | |               | || |          | || |  |
  |    | |__) |_  ___| | __ _ __  ___  | || |  ___   __| || |  |
  |    |  _  /| |/ __| |/ /| '__|/ _ \ | || | / _ \ / _` || |  |
  |    | | \ \| | (__|   < | |  | (_) || || ||  __/| (_| ||_|  |
  |    |_|  \_\_|\___|_|\_\|_|   \___/ |_||_| \___| \__,_|(_)  |
  |                                                            |
  |        Un test a echoue... et vous venez d'etre            |
  |        rickrolle par le CI d'Apache Tika.  (o_o)/ ~ ~      |
  |                                                            |
  +------------------------------------------------------------+
"""


def find_reports(patterns):
    files = set()
    for pattern in patterns.split():
        files.update(glob.glob(pattern, recursive=True))
    return sorted(files)


def failed_cases(report):
    """Renvoie la liste (classe, test, type, message) des cas en echec d'un rapport."""
    try:
        root = ET.parse(report).getroot()
    except (ET.ParseError, OSError):
        return []
    failures = []
    for case in root.iter("testcase"):
        for tag in ("failure", "error"):
            node = case.find(tag)
            if node is not None:
                message = (node.get("message") or node.get("type") or "").strip().splitlines()
                failures.append((case.get("classname", "?"), case.get("name", "?"), tag,
                                 message[0][:150] if message else ""))
                break
    return failures


def write_output(name, value):
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write("{}={}\n".format(name, value))


def write_summary(text):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(text)


def md_escape(text):
    return (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("|", "\\|").replace("\n", " "))


def main():
    patterns = os.environ.get("RICKROLL_REPORTS", "**/target/surefire-reports/TEST-*.xml")
    video = os.environ.get("RICKROLL_VIDEO_URL", "https://www.youtube.com/watch?v=" + VIDEO_ID)

    reports = find_reports(patterns)
    failures = [f for report in reports for f in failed_cases(report)]
    write_output("failed-tests", len(failures))

    if not failures:
        write_output("rickrolled", "false")
        print("::notice title=Rickroll::Le job a echoue, mais aucun cas de test en echec n'a ete trouve "
              "dans {} rapport(s) JUnit (erreur de compilation, checkstyle, ...). Pas de rickroll cette fois.".format(len(reports)))
        return 0

    write_output("rickrolled", "true")
    print(BANNER)
    print("{} cas de test en echec :".format(len(failures)))
    for cls, name, kind, message in failures[:MAX_LISTED]:
        print("  - {}#{} [{}] {}".format(cls, name, kind, message))
    print("\nRegardez ceci pendant que vous corrigez : {}\n".format(video))
    print("::error title=Rickroll ({} test(s) en echec)::Un cas de test a echoue. "
          "Never gonna give you up : {}".format(len(failures), video))

    lines = [
        "## :microphone: Rickroll ! {} cas de test en echec\n".format(len(failures)),
        "Un test a echoue, le CI vous offre donc une petite pause musicale.\n",
        "[![Rick Astley - Never Gonna Give You Up](https://img.youtube.com/vi/{}/hqdefault.jpg)]({})\n".format(VIDEO_ID, video),
        "| Classe | Test | Type | Message |",
        "|---|---|---|---|",
    ]
    for cls, name, kind, message in failures[:MAX_LISTED]:
        lines.append("| `{}` | `{}` | {} | {} |".format(md_escape(cls), md_escape(name), kind, md_escape(message)))
    if len(failures) > MAX_LISTED:
        lines.append("\n_... et {} autre(s)._".format(len(failures) - MAX_LISTED))
    write_summary("\n".join(lines) + "\n\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
