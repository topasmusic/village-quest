# Changelog

## 1.20.3 - Minecraft 26.1.1 Hotfix Update

Release date: 2026-04-03

- Village Quest has been updated for Minecraft `26.1.1`.
- The Fabric stack was refreshed to the latest stable loader and Fabric API builds for `26.1.1`.
- No gameplay or content changes were made in this hotfix release.

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
