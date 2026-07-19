# Codex Start Here

> Current release note (2026-07-19): `Village Quest 2.0.0 - Roads Between Villages` is the public stable release on all three maintained lines. The balance/Trade-Guild expansion, unified UI, terrain map/minimap, caravan reliability, inhabited-village validation, five route-owned outfits, destination renaming, four-offer Pilgrim cap, German umlauts, old-save Ledger/Horn backfills, and the mixed licensing package were deliberately ported. Automated gates and all three builds passed. `26.2` is the native reference; `1.21.11` received a focused native regression pass and target-specific collision-bound refresh fix. The maintainer explicitly deferred a native `26.1.2` smoke test and a full no-admin-completion `The Empty Caravan` run. The eight legacy entity skins retain explicit unresolved-provenance warnings and are excluded from Village Quest ownership and licensing claims.

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

Stand dieser Datei: `2026-07-19`

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

- `26.2` ist die aktive Referenz- und Arbeitslinie; `1.22.8` bleibt bis zur Publikation der oeffentliche Stable-Stand.
- In allen drei lokalen Versionsordnern liegt der unveroeffentlichte Release Candidate `2.0.0` mit `The Empty Caravan`, `Caravan Yard`, persistenten Handelsrouten, sichtbaren Karawanen, Terrainkarte/Minimap, Strassenqualitaet, acht dynamischen Ereignissen und kompatiblem Rechtsklick-Erntetracking.
- Der lokale Karawanenstand unterstuetzt bis zu fuenf Routen, persistente Vermessungsentwuerfe mit bis zu `48` Wegpunkten, echte Umwege auf Karte und Simulation sowie das Entfernen einzelner Routen. Der komplette Fuenf-Routen-/Vermessungs-/Entfernungsablauf wurde am `2026-07-18` auf der Referenzlinie `26.2` im echten Client geprueft; dabei wurden die kompakte Zeilenaufteilung und das `30`-Sekunden-Bestaetigungsfenster finalisiert.
- Alle drei Linien enthalten die Reliability-/Live-Navigation-/UI-Paesse: robuste Karawanen-Recovery, sichere Gruppenplatzierung, bewohnte echte Dorfziele, Terrain-Minimap per `,`/Command, terrainbasierte Vollkarte, Spieler-/Karawanen-Tooltips, fuenf routeigene Outfit-Farben, das kompakte Journal und deterministische QA-Befehle.
- Der native Java-`21`-/Minecraft-`1.21.11`-Clientlauf bestaetigte die Yarn-Paritaet fuer die zentralen Routen- und UI-Flaechen. Ein anschliessend entdeckter automatischer Spawnfehler wurde zielversionsspezifisch durch das Aktualisieren der Kollisionsbox vor `isSpaceEmpty` behoben und im Client nachgeprueft.
- Commit und Push eines unveroeffentlichten Release Candidates brauchen eine ausdrueckliche User-Anweisung; Tag und Release brauchen danach noch einmal eine eigene ausdrueckliche Freigabe.
- `1.22.8` bleibt auf `26.2`, `26.1.2` und `1.21.11` der veroeffentlichte Stable-Stand; `2.0.0` bleibt bis dahin lokal und unveroeffentlicht.
- Die frueheren Multiplayer-Betas sind Teil des aktuellen Stable-Gameplays.
- `1.22.8` verhindert, dass globale Resets alte Party-, Invite- oder Shared-Session-Daten beim Serverstopp wiederherstellen, und bereinigt kurzlebige Runtime-Zustaende sauber an Server- und Spieler-Lifecycle-Grenzen.
- `Restless Pens` Kapitel 4 zeigt jetzt beide Fortschrittszeilen in `en_us`, `de_de` und `es_es` korrekt an.
- `1.22.7` enthaelt die Quest-Tracking-Fixes fuer Halsband-Faerbung, Bienenstock-Ernten, Schaf-Scheren, die korrigierten `The Failing Harvest`-Ziele und das reparierte Shard-Bonus-Tracking fuer actionbasierte `Daily`-Quests.
- Alle drei gepflegten Linien haben jetzt die groessere `Questmaster`-Hover-Vorschau fuer lange Beschreibungen.
- Der globale Reset-Befehl existiert auf allen drei gepflegten Linien:
  - `/vq admin reset complete`
  - `/villagequest admin reset complete`
- Die modernen Linien nutzen fuer Commands nur noch `/vq ...` und `/villagequest ...`.
- Der Reset leert sowohl SavedData als auch laufende Village-Quest-Runtime-Session-Zustaende, einschliesslich Quest-Partys, Invites, Shared Sessions und Reconnect Grace.
- Der erkannte Root-Workflow unter `.github/workflows/build.yml` baut alle drei Linien und prueft Ressourcen sowie Sprachparitaet.
- Wolkensprung wurde restlos aus Code, Ressourcen, Skripten und Templates entfernt.
- `Shadows on the Trade Road` bleibt der spaete Story-Batch, der bei Aenderungen bewusst gegen die Legacy-Linie verglichen werden muss.
- Fuer den neuen Karawanen-Batch ist `/vq admin routes testsetup` der zentrale Ingame-Testbefehl; Details stehen in der jeweiligen `docs/wiki/trade-routes-and-caravans.md`.

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
