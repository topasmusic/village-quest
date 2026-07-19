# Changelog

## 2.0.0 - Roads Between Villages

Release date: pending

### Licensing and packaging

- Adopted a mixed license for `2.0.0`: functional source code is now `LGPL-3.0-only`, while original Village Quest assets, narrative content, branding, and promotional material remain All Rights Reserved under the project licensing notice.
- Added the complete GPLv3 and LGPLv3 texts, preserved the historical MIT notice for already published releases and compatible carried-forward material, and documented third-party software and assets explicitly.
- Recorded every unresolved legacy NPC and caravan skin by shipped filename. Those files are excluded from both the LGPL and the Village Quest ownership claim and remain the final asset-provenance release gate until replaced or cleared.
- Updated Fabric metadata to advertise both the code and protected-asset terms, and embedded the complete licensing package into runtime and sources JARs.

### Unified interfaces and live navigation

- Limited every Pilgrim stock rotation to at most four offers. New spawns and Merchant's Seal rerolls use the four-offer cap, while Pilgrims loaded from older saves automatically trim a previously stored fifth offer instead of carrying it forward.
- Fixed the Pilgrim shop with a clipped four-row goods viewport, defensive mouse-wheel scrolling, matching hitboxes, and one centered title; offers and footer text no longer escape their panels.
- Replaced the cramped Questmaster board with the compact Journal-style `392 x 220` dashboard, dedicated Daily/Weekly/Story/Special icon tabs, a separate quest list, and a scrollable detail card while preserving actions, timers, locked states, and multiplayer party controls.
- Unified Journal, Questmaster, Pilgrim, Caravan Ledger, and Route Office around the same dark-oak, parchment, brass, teal, world-shade, and soft ambient/lower-right shadow system.
- Replaced the long Journal tutorial flow with a compact five-tab dashboard and concise English, German, and Spanish guidance.
- Added the cached terrain-backed full route map and live corner minimap with player, village, caravan, incident, and surveyed-route overlays. The full map supports left-button dragging and defers terrain resampling until the gesture ends.
- Added the configurable default `,` minimap key and `/vq routes minimap`, plus improved overlapping marker tooltips and player/caravan navigation details.
- Added five route-owned caravan outfit/color families shared by merchants, route cards, the full map, and minimap.
- Removed ordinary caravan rubber-banding, improved safe-footing and formation recovery, delayed hard recovery until groups are off-screen, and strengthened pause/removal cleanup so stale merchants and attackers cannot accumulate.
- Added compact running/paused route indicators, readable wrapped tooltips, corrected map/footer alignment, persistent destination renaming in the ledger, and `/vq routes rename <1-5> <name>`.
- Normalized German player-facing text to real UTF-8 umlauts and carried the deterministic normalization helper into this line.
- Hardened vanilla and CTOV village registration through `#minecraft:village`, the structure's real footprint, and a living normal-villager requirement so large settlements work while abandoned and zombie villages remain rejected.
- Added `/vq admin routes testevent <1-5> <event|clear>` and admin-only Questmaster/Pilgrim UI fixtures for repeatable functional and layout QA.
- Hardened the older `Shadows on the Trade Road` convoys with broader stable-footprint checks, all-or-nothing merchant spawning, and recovery for merchants knocked below or outside the encounter area.

### Trade Guild, rewards, and long-term progression

- Added the `Village Trade Guild`, a five-rank long-term progression built from route count, road quality, resolved incidents, and completed freight contracts.
- Added a rotating board of up to three daily freight contracts. Players assign one to a route, load the requested cargo, and complete it on that route's next arrival; long routes, matching specializations, and a Trade Office increase the payout.
- Added six route specializations (`general`, `provisions`, `forge`, `livestock`, `courier`, and `guarded`) and six permanent Silvermark investments: reinforced wheels, lantern crews, weather covers, escorts, insurance, and trade offices.
- The `Market Charter` now opens the ledger, map, and one provisional route much earlier. Completing `The Empty Caravan` and its `Caravan Yard` expands that network to the full five routes and enables recurring incidents.
- Rebalanced route economics around installed path length, security, and quality. Purposeful detours are now paid for, ordinary route income has network-wide daily limits, and offline proceeds wait in a capped trade-office escrow until the owner returns.
- Route incidents are now limited to one active event per player network, no longer mark a route dangerous merely by starting, occur less often on high-quality/fully upgraded roads, and grant tiered rewards based on difficulty and distance. The first full-network incident remains a guaranteed tutorial.
- Added long-term reputation Mastery levels beyond `200` reputation. Mastery is capped at five per track and unlocks a second daily reroll after three combined Mastery levels rather than stacking unlimited raw power.
- Added `/vq daily reroll`, `/vq routes guild`, `/vq routes contracts`, contract accept/supply actions, route specialization, and route upgrade commands. The ledger map now shows guild rank, daily earnings, route specializations, and installed upgrades.
- Added the `Roadwarden Horn` at `200` Monster Hunting reputation. It posts one daily road watch that prevents the next non-tutorial incident and points the Wayfinder toward live route trouble on later uses.
- Improved the other long-term relic loops: sneak-use of the `Shepherd's Flute` now holds nearby animals in place, while the completed `Merchant's Seal` prioritizes unlocked collectibles the player has not bought before.
- Reworked quest experience into bounded level-bar rewards that remain worthwhile at both low and very high vanilla levels. Normal Dailies grant `1.5/3/4.5` bars, normal Weeklies `5.25/6/6.75`, Pilgrim tiers `3/4.5/6`, story chapters scale up to `7.5`, and the Forge Charter adds another `0.75` bar; light/heavy repeatable profiles still adjust their payout.
- Raised daily wallet rewards to `3/6/12` Silvermarks, changed `Fresh Finery for Your Companions!` from one recolor to a tuned multi-recolor objective, and made every Pilgrim combat offer include an Overworld-safe choice plus a second alternative.
- Repriced all large Pilgrim supply bundles individually, reduced the excessive Provisions Satchel baseline and jackpot odds, raised the underpaid Forge/Market/Pasture story rewards, and accepted gold or diamond horse armor in the `Restless Pens` finale.
- Village-project bonuses now apply consistently to the chapter that unlocks them instead of only some reward types seeing the newly completed project.
- Expanded `/vq admin routes testsetup [player]` with all five specializations, multiple upgrade states, a rank-five guild test profile, contract access, and enough wallet funds to exercise the remaining investments.
- Completed a full native-client polish pass on the `26.2` reference line for the expanded network: all five routes, map tooltips, guild commands, contract loading and arrival, specialization/refit, upgrades, the Roadwarden Horn, daily rerolls, reputation Mastery, low- and high-level XP rewards, and save/reload persistence were verified together.
- Freight contracts now complete on their assigned arrival even after ordinary route income has reached its daily network cap. The reputation admin parser also accepts `monster_hunting` and `monster-hunting`, and the English specialization result no longer repeats the word `freight`.
- Existing saves now receive a one-time progression backfill on login: players who already unlocked the `Market Charter` or `Caravan Yard` receive a missing `Caravan Ledger`, and players who already reached `200` Monster Hunting receive a missing `Roadwarden Horn`. Persistent claim flags and inventory checks prevent repeated grants.

### The roads between villages

- Added the six-chapter late story `The Empty Caravan`, which follows the aftermath of `Shadows on the Trade Road` through an abandoned wreck, a trail of clues, village witnesses, a player-chosen amnesty or justice plan, a bait-caravan defense, and the rebuilding of the roads between villages.
- Completing the new story unlocks the permanent `Caravan Yard` village project and the `Caravan Ledger`, creating a new trade-route endgame rather than ending the road storyline at one final battle.
- Players can register villages and maintain up to five persistent trade routes. Routes continue their journeys without force-loading chunks, make deliveries, earn route income, improve from successful intervention, and can be paused or removed from the ledger map.
- Added persistent route surveying for genuine player-built detours. A route can hold up to `48` marked bends or anchors; the map, virtual travel distance, road-quality sampling, event targets, and visible caravan navigation all follow the installed line instead of a forced straight connection.
- Active routes now materialize as named three-merchant caravans when players are nearby. They visibly travel toward their destination, favor suitable player-built road surfaces, and disappear safely back into the persistent simulation outside observation range.
- Expanded the parchment-style trade-route map for five compact route rows, waypoint polylines, survey/install/cancel controls, and a guarded two-click route-removal action alongside caravan positions, direction, security, road quality, earnings, and emergencies.
- Roads now matter mechanically: path blocks, gravel, cobblestone, stone bricks, planks, slabs, and lighting improve route quality, while better routes move faster and earn more.
- Added eight recurring road situations: broken wheels, injured pack animals, washed-out bridges, false distress calls, hungry travelers, road tolls, missing couriers, and storm camps.
- The `Wayfinder's Compass` now automatically selects dedicated targets for route emergencies and `The Empty Caravan` investigations.
- Added `/vq routes`, `/vq routes register`, `/vq routes remove <1-5>`, and the `/vq routes survey ...` workflow. `/vq admin routes testsetup [player]` now creates a complete five-route surveyed test network with two live event scenarios; `/vq admin routes reset [player]` removes that route data again.
- Hardened caravan and ambusher placement in dense or uneven terrain. Physical groups now search nearby safe surfaces, and a false-distress event can no longer complete successfully when no attackers were actually spawned.
- Polished the new interfaces after an in-client QA pass: route-event help wraps into a compact tooltip, and active story chapters now show a disabled `Still Working` action instead of an empty button area.
- A focused five-route client pass on the reference line confirmed survey start, waypoint marking, installation, command removal, and guarded map removal; it also tightened the compact row layout and extended the second-click removal window to `30 seconds`.

### Compatibility and maintenance

- Farming quests now recognize successful mature-crop harvests from compatible right-click-and-replant mods, including the event flow used by `RightClickHarvest`. Holding bone meal during that harvest no longer loses progress, while merely clicking a ripe crop still grants nothing.
- Updated the journal, project overview, item reference, complete-reset cleanup, English/German/Spanish localization, and maintainer documentation for the new caravan systems.

## 1.22.8 - Reliable Resets And Cleaner Sessions

Release date: 2026-07-17

- The complete admin reset now also clears quest parties, invites, shared sessions, and reconnect-grace state, so old multiplayer data cannot return on the next server shutdown or restart.
- `Restless Pens` now shows both finale requirements correctly in English, German, and Spanish: the gathered bell call and the required Diamond Horse Armor.
- Player-bound journals, trackers, Questmaster screens, and relic hints now clean up on disconnect, while every transient cache, including late-road encounters, resets at server start and stop so stale session state cannot carry into later worlds.
- Repository checks now build all three maintained Minecraft lines from the root and verify JSON, translation keys, placeholders, and directly referenced localization entries before release artifacts are produced.

## 1.22.7 - Shard Bonus Daily Tracking Fixes

Release date: 2026-07-01

- Fixed action-based `Daily` quest tracking for shard bonus quests, so `Autumn Harvest` and the other event-driven dailies now progress correctly when accepted through the `Questmaster` shard offer.

## 1.22.6 - Quest Tracking Fixes And Questmaster Hover Preview

Release date: 2026-06-23

- Fixed `Fresh Finery for Your Companions!` so the quest now completes from the real successful `Wolf` and `Cat` collar recolor path again instead of a fragile early interaction callback.
- Fixed successful hive-harvest progress tracking for the `Questmaster`, so `The Failing Harvest` chapter 2 and related bee quests now advance on real honey-bottle and honeycomb harvests again.
- Fixed sheep-based quest progress tracking so `Wool Weaver`, `Stall and Pasture`, and `Restless Pens` chapter 2 now count only real successful shearing events and no longer depend on the fragile early entity-use hook.
- Realigned all `The Failing Harvest` chapter targets with the shipped quest text again: `16/8` crops, `3/1` hive goods, `6/4` baked goods, and `2/4` trade proof.
- `Questmaster` descriptions now open in a larger hover preview so long quest text can be read without enlarging the whole board.
- Project-gated `Pilgrim` wares now wait for completed village-project progress consistently again, so `Village Ledger Plaque` and `Village Ledger Desk` no longer leak into the shop before the first real village project is finished.

## 1.22.5 - Quest Parties, Variety, Localization, And Client Compat

Release date: 2026-05-14

- The multiplayer quest-party batch is now fully promoted into a stable release on the modern line: dedicated-server quest parties, clickable chat invites, restart-persistent shared sessions, `10 minute` reconnect grace, and shared `Daily`, `Weekly`, core `Story`, and normal `Pilgrim` combat-contract progress now ship as part of the main release.
- The `Questmaster` now has a dedicated brown party button on shareable quests, party management stays hidden on singleplayer or integrated worlds, and the journal keeps its `Questmaster` shortcut visible on every page instead of only the first one.
- Repeatable `Daily`, `Weekly`, and `Pilgrim` quests now use stored `light`, `normal`, or `heavy` target profiles with matching reward scaling, and the non-`Shadows` story arcs plus repeatables now avoid the old obvious stack and half-stack requirement numbers.
- The story follow-up cooldown between completed `4`-chapter arcs was reduced from `3 hours` to `1 hour`.
- A full Spanish `es_es` localization now ships on this line as well, with the original translation provided by `Lutte` and the missing newer quest-party strings and placeholder fixes filled in.
- The reported `Bendable Cuboids` and `MTGCard` client conflicts were addressed directly on this line: humanoid quest NPCs now use a safer held-item renderer when needed, the old inventory journal overlay can disable itself safely, the inventory keeps a small fallback `J` journal button, and the journal/admin journal screens now avoid the crashy arrow and `GuiGraphics` compatibility paths in the reported combo.

## 1.22.1-beta.3 - Multiplayer Quest Party Beta

Release date: 2026-05-14

- Ported the `1.21.11` multiplayer quest-sharing beta batch to `26.1.2`, including dedicated-server-only quest parties, clickable chat invites, and `/vq party ...` commands for `show`, `invite`, `accept`, `decline`, `leave`, `disband`, and shared daily/weekly/story/pilgrim offer acceptance.
- The `Questmaster` UI now exposes the same party drawer on shareable `Daily`, `Weekly`, and core `Story` entries, including current members, invite candidates, and direct invite/leave/disband actions; the party toggle now sits as a dedicated brown button above the right quest header panel and remains hidden on singleplayer or integrated worlds.
- Shareable `Daily`, `Weekly`, core `Story`, and normal `Pilgrim` combat-contract flows now support pooled progress, pooled turn-in where applicable, explicit chat offers for late joiners, `10 minute` disconnect grace, and restart-persistent party and shared-session state on `26.1.2`.
- Repeatable `Daily`, `Weekly`, and `Pilgrim` quests now roll and persist synced `light`, `normal`, or `heavy` target profiles on this line as well, scale their numeric rewards to match that profile, and avoid obvious stack or half-stack target counts; the non-`Shadows` story arcs also received the same authored irregular target numbers.
- The journal now keeps its `Questmaster` shortcut button visible on every page instead of only the first page on `26.1.2`.

## 1.22.0 - Shadows On The Trade Road And Admin Reset

Release date: 2026-04-24

- The Fabric Loader dependency was bumped to `0.19.2` on both maintained lines.
- The modern `26.1.2` line now also uses Fabric API `0.146.0+26.1.2`.
- The modern `26.1.2` line now includes the new late `Questmaster` arc `Shadows on the Trade Road`, ported from the `1.21.11` implementation into the Mojang-mapped codebase.
- The new road-defense arc appears after `Watch Bell` as a locked story entry and unlocks after `3` completed `Pilgrim` combat rumors.
- That arc adds village rumor gathering, village-aware interview tracking, a toolsmith compass calibration step, nighttime caravan rescue encounters, a courier handoff chapter, and a larger final convoy defense with new `Caravan Merchant` and `Traitor` entities.
- Late-road encounter balance uses staggered `3`-second wave pulses with up to `2` hostile spawns per pulse, a `16-26` block hostile spawn ring, `5`-second initial `Glowing` on each wave hostile, and `3` final-wave `Traitor` elites.
- Caravan encounter anchors reject water and other non-solid footing, escaped raiders leash back into the ambush area, and the last `1-2` remaining enemies get delayed `Glowing` markers so hidden mobs cannot stall the quest.
- Caravan merchants now have `45` hearts, roughly `1/3` of each convoy can weakly defend with wooden swords, and convoy spawn spacing avoids overlap glitches.
- Active rescue and final-convoy quest status lines now show how many hostile mobs remain in the current encounter.
- The `Wayfinder's Compass` gained story-bound modes for `Caravan Distress` and `Guild Convoy` while the late trade-road arc is active.
- New admin helpers were added for the late-road batch: `/vq admin story shadows unlock`, `/vq admin story shadows testrescue`, and `/vq admin story shadows testfinal`.
- A new admin command, `/vq admin reset complete`, now wipes the saved Village Quest state for every player, including wallet, reputation, story/project progress, special quest data, pilgrim contracts, cooldown state, live quest sessions, and spawned Village Quest NPCs.
- Villager talk objectives now speak back with context-sensitive lore lines in `Market Rounds`, the villager-facing `Market Road Troubles` chapters, and the new `Shadows on the Trade Road` rumor steps instead of progressing silently.
- Villager, toolsmith, and courier dialogue lines were retuned toward a more medieval low-fantasy tone in German and English.
- `Questmaster` and `Pilgrim` screens can now also be closed again through the player's current inventory keybind.
- The `Wayfinder's Compass` now keeps a chunkier pixel-art outer ring while preserving the original readable inner dial and cardinal letters.
- Remaining Wolkensprung extraction scripts, templates, comments, and leftover lang keys were removed from this line.
- README and wiki command/install notes were refreshed for the current dependency stack and admin surface.

## 1.21.1 - First-Use Journal Onboarding And Minecraft 26.1.2 Update

Release date: 2026-04-10

- Village Quest has been updated for Minecraft `26.1.2`.
- The Fabric API dependency was bumped to `0.145.4+26.1.2`.
- The current modern line now lives in the renamed `26.1.2` folder, and README/wiki install docs were refreshed to match the new jar and path names.
- The inventory journal tab now shows a one-time first-use hint with a small arrow in the inventory until it has been clicked once.
- After opening the journal, the `Questmaster` button on the first page now also gets its own one-time guided highlight.

## 1.21.0 - Pilgrim Shop Expansion And Story Cooldown Update

Release date: 2026-04-09

- The pilgrim wallet header now expands its coin spacing so multi-digit `Crown` and `Silvermark` counts no longer overlap the icons.
- Pilgrim shop prices were raised sharply across the full catalog with a central `3x` pricing pass.
- Story wallet rewards were reduced to `70%` of their former values so main-story completion no longer funds the shop as quickly by itself.
- Player and admin commands now also live under the new roots `/villagequest ...` and `/vq ...`.
- The old direct roots such as `/questadmin`, `/questmaster`, `/dailyquest`, `/wallet`, `/reputation`, `/journal`, and `/questtracker` were removed, so commands now start only with `/villagequest ...` or `/vq ...`.
- Journal help pages, clickable chat actions, tracker hints, README notes, and command docs were updated to use the new `/vq` command structure.
- The inventory journal tab now also opens the journal through `/vq journal`, and the remaining live command links were checked so they match the new root structure.
- The `Merchant's Seal` hover text was shortened in English and German so it fits more cleanly in inventory tooltips.
- The first Pilgrim shop-overhaul slice added `Road Camp Kit`, `Village Ledger Desk`, `Apiary Supply Crate`, `Smithing Supply Rack`, `Market Stall Kit`, `Pasture Tack Bundle`, and `Watch Post Kit` as new themed bundle wares.
- `Apiary Charter Plaque` was rebuilt into a real wall-mounted decorative plaque block with depth, custom front art, and proper block placement instead of a flat hanging item.
- The first plaque follow-up fixed wall placement, restored the proper localized item name, and removed the visible transparent border artifact from the front texture.
- The second plaque follow-up restored a visible wood backing/frame, forces wood particles for breaking, and adds a second loot-table path safeguard so the plaque should drop back as an item reliably.
- A second custom-art shop batch added `Village Ledger Plaque`, `Forge Charter Plaque`, `Market Charter Plaque`, `Pasture Charter Plaque`, and `Watch Bell Reliquary`.
- The temporary standing decor experiment was rolled back, so `Pilgrim Trade Chest`, `Milestone Marker`, and `Weathered Signpost` are no longer part of the active unreleased batch.
- The pilgrim now offers a broader premium wave of decorative custom heads, including barrels, shelves, utility pieces, and plushies.
- Decorative custom head wares now cost `5 Crowns`, and each pilgrim can offer at most one of them per visit.
- The `Skeleton Plushie`, `Zombie Plushie`, and `Creeper Plushie` now unlock only after completing their matching Pilgrim combat contract once.
- Decorative custom head items now use proper English and German item names plus short hover lore, so bought heads match the shop presentation more cleanly.
- `Apiary Charter Plaque` now uses the same full plaque artwork style as the later charter plaques so the whole series reads more consistently.
- Plaque and reliquary wares now cost `3 Crowns 5 Silvermarks`, and their shop text plus item lore were shortened into cleaner trophy-style descriptions.
- Completing a full four-chapter village story now starts a real `3 hour` cooldown before the next story appears, and the `Story` tab shows both a waiting note and a live countdown timer during that pause.
- The shared plaque block model now adds a consistent outer wood frame across the whole plaque/reliquary line, so the series reads more uniformly in-world and in inventory.
- Pilgrim detail prices now shrink to stay on one line, so mixed-currency prices such as `3 Crowns 5 Silvermarks` no longer collide or wrap awkwardly.
- Plaque art was downscaled to a chunkier raster size, and `Starreach Ring`, `Merchant's Seal`, plus `Shepherd's Flute` were reduced to `32x32` item textures for a more Minecraft-like look.
- Legacy compatibility junk items such as the old mini blocks, test items, and decorative leftovers were removed from the active registry, and the remaining coin item IDs now use English registry paths: `legacy_copper_penny`, `silvermark`, and `crown`.
- Bundle quantities were adjusted explicitly so `Bed`, `Spyglass`, `Book and Quill`, and `Lectern` now use the intended `1x` counts without a global bundle rule.
- All multi-item pilgrim bundles now use the same price as the `Provisions Satchel`, so satchels, kits, crates, desks, and similar bundle wares stay aligned.
- Pilgrim shop payloads now also shorten long decor-head profile names on `26.1.1`, so custom-head offers no longer risk a packet-encoding disconnect when opening the trader.
- Pilgrim offer locking now blocks both reputation-gated and village-project-gated wares correctly, including direct-buy paths.

## 1.20.4 - Quest Balance And Systems Update

Release date: 2026-04-03

- Daily, weekly, story, and special quests received a broad balance pass across the full `26.1.1` content set.
- Story rewards were normalized by chapter and several story chapters were redesigned or expanded:
  - `The Silent Forge` now includes a much larger mining/smithing ramp and a full enchanted sword-and-armor finale.
  - `Market Road Troubles` now leans harder into crafted ledgers, named professions, and a large village bell finale.
  - `Restless Pens` now pivots its third chapter into horseback exploration and its finale into a herd-and-horse-armor readiness check.
- Special quests were expanded:
  - `Merchant's Seal` now separates villager trading, villager buying, and pilgrim buying requirements.
  - `Shepherd's Flute` now uses breeding plus taming objectives for `Wolf`, `Cat`, and `Parrot`.
  - `Apiarist's Smoker` now tracks bee breeding and crafted honey blocks and consumes all required hive goods at turn-in.
  - `Wayfinder's Compass` now also requires mined `Lapis Lazuli` alongside `Redstone`.
- Tracked mining drops were expanded so `Raw Gold` and `Lapis Lazuli` count correctly with the same self-earned mining logic.
- Pilgrim offers are now single-use per spawned pilgrim: once bought, that offer stays unavailable until a new pilgrim appears.
- The internal `Admin: Core Systems Test` was expanded to cover the new risky hook paths such as taming, crafted honey blocks, villager purchases, and the added mining-drop routes.
- The admin wallet commands no longer print duplicate chat feedback when an admin adjusts their own wallet.
- English and German quest texts were updated to match the new objectives and progress labels.

## 1.20.3 - Minecraft 26.1.1 Hotfix Update

Release date: 2026-04-03

- Village Quest has been updated for Minecraft `26.1.1`.
- The Fabric stack was refreshed to the latest stable loader and Fabric API builds for `26.1.1`.
- Internal release files and documentation were refreshed for the `26.1.1` line.

## 1.20.2 - Story Turn-In And Painting Fixes

Release date: 2026-04-01

- `The Failing Harvest` now truly requires and consumes the final `Bread` and `Baked Potato` turn-in items.
- `The Silent Forge` received several hand-in fixes:
  - chapter 2 now also consumes the required `Iron Ingots`
  - chapter 3 now only accepts fresh undamaged crafted tools instead of also taking used ones
  - chapter 4 now recognizes bought `Sharpness` enchanted books reliably again
- The `Surveyor's Compass` netherite pickaxe hand-in was tightened as well so only undamaged tools count.
- Custom `Village Quest` paintings now preserve their correct custom item when broken instead of turning back into a normal vanilla painting.
- `Questmaster` summon placement now scores nearby spawn spots by both horizontal and vertical distance so indoor summons stay closer to the player's actual floor.
- Interacting with the `Questmaster` now refreshes his full 30 second despawn timer so he does not vanish immediately after closing the UI.
- The `Magic Shard` item texture was reduced from the old oversized render to a smaller, more vanilla-like icon.

## 1.20.1 - Minecraft 26.1 Update

Release date: 2026-03-30

- Village Quest has been updated for Minecraft `26.1`.
- The inventory journal bookmark now renders more cleanly again.
- `Smoke over Blackstone` now shows its real objectives directly instead of the old unclear wording.
- Several delivery quests now check and consume all of their listed hand-in materials consistently again.
- `Smelting for the Smithy` now labels its ingot progress more clearly as `Iron Ingots`.
- Many daily, weekly, story, and pilgrim quest texts were cleaned up for clearer progress labels and more consistent item wording in both English and German.
- Several gathering, mining, and shearing quests were updated:
  - self-earned progress is tracked more reliably
  - `Fortune` bonus yield now counts properly where it makes sense
  - several hand-in quests now truly require their materials again at turn-in
- Relevant hand-in quests now show a clear red hint when progress is finished but the required turn-in items are no longer in the inventory.

## 1.20.0 - Village Stories Foundation And Progression Clarity Rework

Release date: 2026-03-29

- The `Questmaster` received a major `Story` expansion with four visible village storylines:
  - `The Failing Harvest`
  - `The Silent Forge`
  - `Market Road Troubles`
  - `Restless Pens`
- New permanent `Village Projects` were added:
  - `Village Ledger`
  - `Apiary Charter`
  - `Forge Charter`
  - `Market Charter`
  - `Pasture Charter`
  - `Watch Bell`
- `Market Rounds` was added as a second trade-focused daily.
- `Story` now unlocks after the first normal daily, while `Special` unlocks after the first real reputation gain.
- Relic quests now feel more like earned follow-up commissions because they are tied to both reputation and story progress.
- Village progression was split more clearly:
  - the `Questmaster` focuses more strongly on village-core work
  - late dangerous road jobs were moved toward the `Pilgrim`
  - `Watch Bell` now unlocks automatically once the four village-core stories are completed
- The `Pilgrim` was streamlined:
  - `Roadmarks for the Compass` is now a one-time special contract
  - later on, there is exactly one dangerous road rumor per day
- The `Wayfinder's Compass` was expanded into a staged relic with `Home`, `Field Bearings`, and `Roadmarks`.
- Quest rewards were changed from raw XP values to direct level gains.
- The journal, quest tracker, Questmaster UI, Pilgrim UI, and NPC presentation all received a major polish pass.
- Many smaller progress, turn-in, and UI issues were cleaned up.

## 1.19.5 - Quest Progress Hotfixes

- Villager trade quests now count reliably again, even when results are taken quickly with `Shift`-click.
- The anvil step of the shard quest is now tracked more reliably as well.
- `Pet Collar` became stricter and now only counts real recolors on the player's own tamed wolf or cat.

## 1.19.4 - NPC Self-Defense Update

- The `Questmaster` and `Pilgrim` can now defend themselves when attacked by players.
- Both gained warning lines, combat lines, and visible weapon behavior.
- Killing the `Questmaster` now gives the attacker a personal summon cooldown.

## 1.19.3 - Furnace Quest Hook Fixes

- Furnace-based quests now count correctly again, even when finished items are taken quickly from the output slot.
- Several cooking and smithing quests now properly consume their turn-in items.
- The `Provisions Satchel` was upgraded into a better travel reward.

## 1.19.2 - Inventory Compat Follow-Up

- The journal bookmark now sits more naturally on the inventory edge again.
- A better fallback was added for mods that use status-effect sidebars.

## 1.19.1 - Questmaster And Inventory Polish

- The `Questmaster` can now appear more sensibly in caves and underground bases.
- Breeding quests now count real animal pairs more cleanly.
- The journal access in the inventory was rebuilt into an animated bookmark tab.

## 1.19.0 - Reputation Expansion Batch

- The new `Monster Hunting` reputation track was added.
- Four monster-hunting dailies were added:
  - `Zombie Cull`
  - `Skeleton Patrol`
  - `Spider Sweep`
  - `Creeper Watch`
- The relic quests `Apiarist's Smoker` and `Surveyor's Compass` were added.
- Relic unlock thresholds were raised to `200` reputation.
- `Surveyor's Compass` later evolved into `Wayfinder's Compass`.
- Special and relic items were presented more clearly in the journal and UI.

## 1.18.0 - Weekly Quest System

- The weekly quest system was introduced.
- Seven weekly quests were added:
  - `Harvest for the Village`
  - `Bakehouse Stock`
  - `Smith Week`
  - `Stall and Pasture`
  - `Market Week`
  - `Night Watch`
  - `Road Warden`
- Weeklies were integrated into the Questmaster, journal, and quest tracker.

## 1.17.5-1.17.2 - Questmaster And Pilgrim UI Hotfixes

- Several UI passes improved text, buttons, scrolling, timers, and the overall layout of the `Questmaster` and `Pilgrim`.

## 1.17.1 - Guaranteed First Magic Shard

- The first completed quest now always guarantees one `Magic Shard`.
- After that, the normal shard chance applies again.

## 1.17.0 - Questmaster UI V2 And Decline Removal

- The `Questmaster` received its second major board UI.
- Daily accept and cancel flow was simplified.
- The old decline lockout was removed.

## 1.16.9-1.16.1 - Questmaster UI Polish Cycle

- The first Questmaster UI received many layout, tooltip, scrolling, and presentation improvements.
- The `Questmaster` no longer despawns while someone still has the UI open.

## 1.16.0 - Questmaster UI Foundation

- The old chat-only `Questmaster` interaction was replaced with a real quest window.
- The `Daily`, `Weekly`, and `Special` structure was established.

## 1.15.7-1.15.1 - Merchant's Seal Stabilization

- `Merchant's Seal` was stabilized and later made usable with the wandering trader as well.

## 1.15.0 - Relic Questlines Pack 1

- The first two relic questlines were added:
  - `Merchant's Seal`
  - `Shepherd's Flute`

## 1.14.0 - Reputation Unlocks And Pilgrim Progression

- Reputation gained clearer unlock thresholds.
- The `Pilgrim` began tying offers to player progress and reputation.

## 1.13.0 - Village Reputation Foundation

- The first four reputation tracks were added:
  - `Farming`
  - `Crafting`
  - `Animals`
  - `Village Trade`

## 1.12.9 - Leather Daily Removal

- The leather daily was removed from the active quest pool.

## 1.12.8-1.12.2 - Inventory Journal Button Rollout

- The journal received its first direct button in the inventory.
- Position, art, and stability were improved several times.

## 1.12.1 - Journal Player Command Overhaul

- The journal was reorganized and its player command overview was improved.

## 1.12.0 - Quest Progress Feedback And Tracker

- Quest progress now appears directly in the action bar.
- Milestone feedback and the quest tracker were added as well.

## 1.11.7-1.11.5 - Starreach Ring Texture Iteration

- The `Starreach Ring` received multiple art and polish passes.

## 1.11.4-1.11.1 - Secret Shard Quest Debug And Fix Pass

- The hidden shard quest and its cache behavior were made more robust.
- The `Magic Shard` text was shortened.

## 1.11.0 - Secret Shard Quest And Starreach Ring

- The hidden quest `Whispers of the Shards` was added.
- The `Starreach Ring` was introduced as its reward.

## 1.10.2-1.10.1 - Magic Shard Bonus Daily And Daily Reopen

- `Magic Shards` can now unlock an extra daily on the same day.
- Dailies can be reopened later on the same day instead of being lost immediately.

## 1.10.0 - Questmaster NPC Replaces Quest Block

- The old quest block was replaced by the summonable `Questmaster`.

## 1.9.15-1.9.14 - Magic Shard Introduction

- The `Magic Shard` was introduced.

## 1.9.13-1.9.10 - Release Cleanup And Command Pruning

- Standard daily rewards were streamlined toward wallet currency and XP.
- Old release leftovers and unnecessary commands were cleaned up.

## 1.9.9-1.9.1 - Pilgrim Economy And Presentation Polish

- The `Pilgrim` received departure and respawn timers plus several presentation improvements.

## 1.9.0 - Painting Size Rebalance And Two-Currency Overhaul

- The wallet economy was rebuilt around `Silvermark` and `Crown`.
- Pilgrim paintings and prices were rebalanced.

## 1.8.2-1.8.0 - Painting Expansion And Currency Naming Cleanup

- More Pilgrim paintings were added.
- Sizes, prices, and currency naming were cleaned up.

## 1.7.8-1.7.1 - Pilgrim UI And Trade Polish

- The Pilgrim trade screen received several layout and readability improvements.

## 1.7.0 - Pilgrim Trader

- The traveling `Pilgrim` merchant was introduced.

## 1.6.2-1.6.0 - Digital Wallet And Journal Basics

- Coin items were replaced by the digital wallet.
- Wallet display and early journal basics were added.

## 1.5.10-1.5.8 - Wolkensprung Split

- `Wolkensprung` was split out of `Village Quest` into its own separate mod project.

## 1.5.7-1.5.1 - Daily Admin, Balance, And Selection Polish

- Daily testing tools, balancing, and rotation logic were improved.

## 1.5.0 - Daily Wave 2

- Five more action-based dailies were added:
  - `River Meal`
  - `Autumn Harvest`
  - `Smith Smelting`
  - `Stall Breeding`
  - `Village Trading`

## 1.4.4-1.4.1 - Project Continuity And Cleanup

- Project continuity helpers and early cleanup work were introduced.

## 1.4.0 - Daily Wave 1

- The first action-based dailies entered the game.

## 1.3.1-1.3.0 - Daily Refactor And Reset Command

- The daily system was restructured.

## 1.2.1-1.2.0 - Compatibility And Localization

- Early compatibility and localization work was added.

## 1.1.2-1.1.0 - Early Daily-State And Peace-Armor Cleanup

- Early quest-state and text cleanup was implemented.

## 1.0.5-1.0.1 - Early 1.21.11 Compatibility Fixes

- Early compatibility issues from the first `1.21.11` line were fixed.

## 1.0.0 - Baseline

- Baseline release on Minecraft `1.21.11`.
