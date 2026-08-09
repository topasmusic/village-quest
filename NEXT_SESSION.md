# Next Session Notes

## 2026-08-09 Village Quest 2.1.1 Release

- By explicit maintainer exception, `2.1.1 - Homesteads & Wayfinding` was ported and released with feature parity on `26.2`, `26.1.2`, and Yarn `1.21.11`. Future feature development returns to `26.2` only.
- Player-built bases can be confirmed as a `Homestead Trade Post` immediately after Market Charter route access, on safe Overworld ground, with no installed routes. Destinations remain real inhabited vanilla/CTOV villages; the later Caravan Yard still expands capacity from one route to five and enables incidents.
- Surveyed ocean waypoints create virtual ferry legs only in biomes belonging to `#minecraft:is_ocean`. Lakes/rivers are not ferry-compatible. Revision `.9` turns the final safe land node into a boarding anchor: an observed group holds persistent progress at the dock, reaches it, gathers nearby followers, and then transfers into the crossing; unobserved/unloaded travel remains immediate and never loads chunks. Both maps show the dock/sea boat state, dashed blue lanes, and remaining crossing time.
- Terrain sampling is aligned to world coordinates and persisted in recoverable world/server, dimension, and quality-separated `.minecraft/village-quest/map-cache` tiles with retention and size limits.
- The full-map viewport now keeps an exact width/height while panning, and its sampled cells, slope shading, shorelines, palette texture, and glyph placement are all world-anchored. A drag must translate the existing illustrated terrain rather than subtly rebuilding its pixel pattern.
- `.minecraft/config/village-quest/server.properties` owns reset timezone/day/hour, Homestead permission, and `FULL`/`REDUCED`/`MAP_ONLY` caravans. `client.properties` owns HUD/minimap layout, layers, notifications, sounds, cache quality/limits, and tutorial hints.
- `.` toggles the saved Quest Tracker, `,` toggles the minimap, and both appear under `Village Quest` in Controls.
- All eight active special items now share bespoke project art. The 32-frame compass keeps a fixed brass bezel and rotates only its inset cardinal dial; source masters, extracted layers, builder, and previews are retained under `26.2/design/2.1.1-item-art/`.
- The accompanying cleanup removes only verified duplicate/unreachable resources. Do not remove compatibility item registrations, save-facing IDs, plaque rewards, Peace Armor recipes, or unresolved entity skins as part of this pass.
- `/vq diagnose [player]` is a read-only support report. It must remain non-repairing and non-mutating.
- Four isolated tests cover Daily, Weekly, non-European timezone, and DST boundaries. Existing quest turn-in and timer state machines were deliberately not rewritten for this patch.
- Stable target identity is `2.1.1`. Revision `.7` contains the riding/Bakehouse/Trust/Prosperity polish; `.8` adds early Homestead Trade Posts and ocean-only ferry travel with map presentation; `.9` adds safe observed dock boarding, unsafe-shore validation, and two ferry-boundary regression tests. Full Gradle tests and builds pass on all three targets, with target-appropriate Mojang or Yarn APIs.

## 2026-07-29 Village Quest 2.1.0 Release

- `Village Quest 2.1.0 - Prosperity & Prestige` is the public stable release for Minecraft `26.2`, `26.1.2`, and `1.21.11`.
- Release tags are `v2.1.0-mc26.2`, `v2.1.0-mc26.1.2`, and `v2.1.0-mc1.21.11`.
- Minecraft `26.2` is now the only active content-development line. Older targets remain downloadable maintenance releases under `VERSION_SUPPORT.md`.
- Stable runtime SHA-256 values: `26.2` `4868A3665543FE0ED6468B297CB2571249901EB8A422800CD19F3F3842AA7A01`, `26.1.2` `42FD13EA1240D92C22840034F3CA32E756B8D9B4FAA45930DA59B3C12AEB6586`, and `1.21.11` `CE81C5A7E9EED0A2C27F5D0187095EE343C70ED1903A50F25860AB40CF602181`.

## 2026-07-29 Prosperity Wallet And Journal Return Navigation

- Prosperity now uses the exact accepted Journal wallet coordinates on every maintained line: `40` UI pixels from the right and `10` from the top of the responsive board.
- This replaces Prosperity's former `24 / 7` placement, moving the Crown/Silvermark icons and amounts left and down into the center of the wooden header and away from the brass corner.
- Prosperity's `Done` button and the full map's regular close action return to the Journal on all three lines. Starting a route survey still closes directly into gameplay.
- The final candidate revisions were `2.1.0-unreleased.16` on `26.2` and `2.1.0-unreleased.9` on Mojang `26.1.2` and Yarn `1.21.11`.
- All three full Gradle builds pass and the shared validator confirms `1841` localization keys. Embedded versions and compiled screens were checked; each `build/libs` retains only Stable `2.0.1` plus the latest Unreleased pair. Runtime SHA-256: `26.2` `0E8B71798AADF2EA5F8E6F250C32825B08AE6FBC97C5C104482CE162A9FD77A6`, `26.1.2` `EDB66B859CF513187E338DB4265FD3AF3E4AA9262B719398B2850EB0F088FCAD`, `1.21.11` `212C9623A245B10C6CF8F531D6558FFAC48A23118F22774219CB5A2E7BBAD210`. No native client session was run for this focused presentation and navigation change.

## 2026-07-29 Version-Support Decision

- `2.1.0 - Prosperity & Prestige` remains the final planned feature-parity release for `26.2`, `26.1.2`, and Yarn `1.21.11`; until it is published on all three targets, the current parity workflow still applies.
- After that release, `26.2` is the sole active content-development line. Older `2.1.x` builds remain downloadable but receive no routine quest, system, item, UI, balance, compatibility, or ordinary bug backports.
- A critical old-line hotfix is discretionary and requires an explicit maintainer decision for a startup crash, save/persistent-data corruption, or a severe exploit.
- When a later stable Minecraft version is adopted, it replaces `26.2` as the one active line instead of creating another permanently maintained branch.
- The public and maintainer-facing policy is recorded in `VERSION_SUPPORT.md`, linked from the root `README.md`, and mirrored in `CODEX_START_HERE.md`.

## 2026-07-29 Journal Reliability And Restored First-Use Pointer

- The inventory Journal controls on all three maintained lines now send an idempotent `/vq journal open`; normal Journal closing sends `/vq journal close`, and navigation to Routes, Prosperity, or the Questmaster clears the server-side Journal state before opening its destination. The existing `/vq journal` command remains a backward-compatible toggle.
- The one-time animated inventory pointer is restored and versioned. Fresh players and existing installations upgrading from the old seen flag receive one guided Journal click; the hint persists until clicked and then saves its current tutorial version.
- The pointer targets the normal right-side bookmark, the top-right status-effect-safe placement, or the compact `J` widget used when an inventory-screen compatibility rule disables the overlay. MTGCard therefore keeps its established fallback; Bendable Cuboids remains on the separate safe NPC renderer path and is unaffected.
- Current candidates are `2.1.0-unreleased.15` on `26.2` and `2.1.0-unreleased.8` on Mojang `26.1.2` and Yarn `1.21.11`.
- The shared validator confirms `1841` matching localization keys, and all three full Gradle builds pass. Embedded versions and compiled runtime classes were verified; each `build/libs` contains only the published `2.0.1` pair plus the newest Unreleased pair. Runtime SHA-256: `26.2` `6E41B7ED9E59C43867BBDC7E875D010FFCE68129AE30DD3DC77D178586CB9FB9`, `26.1.2` `09310709122CD1C287261D63AEBBAA3BC3EDAE23E55AEEB5EBCD5F142701D474`, and `1.21.11` `01E89A357D950CBEB1735118E27D2CFB4A68A9A19CA15ADD4A9A386541107A6A`. No native client session was run.

## 2026-07-29 Three-Line Quest-Polish Parity

- The complete focused quest-polish pass is now implemented on all maintained lines with target-appropriate APIs: locked-entry requirements and scoped timers, six-stage `The Master's Edge`, six other dependency-based production chains, centralized Daily/Weekly/Story progress sounds, the selectable zero-badge Story cooldown timer, and persistent one-time Questmaster availability notices.
- Completed unlock requirements and completed numeric tracker objectives use a muted dark green instead of the former neon green; missing requirements remain red.
- Those quest-polish parity candidates were `2.1.0-unreleased.14` on `26.2` and `2.1.0-unreleased.7` on `26.1.2` and Yarn `1.21.11`; the Journal reliability candidates above supersede them.
- All three lines validate against the same `1841` localization keys and pass their full Gradle builds. Runtime SHA-256 values: `26.2` `EE2ACB6176D6A644ADC3AD9F362142318F723D98FC070449D6D8B1711AC3C4ED`; `26.1.2` `DC18C2D862122FEB8E5E8E872A29847BBB0B37A58EFB4EEC4E6280C5C71C5D8F`; `1.21.11` `DB80F1FC1428C45075F3181F631AF377EC8896AB26DF017F374AE9BAF474AB94`. Each `build/libs` retains only the Stable `2.0.1` pair and its newest numbered Unreleased pair. No native client session was requested or run for this port.

## 2026-07-29 Quest Feedback And Availability

- Revision `2.1.0-unreleased.13` on `26.2`, now ported in target revision `.7`, centralizes low-volume feedback for quest acceptance, normal progress, completed objectives, and staged transitions across Daily, Weekly, and Story content. Final claims retain the existing level-up reward sound.
- The Story cooldown placeholder no longer creates a false `1`; its zero-badge tab remains selectable and now shows the live remaining cooldown.
- Persistent per-player offer tokens announce each newly available Daily, Weekly, or Story assignment once in localized Questmaster chat, survive restarts, avoid tick/login spam, and refresh an open Quest Board.
- Full build and focused resource validation pass with `1841` localization keys. `build/libs` contains the published `2.0.1` pair plus the `.13` pair; runtime SHA-256 is `F4BA7CFCC427DED85B0394597EB0C8CAA38F006925AF2F4C17850B98553FF114`.
- The original pass was limited to `26.2`; the parity section above supersedes that limitation. No native client session was run.

## 2026-07-29 German Localization And Staged Quest Chains

- German localization was audited on all three maintained lines. Leftover Swiss-style `ss` spellings and malformed earlier substitutions such as `Baün`, `Neü`, and `qüstmaster` were corrected without changing translation keys.
- Focused `26.2` revision `2.1.0-unreleased.11` rebuilds `The Silent Forge: The Master's Edge` as six sequential tracker stages: five distinct villager enchanted books, four freshly crafted Iron Armor pieces, four protected armor pieces, a freshly crafted Diamond Sword, Sharpness on that sword, and the final five-piece inventory hand-in.
- Villager enchanted-book purchases now read Minecraft's stored-enchantment component, so any level including Sharpness V counts. The four protection types work at any level on any of the four Iron Armor slots.
- Existing active Chapter 4 saves receive a one-time recovery scan for qualifying books still carried after the old tracker missed their purchase. Newly accepted chapters retain strict post-acceptance villager-purchase semantics.
- Focused `26.2` revision `2.1.0-unreleased.12` extends staged progression to every other genuine dependency chain: `Bakehouse Help`, `Meal from the River`, `Smelting for the Smithy`, `Harvest for the Village`, `Smith's Week`, and `Ledger And Notices`. Processing before its harvesting/mining/crafting prerequisite no longer counts, the tracker shows only the active stage, and the final bundle remains a Questmaster hand-in. Independent bundles remain parallel.
- These were the pre-port candidates. The parity section above supersedes them with `.14 / .7 / .7` and `1841` keys on every line.

## 2026-07-29 Quest Wording And Wiki Audit

- All visible Daily, Weekly, Story, and Special objectives were checked against their live progress and turn-in code on all three maintained lines.
- Quest text in English, German, and Spanish now distinguishes actions performed after acceptance, pure supply deliveries that may use stored or traded goods, and hybrid chapters that require both fresh progress and a carried bundle.
- The wiki documents that Minecraft merges identical stacks: fresh work is persisted as quest progress, while the final carried bundle is checked separately and consumed atomically.
- Old authored-target drift was corrected in quest copy and wiki, including Silent Forge, Market Road Troubles, Restless Pens, legacy Night Bells, and the four relic commissions. Code balance values were not changed.
- Those audit builds were `2.1.0-unreleased.10` on `26.2` and `2.1.0-unreleased.5` on both ports; they are now superseded by the candidates documented above.
- All three builds succeeded. Per-line resource validation passes with `1813` keys on intentionally divergent `26.2` and `1810` keys on each port. No native client session was run for this text/wiki audit.

## 2026-07-29 Locked Quest Requirements And Tab Timers

- Focused `26.2` revision `2.1.0-unreleased.9` renders locked-entry objectives/reasons as an `Unlock requirements` section before the normal description. Relic reputation locks show a green completed Story prerequisite and red live Reputation progress; generic locked Story/Special reasons use the same section.
- A Story chapter whose accept precondition fails now carries the locked flag so its blocking lines use the requirement presentation consistently.
- Quest Board footer timers are category-scoped: the Daily reset appears only in Daily and the Weekly reset only in Weekly. Special/Relic no longer inherits a completed Daily countdown.
- This limitation was historical; target revision `.7` now carries the same UI behavior on both ports.

## 2026-07-29 Modrinth Test-Profile Runtime Correction

- The `Fabric 26.2` test profile accidentally contained `village-quest-2.1.0-unreleased.8-mc26.2-sources.jar` instead of the runtime JAR. Fabric therefore discovered the included `fabric.mod.json` but could not load compiled mixins, producing `ClassNotFoundException` for `AbstractFurnaceScreenHandlerMixin`.
- The Sources JAR was removed and replaced by the verified runtime `village-quest-2.1.0-unreleased.8-mc26.2.jar`; that installed JAR contains `de/quest/mixin/AbstractFurnaceScreenHandlerMixin.class`.
- Future profile deployment must reject filenames ending in `-sources.jar` and verify a representative compiled `de/quest/*.class` entry before launch.

## 2026-07-29 Exact Ore Progress And Completed Objectives

- The focused `26.2` revision `2.1.0-unreleased.8` captures the exact generated stack for every tracked block resource, not only Wheat/Potato/Carrot. This removes Fortune-sensitive discrepancies between tracked Coal/Raw Iron/Redstone/Raw Gold/Diamond/Lapis/Amethyst progress and the items that actually enter the world.
- If `Block.popResource` is bypassed, the pending item-entity association remains active for five ticks as the compatibility fallback. Pre-acceptance inventory still does not satisfy explicit newly-gathered progress; the separate turn-in check continues to validate current carried stock.
- Numeric tracker lines turn green only when every `current/target` pair on that line is complete.
- The behavior is ported to Mojang `26.1.2` and Yarn `1.21.11` in target revision `.4`; all three maintained lines now share the exact tracked-resource and green completed-objective behavior.

## 2026-07-29 Completed Weekly Navigation

- Quest Board badge counts and category availability are separate on all three lines. Completed or locked entries still contribute `0` to the badge, but their category remains selectable whenever an entry payload exists.
- This fixes the regression where completing a Weekly made its tab look locked and prevented the player from reopening the completed entry. Selecting Weekly now exposes the existing Monday-reset countdown again.
- The completed-Weekly fix first shipped in `.9`/`.4`; the current candidates are listed in the newer quest-wording section above.

## 2026-07-28 Compact Missing Turn-in Warning

- `26.2` revision `2.1.0-unreleased.5` filters central `Texts.turnInMissing` output to requirements whose inventory count is still below target. Only those item labels and counts are explicitly red; fulfilled Wheat, Carrot, Bread, and other bundle entries disappear.
- `QuestTrackerHud` converts titles and lines into bounded wrapped text before sizing the panel. Its content width is capped at `220` GUI pixels, so long localized or multi-item warnings wrap downward instead of stretching across the screen.
- Revision `.6` preserves an explicit component root color when those wrapped display strings are created, fixing the `.5` regression that rendered the otherwise-correct missing-only message in the normal quest-section color.
- A new five-requirement helper overload brings Silent Forge's largest bundle onto the same filtered path. The behavior is ported to both maintained target lines in their revision `.2`.

## 2026-07-28 26.2 Crop Drop Lifecycle Hotfix

- `26.2` revision `2.1.0-unreleased.4` replaces the unreliable post-spawn crop association used by `.2` and `.3`.
- Root cause: even with longer-lived sources and tick/pickup fallback, normal Potato drops were not reliably associated with their `ItemEntity` in the native client. `.4` hooks Minecraft's actual `Block.popResource` call and dispatches the exact already-randomized Wheat/Potato/Carrot stack before the item entity exists. The block source is then retired at `AFTER`, preventing pickup double credit; the entity path remains as a compatibility fallback.
- After maintainer validation, the lifecycle fix was ported to Mojang `26.1.2` through `Block.popResource` and Yarn `1.21.11` through `Block.dropStack`; both target lines use revision `.2`.

## 2026-07-28 Numbered Unreleased Builds

- All three lines require `unreleased_revision` whenever `build_channel=unreleased`. `26.2` currently uses revision `12`; `26.1.2` and `1.21.11` use revision `6`.
- Increment the revision once before handing off a source state that differs from the last handed-off Unreleased build. Rebuilding identical source keeps the same revision; Stable releases ignore it and remain plain `2.1.0`.
- `build/libs` keeps exactly the published Stable runtime/source pair plus the latest numbered Unreleased runtime/source pair. Superseded unnumbered or lower-revision Unreleased artifacts are removed only after the new pair builds successfully.

## 2026-07-28 Global Crop-Yield Audit

- All active Wheat/Potato/Carrot quantity quests now share the tracked mature-crop item-yield path on `26.2`, `26.1.2`, and Yarn `1.21.11`: `Kitchen Supplies`, `Bakehouse Help`, `Harvest for the Village`, and `The Failing Harvest: Thin Fields`.
- Normal harvests credit the actual collected item-entity count, so one mature Potato or Carrot plant can add its complete multi-item drop. Verified right-click-and-replant harvests use the existing compatibility path with Carrots added.
- Immature crops do not enter the crop-yield tracker, and the old per-block callbacks were removed from the quantity quests, preventing block-plus-item double credit. `Autumn Harvest` intentionally remains one objective point per attached Pumpkin/Melon fruit block.

## 2026-07-28 Artifact Separation And Wallet Alignment

- All three development lines use `build_channel=unreleased` with a positive revision. The current focused `26.2` candidate is revision `9`; both target ports are revision `4`.
- The last published `2.0.1` runtime and sources JARs must remain in each line's `build/libs` directory beside the current Unreleased pair. If `clean` removes them, restore the exact assets from the matching GitHub release; never rebuild or relabel current source as an older Stable.
- The Journal wallet moves `16` UI pixels left and `3` down. The Pilgrim wallet moves `8` left and `2` up while its separate `Rumor` header control retains its accepted vertical position. The same coordinates are ported to all three lines.
- Revision `.8` builds successfully on `26.2` and its focused validator confirms all `1810` keys. The earlier Mojang `26.1.2` and Yarn `1.21.11` revision `.3` ports remain full-build/remap and strict three-line validation verified.
- `26.2/build/libs` retains exactly the `2.0.1` Stable pair plus the `.9` Unreleased pair. Runtime SHA-256: `0ACC66DE36215D838324E8777ECB255ABE60B5AB698590F9EA48E0471D4DB4BB`.
- `26.1.2/build/libs` and `1.21.11/build/libs` each retain exactly the `2.0.1` Stable pair plus their `.4` pair. Runtime SHA-256 values: `E9E56AB2D11DF1710EB0CDB7C90BDE7160F2985BA1793E24E366C1FC49E4F095` (`26.1.2`) and `6F675E47956AF1B6B75575C72EF29E642955E94A9D5BBB6513F30C9DC7E3BA70` (`1.21.11`).

## 2026-07-27 Quest Turn-in Stability Pass

All three maintained lines now share the inventory-backed quest reliability fixes from the `2.1.0` bug report:

- `The Failing Harvest` chapter 1 tracks actual Wheat/Potato item yields and requires/consumes `16 Wheat` plus `8 Potatoes`.
- Its chapter 2 requires/consumes `3 Honey Bottles` plus `1 Honeycomb`.
- Straightforward multi-item Story, Daily, and Weekly turn-ins prevalidate the full bundle before removing anything; predicate-based wool/tool/equipment handlers retain their existing full prechecks.
- Completion is now explicit and consistent: pure action Daily/Weekly quests auto-complete, while item-consuming Daily/Weekly quests, all Story chapters, and Special commissions wait for a Questmaster claim. Progress events never remove delivery items.
- Completed/locked Quest Board entries no longer inflate category badges, and the Weekly reset label is positioned above the lower frame.
- The Prosperity screen shows the localized selected-rank Crown price directly above `Invest` on all three maintained lines.

Full `gradlew build` passes for `26.2`, `26.1.2`, and Yarn `1.21.11`; the strict validator reports `1810` matching localization keys, and a targeted audit confirms all eight item-consuming repeatables plus the Daily, Weekly, Story, and four Special Questmaster claim routes on every line. No native client session was run for this focused fix.

## Current Release State

As of `2026-07-29`, `Village Quest 2.1.0 - Prosperity & Prestige` is the public stable release on all three maintained Minecraft lines.

All three maintained lines share the released `2.1.0 - Prosperity & Prestige` feature set. It contains five three-rank project investment tracks, permanent economic benefits, Pilgrim commissions, five paid services, ten prestige collection rewards, selectable route liveries shared by NPCs and both maps, lifetime economy statistics, a modular five-tab board, and `/vq admin economy testsetup`. Its `Market Week` guard measures whether the player can afford the three cheapest unlocked Pilgrim purchases; invalid unaccepted offers migrate, while accepted Weeklies remain stable.

The latest UI polish standardizes the Journal, Pilgrim, and Prosperity wallet as Crown/Silvermark icons in the upper-right wood header; centers Prosperity and Collection icons; moves the Pilgrim Rumor control into the upper-left header; clears the Journal/Prosperity Done buttons from the brass corner; and replaces every shared scroll thumb with a generated medieval double-arrow ornament on all three lines.

All three development lines now share fullscreen-safe responsive board sizing, including Prosperity. The Journal, Quest Board, Pilgrim Trader, Prosperity, and Caravan Ledger/Route Office remain centered at a consistent footprint across a maximized window and fullscreen. Hover, click, scrolling, and map-drag coordinates follow the transform. Terrain-map texture cleanup on disconnect is scheduled back onto the render thread on every line.

The `2.1.0` release is present on:

- Minecraft `26.2`, Java `25`, Fabric Loader `0.19.3`, Fabric API `0.153.0+26.2`, Mojang names
- Minecraft `26.1.2`, Java `25`, Fabric Loader `0.19.2`, Fabric API `0.146.0+26.1.2`, Mojang names
- Minecraft `1.21.11`, Java `21`, Fabric Loader `0.19.2`, Fabric API `0.141.3+1.21.11`, Yarn `1.21.11+build.4`

All three lines now contain the same intended gameplay and presentation layer. The unified UI, terrain-backed full map and minimap, caravan reliability, inhabited-village validation, route-owned outfits, pause/removal cleanup, destination renaming, German umlaut normalization, deterministic QA helpers, and four-offer Pilgrim cap were deliberately ported from the `26.2` reference to `26.1.2` and `1.21.11` with target-appropriate APIs.

The release uses the tags `v2.1.0-mc1.21.11`, `v2.1.0-mc26.1.2`, and `v2.1.0-mc26.2`. All three changelogs record the publication date `2026-07-29`.

The release uses one mixed-license package on all three lines. Functional code is `LGPL-3.0-only`; original Village Quest assets and creative content remain All Rights Reserved under the limited-use project notice; earlier MIT releases keep their granted permissions. The complete license and notice set is embedded in runtime and sources JARs. The maintainer explicitly authorized publication with the legacy NPC and caravan skins still listed as unresolved in `THIRD_PARTY_ASSETS.md`; they are excluded from ownership/license claims and remain priority replacement or clearance work.

## Village Quest 2.0 Scope

- `The Empty Caravan`, a six-chapter follow-up to `Shadows on the Trade Road`
- permanent `Caravan Yard` and `Caravan Ledger` progression
- up to five persistent village-to-village routes per player
- persistent surveys with up to `48` waypoint anchors for real player-built detours
- nearby physical three-merchant caravans plus unloaded-chunk background simulation
- route-quality, lighting, distance, security, earnings, daily caps, and offline escrow
- eight recurring route incidents and automatic Wayfinder targets
- five-rank Village Trade Guild, daily freight contracts, six specializations, and six investments
- route-owned burgundy, forest, blue, ochre, and violet caravan outfits matching the maps
- cached terrain-backed full route map with left-button dragging, zoom, tooltips, player/caravan markers, and guarded removal
- configurable live terrain minimap through the default `,` key or `/vq routes minimap`
- inhabited vanilla/CTOV village validation that rejects abandoned and zombie villages
- compact unified Journal, Questmaster, Pilgrim, Ledger, and Route Office presentation
- Pilgrim stock capped at four offers, including old-save trimming
- bounded level-bar experience rewards, Mastery, rerolls, relic improvements, and broad economy/reward retuning
- `Roadwarden Horn` and one-time old-save backfills for qualifying missing Ledger/Horn items
- mature-crop compatibility for normal breaking and right-click-and-replant mods

## Verified State

- The released `2.1.0` code, language set, and Prosperity UI assets are present on all three maintained lines. Full `gradlew build` passes for `26.2`, `26.1.2`, and Yarn `1.21.11`; the strict validator reports `1841` matching English, German, and Spanish localization keys across all three lines.
- A native `26.2` fullscreen/maximized comparison on `2026-07-22` verified the responsive Journal, Questmaster, Pilgrim, route map, and Prosperity screens. Transformed category/button hitboxes, hover tooltips, route-map panning, and Prosperity tabs all worked. A disconnect-only wrong-thread map-texture warning found during shutdown was fixed on all three lines.
- The earlier published `2.0.1` baseline used `1701` matching keys. The released `2.1.0` set is shared across all three lines.
- All three release projects build successfully with their required Java versions after the responsive-screen and disconnect-cleanup ports.
- `git diff --check` passes; line-ending messages are informational Windows warnings.
- `26.2` remains the native visual/gameplay reference. Its client passes covered the unified full-screen interfaces, map/minimap, five-route lifecycle, measurable caravan movement, obstruction recovery, inhabited/depopulated village validation, pause cleanup, reload without duplication, representative route incidents, Guild/contracts, route upgrades, Roadwarden Horn, rerolls, Mastery, low/high-level XP, and persistence.
- The vanilla/RightClickHarvest comparison passed on `26.2`, including bone-meal-held right-click harvesting without false credit or double counting.
- The native `1.21.11` pass covered the five-route UI, survey/install, both removal paths, Wayfinder, Broken Wheel consumption, automatic caravan materialization, physical position updates, pause cleanup, resume respawn, and the unified interfaces.
- A real `1.21.11` port regression was fixed on `2026-07-19`: route merchants and story/incident attackers now refresh their collision bounds before strict space validation. A dry artificial-grass reproduction proved water was not the cause.

## Explicitly Deferred By The User

These two checks are intentionally not release blockers for the prepared candidate unless the user later reopens them:

- native `26.1.2` client smoke test
- full `The Empty Caravan` playthrough without admin chapter completion

`26.1.2` is compiler/build/resource verified and uses the same newer Mojang-named spawn behavior already exercised on `26.2`; it has not received a separate native client pass for this candidate.

## Release Handling

- Public stable is `2.1.0` on all three maintained lines.
- Release tags are `v2.1.0-mc26.2`, `v2.1.0-mc26.1.2`, and `v2.1.0-mc1.21.11`.
- Primary artifacts are `village-quest-2.1.0-mc<target>.jar`, with matching sources jars.
- Release title: `Village Quest 2.1.0 - Prosperity & Prestige`.
- Keep GitHub release notes short and player-facing; `RELEASE_NOTES_2.1.0.md` is the publication source.
- The `2.1.0` release was explicitly requested by the maintainer and published on `2026-07-29`.
- Keep the unresolved entity-skin warnings intact until original replacements or valid source permissions are available.

## Workflow Rules

- Git operations happen at the repository root.
- `26.2` is the default implementation/reference line.
- Never blindly copy Mojang-named code to Yarn `1.21.11`; port behavior and preserve API differences.
- Read the matching `MEMORY.md`, `CHANGELOG.md`, README, and relevant wiki before version-specific changes.
- If work touches `Shadows on the Trade Road`, also read `WATCH_BELL_EXPANSION_PLAN.md`.
- `/vq admin routes testsetup [player]` is the central five-route QA fixture.
- The onboarding state is stored in `run/config/village-quest-client.properties` per line.
- Party UI remains intentionally hidden in singleplayer/integrated worlds.

## Next Sensible Work

- For future releases, retain the build/resource audit, staged-file review, human-facing notes, sources JARs, and per-Minecraft-line tags used for `2.1.0`.
- If the user later wants the deferred checks, run the short native `26.1.2` smoke pass and/or the no-admin-completion `The Empty Caravan` playthrough.
- For future caravan regressions, start from the completed `26.2` reference pass and the focused `1.21.11` target regression pass.
