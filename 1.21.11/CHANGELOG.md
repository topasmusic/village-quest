# Changelog

## 1.21.1 - First-Use Journal Onboarding

Release date: 2026-04-10

- The inventory journal tab now shows a one-time first-use hint with a small arrow in the inventory until it has been clicked once.
- After opening the journal, the `Questmaster` button on the first page now also gets its own one-time guided highlight.

## 1.21.0 - Pilgrim Shop Expansion And Story Cooldown Update

Release date: 2026-04-09

- The pilgrim wallet header now expands its coin spacing so multi-digit `Crown` and `Silvermark` counts no longer overlap the icons.
- Pilgrim shop prices were raised sharply across the full catalog with a central `3x` pricing pass.
- Story wallet rewards were reduced to `70%` of their former values so main-story completion no longer funds the shop as quickly by itself.
- Player and admin commands now also work under the new roots `/villagequest ...` and `/vq ...`, and the journal button, journal help pages, clickable quest actions, tracker hints, README notes, and command docs were updated to that structure.
- The legacy direct roots such as `/questadmin`, `/questmaster`, `/dailyquest`, `/wallet`, `/reputation`, `/journal`, and `/questtracker` still remain on this legacy line as compatibility aliases.
- The nested `/villagequest ...` and `/vq ...` child commands now register as real subcommands again on `1.21.11`, so journal toggles and other UI-driven command calls no longer fall through broken redirect paths.
- Pilgrim shop payloads no longer disconnect the client on `1.21.11` when a custom head offer is present; long decor-head profile names are now shortened to packet-safe values before encoding.
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
- Pilgrim offer locking now blocks both reputation-gated and village-project-gated wares correctly, including direct-buy paths.

## 1.20.4 - Quest Balance And Systems Update

Release date: 2026-04-03

- Daily, weekly, story, and special quests received a broad balance pass across the full `1.21.11` content set.
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

## 1.20.3 - 1.21.11 Line Sync

Release date: 2026-04-03

- Release numbering was aligned with the parallel `1.20.3` branch work.
- Minecraft remains `1.21.11` on this line; there is no platform jump here.
- Internal release files and documentation were refreshed so the `1.21.11` branch stays in sync with the newer quest line changes.

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

## 1.20.1 - Legacy Follow-Up Polish

Release date: 2026-03-30

- `Smoke over Blackstone` now shows its real objectives directly instead of the old unclear wording.
- Several gathering, mining, and shearing quests were updated:
  - self-earned progress is tracked more reliably
  - `Fortune` bonus yield now counts properly where it makes sense
  - several hand-in quests now truly require their materials again at turn-in
- Relevant hand-in quests now show a clear red hint when progress is finished but the required turn-in items are no longer in the inventory.
- `Wayfinder's Compass` now asks for `Redstone Dust` from your own mining instead of the old ore-only wording.
- `Whispers of the Shards` now tracks `Amethyst Shards` in the clearer modern form.
- Several quest texts in English and German were cleaned up so progress lines and offers better match the real objectives.

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
