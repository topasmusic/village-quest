# Quest Party Multiplayer Draft

Stand: `2026-05-11`

Maintainer-Kontext:
- Sichtbare Autorenangabe ab dem vorbereiteten `2.0.0`-Stand: `TopasMusic`
- `1.21.11` ist die Legacy-/Yarn-Linie
- `26.1.2` ist die Mojang-Mappings-Linie

## Aktueller Umsetzungsstand

Der Draft ist inzwischen teilweise real umgesetzt.

Fuer den aktuellen lokalen Beta-Stand `1.22.1-beta.1` auf `1.21.11` gilt:
- Quest-Parties sind eingebaut, aber nur auf Dedicated Servern aktiv.
- Der `Questmaster` hat den sichtbaren Party-Button und Party-Drawer fuer sharebare `Daily`- und `Weekly`-Eintraege.
- Shared `Daily` und `Weekly` nutzen gemeinsamen Objective-Fortschritt; verteilte Turn-ins sind fuer die betroffenen Harvest-/Craft-/Food-Quests gepoolt.
- Laufende geteilte Quests werden bei Nachjoin nicht mehr still synchronisiert, sondern als Chat-Offer mit Accept/Decline angeboten.
- Disconnects nutzen eine `10 minute`-Grace-Phase statt Sofort-Entfernung.
- Party-Mitgliedschaft, Shared Sessions, Offers und Disconnect-Grace sind restart-persistent.
- `Story`, `Special` und die `26.1.2`-Portierung sind noch nicht Teil dieses umgesetzten Schnitts.

Wichtig fuer Maintainer:
- Dieser Draft bleibt als Architektur- und Port-Kontext nuetzlich.
- Wo Draft und Code heute auseinanderlaufen, gewinnt der aktuelle `1.21.11`-Codepfad.

## Zielbild

Spieler sollen andere Spieler in aktive Village-Quest-Inhalte einladen koennen und dann als kleine Party dieselben Ziele teilen.

Gewuenschtes Verhalten:
- Wenn ein Party-Mitglied einen relevanten Villager anspricht, geht das in denselben Quest-Fortschrittspool.
- Wenn Spieler A `10` Weizen sammelt und Spieler B `10`, soll die Party bei einem Ziel von `20` gemeinsam bei `20/20` stehen.
- Die Beteiligten sollen nicht nur Fortschritt teilen, sondern auch dieselbe aktive Quest-Instanz sehen.
- Der erste technische Schnitt soll auf `1.21.11` sauber umgesetzt werden und danach bewusst auf `26.1.2` portiert werden.

## Ist-Zustand im Code

Die aktuelle Quest-Architektur ist fast komplett pro Spieler gebaut.

Zentrale Stellen:
- `1.21.11/src/main/java/de/quest/data/QuestState.java`
- `1.21.11/src/main/java/de/quest/data/PlayerQuestData.java`
- `1.21.11/src/main/java/de/quest/quest/daily/DailyQuestService.java`
- `1.21.11/src/main/java/de/quest/quest/weekly/WeeklyQuestService.java`
- `1.21.11/src/main/java/de/quest/quest/story/StoryQuestService.java`
- `1.21.11/src/main/java/de/quest/quest/QuestService.java`
- `1.21.11/src/main/java/de/quest/network/Payloads.java`

Wichtige Beobachtungen:
- `QuestState` speichert nur `Map<UUID, PlayerQuestData>`.
- Quest-Definitionen schreiben heute direkt in den jeweiligen Spielerzustand.
- `Daily`, `Weekly` und `Story` mischen echten Objective-State mit lokalem Hilfs-State im selben Int-/Flag-Speicher.
- Turn-in-Checks und Turn-in-Verbrauch laufen heute fast immer nur gegen das Inventar des claimenden Spielers.
- Tracker, Journal und Questmaster lesen indirekt aus genau diesen per-player Services.

Das bedeutet: Ein Party-System ist kein kleiner UI-Zusatz, sondern braucht eine saubere gemeinsame Fortschrittsschicht zwischen Event-Hooks und `PlayerQuestData`.

## Hauptproblem fuer Koop

Die heiklen Teile sind nicht die offensichtlichen Zaehler, sondern die versteckten Hilfswerte.

Beispiele aus `1.21.11`:
- `DailyQuestKeys.LAST_BREAD_CRAFTED`
- `DailyQuestKeys.EXPECTED_HONEY`
- `DailyQuestKeys.LAST_WOOL_PICKED_UP`
- `WeeklyQuestKeys.HARVEST_LAST_BREAD`
- `WeeklyQuestKeys.PASTURE_EXPECTED_WOOL`
- `StoryQuestKeys.FAILING_HARVEST_BREAD_BASELINE`
- `StoryQuestKeys.MARKET_ROAD_BOOK_BASELINE`
- `StoryQuestKeys.RESTLESS_PENS_RIDE_BASELINE`

Diese Werte duerfen nicht blind geteilt werden, weil sie persoenliche Stat-Baselines oder Anti-Dupe-Helfer sind.

Fazit:
- Objective-Werte muessen teilbar sein.
- Baselines, Cooldowns und persoenliche Hilfswerte muessen lokal bleiben.

## Empfehlung fuer den ersten Schnitt

Nicht sofort "alle Questarten fuer alles" aufmachen.

Empfohlener Scope fuer `1.21.11` Phase 1:
- Party erstellen, einladen, annehmen, verlassen, aufloesen
- Shared Progress fuer `Daily`
- Shared Progress fuer `Weekly`
- Shared Turn-in ueber die Party
- Gemeinsamer Claim mit Reward-Fanout an alle aktuellen Party-Mitglieder
- Kleine UI-/Feedback-Erweiterung fuer Party-Status in Chat/Tracker/Questmaster

Explizit noch nicht im ersten Schnitt:
- `Special`-Relic-Quests
- `Pilgrim`-Contracts
- volle Story-Unterstuetzung
- `ShadowsTradeRoadEncounterService` als Party-Runtime

Warum diese Grenze sinnvoll ist:
- Daily/Weekly haben schon die richtigen Hook-Punkte.
- Die Late-Story-Runtime ist heute deutlich staerker an einen einzelnen Spieler gebunden.
- So bekommt man frueh ein wirklich spielbares Multiplayer-Feature ohne das Save-/Runtime-System sofort an zu vielen Stellen aufzureissen.

## Party-Modell

Empfehlung: kleine Quest-Party mit Leader.

Regeln:
- Ein Spieler ist in genau `0` oder `1` Quest-Party.
- Der Leader laedt online Spieler ein.
- Die Invite-Antwort laeuft zuerst ueber Commands und klickbare Chat-Texte, nicht ueber eine neue GUI.
- Der Leader ist im ersten Schnitt der Quest-Starter fuer shared Quests.
- Jedes Party-Mitglied kann den gemeinsamen Fortschritt sehen.
- Der Claim kann vom Leader oder von einem Party-Mitglied ausgeloest werden, wirkt aber auf die ganze Party.

Aktuelle Commands im Beta-Schnitt:
- `/vq party show`
- `/vq party invite <player>`
- `/vq party accept`
- `/vq party decline`
- `/vq party leave`
- `/vq party disband`
- `/vq party share daily accept|decline`
- `/vq party share weekly accept|decline`

Empfohlene harte Regeln fuer Phase 1:
- nur Online-Spieler
- Logout startet im aktuellen Beta-Schnitt eine `10 minute`-Grace-Phase; erst danach wird der Spieler entfernt
- empfohlene Party-Groesse: `4`

Das haelt den ersten Schnitt robust und verhindert festhaengende Sessions.

## Datenmodell

Empfohlene neue Schicht in `1.21.11`:
- neues Package `de.quest.party`

Empfohlene neue Klassen:
- `QuestPartyService`
- `QuestPartyData`
- `QuestPartyInvite`
- `PartyQuestSession`
- `PartyQuestCategory`

Empfohlene Erweiterungen:
- `PlayerQuestData` bekommt `activePartyId` oder gleichwertige Mitgliedsinfo
- `QuestState` bekommt `Map<UUID, QuestPartyData> parties`
- `QuestState` bekommt `Map<UUID, QuestPartyInvite> pendingInvites`

Empfohlene Session-Form pro Kategorie:
- `dailySession`
- `weeklySession`
- spaeter `storySession`

Ein `PartyQuestSession` sollte mindestens enthalten:
- Quest-Kategorie
- Quest-ID / Typ / Arc+Chapter
- Tag/Zyklus, damit alte Sessions sauber verfallen
- `leaderId`
- `Set<UUID> participants`
- `Map<String, Integer> sharedInts`
- `Set<String> sharedFlags`

Wichtig:
- Shared Objective-State liegt in der Session
- persoenlicher Hilfs-State bleibt in `PlayerQuestData`

## Sehr wichtige Architekturentscheidung

Die bestehenden Low-Level-Methoden sollten nicht stillschweigend global auf Party-Verhalten umgebogen werden.

Nicht empfehlenswert:
- `DailyQuestService.getQuestInt(...)` fuer alles automatisch party-aware machen
- `StoryQuestService.setQuestInt(...)` fuer alle Keys blind teilen

Empfehlung:
- neue explizite Objective-APIs pro Kategorie einfuehren

Beispiel Daily:
- `getObjectiveInt(...)`
- `setObjectiveInt(...)`
- `addObjectiveIntClamped(...)`
- `hasObjectiveFlag(...)`
- `setObjectiveFlag(...)`

Die alten `getQuestInt`-/`setQuestInt`-Methoden bleiben fuer lokalen Hilfs-State verwendbar.

Vorteile:
- weniger versteckte Seiteneffekte
- Baseline-Keys bleiben lokal
- Quest-fuer-Quest Migration ist kontrollierbar

## Konkrete Daily-Beispiele

### `WheatHarvestDailyQuest`

Shared:
- `DailyQuestKeys.WHEAT_PROGRESS`
- `DailyQuestKeys.BREAD_PROGRESS`

Lokal:
- `DailyQuestKeys.LAST_BREAD_CRAFTED`

Konsequenz:
- beide Spieler koennen Brot craften und damit denselben `BREAD_PROGRESS` fuellen
- jeder Spieler behaelt aber seine eigene Crafting-Baseline

### `MarketRoundsDailyQuest`

Shared:
- `DailyQuestKeys.MARKET_ROUNDS_VISITS`
- `DailyQuestKeys.MARKET_ROUNDS_TRADES`
- `DailyQuestKeys.MARKET_ROUNDS_VISITED_PREFIX + villagerUuid`

Konsequenz:
- wenn ein Party-Mitglied einen Villager schon fuer die Runde gezaehlt hat, zaehlt derselbe NPC nicht doppelt
- Villager-Talks gehen direkt in denselben Party-Pool

## Konkrete Weekly-Beispiele

Dasselbe Muster gilt fuer Weeklys:
- Objective-Keys teilen
- Baseline-/Expected-Keys lokal lassen

Beispiele:
- `WeeklyQuestKeys.HARVEST_WHEAT` teilen
- `WeeklyQuestKeys.HARVEST_LAST_BREAD` lokal lassen
- `WeeklyQuestKeys.PASTURE_WOOL` teilen
- `WeeklyQuestKeys.PASTURE_EXPECTED_WOOL` lokal lassen

## Shared Turn-in und Claim

Nur Progress teilen reicht nicht.

Warum:
- Viele Quests verlangen beim Abschluss echte Inventar-Items.
- Wenn der Fortschritt gemeinsam `20/20` ist, der Turn-in aber weiter nur ein einzelnes Inventar prueft, fuehlt sich das System kaputt an.

Empfehlung fuer Phase 1:
- neuer `PartyTurnInService` oder Teil von `QuestPartyService`
- Zaehlen ueber alle aktuellen Party-Mitglieder
- Verbrauch ueber alle aktuellen Party-Mitglieder
- ein einziger Claim schliesst die Quest fuer die ganze Party ab
- Rewards, Reputation und Folgeprogress werden fuer jeden Party-Teilnehmer einzeln verteilt

Das ist wichtig, weil die aktuelle Einzelspieler-Logik sonst unfaire Doppel-Claims oder unmoegliche Turn-ins erzeugt.

## Reward-Fanout

Empfohlenes Verhalten:
- gemeinsamer Claim markiert die Quest fuer alle Session-Teilnehmer als abgeschlossen
- Currency, Levels, Reputation und Folge-Unlocks werden fuer jeden Spieler einzeln ueber die bestehenden Services vergeben
- projektspezifische Boni bleiben pro Spieler berechnet

Das passt gut zur bestehenden Architektur, weil:
- `DailyQuestService.deliverCompletion(...)`
- `WeeklyQuestService.deliverCompletion(...)`
- `StoryQuestService.deliverCompletion(...)`

schon heute pro Spieler arbeiten und nur ueber einen Party-Fanout aufgerufen werden muessen.

## Story fuer spaeter

Story ist moeglich, aber nicht komplett im ersten Schnitt.

Relativ gut party-faehige Story-Teile:
- Villager-Talk-Kapitel
- reine Kill-/Harvest-/Trade-Zaehler
- Kapitel ohne individuellen Runtime-Anker

Deutlich riskantere Story-Teile:
- `ShadowsTradeRoadEncounterService`
- Home-Village-Binding
- Rescue-/Final-Target-Koordinaten
- Courier-/Letter-Runtime

Empfohlene Story-Phase 2:
- nur Kapitel freischalten, bei denen alle Party-Mitglieder exakt dieselbe Arc- und Chapter-Lage haben
- `StoryQuestKeys` in shared objective keys vs lokale runtime keys trennen
- erst danach an `Shadows`-Runtime-Sessions gehen

## Networking und UI

Phase 1 braucht keine neue grosse Screen-Flaeche.

Genug fuer den ersten Schnitt:
- Invite als Chat-Nachricht mit klickbarem Accept/Decline
- `QuestTracker` bekommt eine kleine Party-Markierung
- `QuestMaster` und Journal zeigen, dass die aktive Quest geteilt ist

Betroffene Stellen:
- `1.21.11/src/main/java/de/quest/network/Payloads.java`
- `1.21.11/src/main/java/de/quest/network/QuestNetworking.java`
- `1.21.11/src/client/java/de/quest/client/network/ClientQuestNetworking.java`
- `1.21.11/src/main/java/de/quest/quest/QuestTrackerService.java`
- `1.21.11/src/main/java/de/quest/questmaster/QuestMasterUiService.java`
- `1.21.11/src/client/java/de/quest/client/screen/QuestMasterScreen.java`

## Empfohlene `1.21.11`-Implementierungsreihenfolge

1. Party-Datenmodell und Commands einfuehren
2. Invite-/Accept-/Leave-Flow plus Chat-Feedback bauen
3. Shared Daily-Session mit `WheatHarvestDailyQuest` und `MarketRoundsDailyQuest` als Referenzquesten anbinden
4. Shared Turn-in und Reward-Fanout fuer Daily sauber machen
5. restliche normale Daily-Pool-Quests auf objective-vs-local aufteilen
6. dieselbe Schicht auf Weekly portieren
7. erst dann UI-Markierungen nachziehen
8. danach Story-Phase 2 planen

## Port-Pfad nach `26.1.2`

Die moderne Linie ist strukturell fast gleich aufgebaut.

Paritaetsrelevante Unterschiede:
- `PersistentState` vs `SavedData`
- `ServerWorld` vs `ServerLevel`
- `Text` vs `Component`
- andere Packet-/Codec-Typen in `Payloads`
- Mojang-Namen statt Yarn-Namen

Das spricht fuer dieses Vorgehen:
- Design und erster funktionaler Schnitt in `1.21.11`
- danach gezielter Verhaltens-Port nach `26.1.2`
- keine Dateikopie, sondern API-saubere Uebertragung

## Bereits entschiedene Produktpunkte fuer den aktuellen Beta-Schnitt

- Die Party ist restart-persistent.
- Shared `Daily` und `Weekly` laufen ueber explizite Chat-Offers fuer spaet beitretende Party-Mitglieder.
- Die Party-Oberflaeche ist bewusst dedicated-server-only und in Singleplayer/Integrated Worlds versteckt.
- `Story` und `Special` bleiben im aktuellen Beta-Schnitt ausserhalb der Shared-Quest-Logik.
- Der erste reale Koop-Schnitt bleibt auf `1.21.11`, bevor eine Portierung nach `26.1.2` angegangen wird.

## Weiterhin offen fuer spaetere Wellen

- wie weit `Story` spaeter mit einer echten Allowlist oder Kapitel-Gleichstand koop-faehig wird
- ob die aktuelle Party-Groesse und Reward-Verteilung nach echten Serverplaytests noch nachjustiert werden muessen
- wann der Verhaltens-Port nach `26.1.2` erfolgen soll

## Meine klare Empfehlung

Wenn das Feature stabil und ohne Quest-Regressions starten soll:
- `1.21.11` zuerst
- Commands + Chat statt neuer GUI zuerst
- Daily und Weekly als erster echter Koop-Schnitt
- shared objective state explizit von local helper state trennen
- gemeinsamer Turn-in und Reward-Fanout als Pflichtbestandteil
- Story und `Shadows` erst in einer zweiten Welle

So bekommt ihr ein Multiplayer-System, das fuer die gezeigten Beispiele sofort logisch funktioniert, ohne die spaeteren komplexen Story-Runtimes vorschnell mitzuziehen.
