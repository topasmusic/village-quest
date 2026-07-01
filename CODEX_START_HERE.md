# Codex Start Here

Wenn in einer neuen Session auf diese Datei verwiesen wird, arbeite nach diesem Ablauf, bevor du Annahmen triffst oder Aenderungen machst.

## Ziel

Baue zuerst belastbaren Kontext aus Maintainer-Daten, Changelog und Wiki auf, damit du konsistent, vorsichtig und versionssauber arbeitest.

## Immer zuerst lesen

1. `NEXT_SESSION.md`
2. die passende Versions-Memory:
   - `26.2/MEMORY.md`
   - `26.1.2/MEMORY.md`
   - `1.21.11/MEMORY.md`
3. die betroffenen `CHANGELOG.md`-Dateien
4. die passende `README.md`
5. die relevanten Wiki-Seiten unter `docs/wiki/`
6. erst danach die betroffenen Code-Dateien

## Repo-Grundsaetze

- Standard-Arbeitslinie ist `26.2`, ausser der User will explizit Legacy oder Paritaet.
- `26.2` und `26.1.2` sind Mojang-Mappings-Linien.
- `1.21.11` ist die Yarn-Mappings-Linie.
- Niemals blind Code zwischen `26.2` und `1.21.11` uebernehmen.
- Verhalten portieren, aber APIs, Methodennamen, Typen und Imports pro Linie sauber anpassen.
- `26.1.2` bleibt die letzte stabile Modern-Referenzlinie.

## Aktuell wichtige Versionsfakten

Stand dieser Datei: `2026-07-01`

- `26.2`
  - Minecraft `26.2`
  - Java `25`
  - Fabric Loader `0.19.3`
  - Fabric API `0.153.0+26.2`
  - offizielle Mojang-Namen
- `26.1.2`
  - Minecraft `26.1.2`
  - Java `25`
  - Fabric Loader `0.19.2`
  - Fabric API `0.146.0+26.1.2`
  - offizielle Mojang-Namen
- `1.21.11`
  - Minecraft `1.21.11`
  - Java `21`
  - Fabric Loader `0.19.2`
  - Fabric API `0.141.3+1.21.11`
  - Yarn `1.21.11+build.4`

## Aktuell wichtige inhaltliche Fakten

- `26.2` ist jetzt die aktive Stable- und Arbeitslinie fuer `Village Quest 1.22.7`.
- `26.1.2` und `1.21.11` sind ebenfalls auf `1.22.7` veroeffentlicht.
- Die frueheren Multiplayer-Betas sind Teil des aktuellen Stable-Gameplays.
- `1.22.7` enthaelt die Quest-Tracking-Fixes fuer Halsband-Faerbung, Bienenstock-Ernten, Schaf-Scheren, die korrigierten `The Failing Harvest`-Ziele und das reparierte Shard-Bonus-Tracking fuer actionbasierte `Daily`-Quests.
- Alle drei gepflegten Linien haben jetzt die groessere `Questmaster`-Hover-Vorschau fuer lange Beschreibungen.
- Der globale Reset-Befehl existiert auf den modernen Linien:
  - `/vq admin reset complete`
  - `/villagequest admin reset complete`
- Die modernen Linien nutzen fuer Commands nur noch `/vq ...` und `/villagequest ...`.
- Der Reset leert sowohl SavedData als auch laufende Village-Quest-Runtime-Session-Zustaende.
- Wolkensprung wurde restlos aus Code, Ressourcen, Skripten und Templates entfernt.
- `Shadows on the Trade Road` bleibt der spaete Story-Batch, der bei Aenderungen bewusst gegen die Legacy-Linie verglichen werden muss.

## Wenn der User nach "latest" fragt

- Bei Fabric Loader, Fabric API, Minecraft-Versionen oder aehnlichen beweglichen Fakten nicht raten.
- Bei "neueste", "aktuellste", "up to date" oder aehnlichem offizielle Quellen oder primaere Quellen pruefen.
- Wenn sich Linien unterschiedlich verhalten, das explizit benennen.

## Arbeitsweise fuer Aenderungen

1. Zuerst Maintainer-Daten, Changelog, README und Wiki lesen.
2. Dann die betroffenen Klassen und Ressourcen lesen.
3. Unterschiede zwischen `26.2` und `1.21.11` bewusst benennen, wenn beide Linien betroffen sind.
4. Wenn die Aufgabe `Shadows on the Trade Road`, `Watch Bell`-Folgecontent oder Karawanen-/Traitor-Systeme betrifft:
   - zusaetzlich `WATCH_BELL_EXPANSION_PLAN.md` lesen
5. Erst dann editieren.
6. Bei user-facing Aenderungen auch Doku aktualisieren:
   - `README.md`
   - relevante Wiki-Seiten
   - `CHANGELOG.md`
   - `MEMORY.md`
   - `NEXT_SESSION.md`
   - falls sinnvoll `NEXT_SESSION_PROMPT.txt`

## Build- und Check-Regeln

- Wenn nur eine Linie geaendert wurde, mindestens diese Linie bauen.
- Wenn beide Linien geaendert wurden, beide Linien bauen.
- Korrekte Java-Version pro Linie verwenden.
- Danach kurze Konsistenzsuche machen:
  - betroffene Befehle
  - Versionen
  - alte Feature-Namen
  - Sprachdateien

## Wichtige Vorsichtspunkte

- Keine alten Alias-Pfade ungefragt entfernen, wenn sie auf einer Linie absichtlich als Kompatibilitaet existieren.
- Nicht annehmen, dass ein Build auf der Mojang-Linie automatisch dasselbe fuer Yarn bedeutet.
- Nicht nur Code aendern und Maintainer-/Wiki-Dateien vergessen.
- Keine destruktiven Git-Operationen verwenden.
- `runClient` nicht starten, ausser der User verlangt es ausdruecklich.

## Definition von "sauber fertig"

Eine Aufgabe gilt hier erst als sauber abgeschlossen, wenn:

- der Code pro betroffener Linie korrekt umgesetzt ist
- Mapping-Unterschiede sauber beachtet wurden
- Doku und Maintainer-Daten nachgezogen wurden, falls die Aenderung user-facing oder workflow-relevant ist
- die passenden Builds erfolgreich gelaufen sind
- die Ergebnisse und eventuelle Restrisiken klar berichtet wurden
