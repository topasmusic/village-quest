# Codex Start Here

> Current release note (2026-07-29): `Village Quest 2.1.0 - Prosperity & Prestige` is the public stable release for Minecraft `26.2`, `26.1.2`, and `1.21.11`. It adds the complete economy/endgame layer and the accumulated quest, tracker, localization, responsive-UI, Journal-navigation, and progression corrections. All three builds and the shared `1841`-key resource gate pass.

> Current development note (2026-07-29): after the three-line `2.1.0` release, Minecraft `26.2` is the sole active content-development line. `26.1.2` and Yarn `1.21.11` remain released maintenance targets and receive no routine content, UI, balance, compatibility, or ordinary bug backports.

> Crop-yield note (2026-07-28): all active Wheat/Potato/Carrot quantity quests use one mature-crop tracked-item path on all three lines. Normal harvests credit the collected stack count, verified right-click-and-replant harvests include Carrots, immature crops grant nothing, and the former per-block callbacks are removed to prevent duplicate credit. `Autumn Harvest` remains an intentional fruit-block objective.

> Build-identity note (2026-07-29): all three release projects use the stable embedded version `2.1.0`. Future unreleased work must return to `build_channel=unreleased` with a new numbered revision and follows the Stable/Unreleased artifact-preservation rules below.

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

- `26.2` ist die aktive Referenz- und Arbeitslinie; der unveroeffentlichte `2.1.0`-Stand ist auf alle drei gepflegten Linien portiert.
- Die Versionsrichtlinie steht in `VERSION_SUPPORT.md`: `2.1.0` bleibt bis zur Veroeffentlichung auf `26.2`, `26.1.2` und `1.21.11` feature-paritaetisch. Nach der Drei-Linien-Veroeffentlichung ist nur noch die jeweils ausgewaehlte neueste stabile Minecraft-Version eine aktive Content-Linie, zunaechst `26.2`.
- Nach `2.1.0` duerfen neue Quests, Systeme, Items, UIs, Balance- oder normale Kompatibilitaetsaenderungen nicht automatisch auf `26.1.2` oder `1.21.11` portiert werden. Alte Releases bleiben verfuegbar; ein Backport ist nur nach ausdruecklicher Maintainer-Entscheidung fuer Startabstuerze, Save-/Persistenzkorruption oder schwere Exploits vorgesehen.
- Wird spaeter eine neue stabile Minecraft-Version als Ziel gewaehlt, ersetzt sie `26.2` als einzige aktive Linie. Es wird nicht fuer jede neue Minecraft-Version eine weitere dauerhaft aktive Linie angehaeuft.
- In allen drei Versionsordnern liegt der veroeffentlichte Stable-Stand `2.0.1` mit dem kompletten `Roads Between Villages`-Umfang und dem Interface-Frame-Hotfix.
- Der lokale Karawanenstand unterstuetzt bis zu fuenf Routen, persistente Vermessungsentwuerfe mit bis zu `48` Wegpunkten, echte Umwege auf Karte und Simulation sowie das Entfernen einzelner Routen. Der komplette Fuenf-Routen-/Vermessungs-/Entfernungsablauf wurde am `2026-07-18` auf der Referenzlinie `26.2` im echten Client geprueft; dabei wurden die kompakte Zeilenaufteilung und das `30`-Sekunden-Bestaetigungsfenster finalisiert.
- Alle drei Linien enthalten die Reliability-/Live-Navigation-/UI-Paesse sowie `Prosperity & Prestige`: robuste Karawanen-Recovery, echte bewohnte Dorfziele, Terrain-Karten, fuenf routeigene Outfit-Farben, das kompakte Journal, Investitionen, Kommissionen, Dienste, Sammlung, Wirtschaftsbuch und deterministische QA-Befehle.
- Der native Java-`21`-/Minecraft-`1.21.11`-Clientlauf bestaetigte die Yarn-Paritaet fuer die zentralen Routen- und UI-Flaechen. Ein anschliessend entdeckter automatischer Spawnfehler wurde zielversionsspezifisch durch das Aktualisieren der Kollisionsbox vor `isSpaceEmpty` behoben und im Client nachgeprueft.
- Commit und Push eines unveroeffentlichten Release Candidates brauchen eine ausdrueckliche User-Anweisung; Tag und Release brauchen danach noch einmal eine eigene ausdrueckliche Freigabe.
- `2.0.1` ist auf `26.2`, `26.1.2` und `1.21.11` der veroeffentlichte Stable-Stand.
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
   - Vor jeder Portierung zusaetzlich `VERSION_SUPPORT.md` pruefen. Nach dem Drei-Linien-Release `2.1.0` ist eine Aenderung an der aktiven Linie keine automatische Erlaubnis oder Pflicht fuer Backports.
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
- Bei bewusst nur einer geänderten Entwicklungslinie kann `python tools/validate_resources.py --line <version>` die JSON-, Übersetzungs- und Platzhalterprüfung dieser Linie ausführen. Der Aufruf ohne `--line` bleibt die strengere Drei-Linien-Paritätsprüfung und muss nach einer vollständigen Portierung wieder bestehen.

## Stable- und Unreleased-Artefakte

- Solange ein Stand nicht veroeffentlicht ist, muessen in jeder betroffenen `gradle.properties` neben der naechsten Zielversion `build_channel=unreleased` und eine positive ganzzahlige `unreleased_revision` stehen.
- Ein Unreleased-Build muss sowohl in `fabric.mod.json` als auch im Dateinamen eindeutig als `<version>-unreleased.<revision>` erscheinen, zum Beispiel `village-quest-2.1.0-unreleased.1-mc26.2.jar`. Eine unmarkierte oder unnummerierte JAR darf nicht aus einem neuen unveroeffentlichten Arbeitsstand gebaut oder weitergegeben werden.
- Die Revision wird genau einmal erhoeht, bevor ein gegenueber dem letzten weitergegebenen Teststand veraenderter Quellstand erneut gebaut und uebergeben wird. Ein identischer Wiederholungsbuild behaelt dieselbe Revision; eine bereits verwendete Revision darf nicht fuer anderen Inhalt recycelt werden.
- Die letzte auf GitHub veroeffentlichte Stable-Runtime-JAR und ihre Sources-JAR bleiben in `build/libs` neben dem neuesten nummerierten Unreleased-Paar erhalten. Sie duerfen weder ueberschrieben noch aus aktuellem Unreleased-Quellcode neu erzeugt oder auf eine alte Stable-Version umbenannt werden.
- Fehlen die Stable-Artefakte lokal, muessen die exakten Assets des letzten passenden GitHub-Releases wiederhergestellt werden. Vor dem Kopieren Release-Tag, Dateiname und nach Moeglichkeit SHA-256 gegen GitHub pruefen.
- `gradlew clean` leert `build/libs`. Wenn fuer einen sauberen Build `clean` notwendig ist, danach die echten Stable-Artefakte erneut aus dem GitHub-Release laden, bevor der Arbeitsstand uebergeben wird.
- Vor jeder Uebergabe eines Unreleased-Builds pro Linie kontrollieren, dass genau diese vier beabsichtigten Dateien nebeneinander liegen: Stable Runtime, Stable Sources, neueste nummerierte Unreleased Runtime und deren Sources. Ein supersediertes unnummeriertes oder niedriger nummeriertes Unreleased-Paar darf erst entfernt werden, nachdem das neue Paar erfolgreich gebaut wurde.
- In ein Minecraft-/Modrinth-Testprofil darf ausschliesslich die Runtime-JAR ohne `-sources` im Dateinamen kopiert werden. Vor dem Start kontrollieren, dass im `mods`-Ordner keine Village-Quest-Sources-JAR liegt und dass die installierte Runtime mindestens eine erwartete `.class`-Datei aus `de/quest/` enthaelt; eine Sources-JAR besitzt ebenfalls `fabric.mod.json`, ist aber nicht spielbar und verursacht fehlende Mixin-Klassen beim Pre-Launch.
- Erst nach einer ausdruecklichen Release-Freigabe darf `build_channel=stable` gesetzt werden. Danach alle Ziel-Linien neu bauen und sicherstellen, dass Release-JAR, eingebettete Mod-Version, Tag und Changelog dieselbe Version ohne `-unreleased` tragen. Nach dem Release beginnt die naechste Entwicklung wieder mit `build_channel=unreleased`.

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
