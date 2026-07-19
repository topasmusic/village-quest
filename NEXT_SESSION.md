# Next Session Notes

## Current Release State

As of `2026-07-19`, `Village Quest 1.22.8` remains the public stable release on all three maintained Minecraft lines. The local source tree is prepared as the unreleased `Village Quest 2.0.0 - Roads Between Villages` release candidate.

The `2.0.0` candidate is present on:

- Minecraft `26.2`, Java `25`, Fabric Loader `0.19.3`, Fabric API `0.153.0+26.2`, Mojang names
- Minecraft `26.1.2`, Java `25`, Fabric Loader `0.19.2`, Fabric API `0.146.0+26.1.2`, Mojang names
- Minecraft `1.21.11`, Java `21`, Fabric Loader `0.19.2`, Fabric API `0.141.3+1.21.11`, Yarn `1.21.11+build.4`

All three lines now contain the same intended gameplay and presentation layer. The unified UI, terrain-backed full map and minimap, caravan reliability, inhabited-village validation, route-owned outfits, pause/removal cleanup, destination renaming, German umlaut normalization, deterministic QA helpers, and four-offer Pilgrim cap were deliberately ported from the `26.2` reference to `26.1.2` and `1.21.11` with target-appropriate APIs.

The `2.0.0` source candidate is intentionally kept in version control, but it remains untagged and unpublished. Every changelog uses `Release date: pending` until the actual publishing turn.

The candidate now uses one mixed-license package on all three lines. Functional code is `LGPL-3.0-only`; original Village Quest assets and creative content remain All Rights Reserved under the limited-use project notice; earlier MIT releases keep their granted permissions. The complete license and notice set is embedded in runtime and sources JARs. The legacy NPC and caravan skins listed with unknown provenance in `THIRD_PARTY_ASSETS.md` remain a release gate until replaced or cleared.

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

- `python tools/validate_resources.py` passes with `1701` matching localization keys across all three lines.
- All three `2.0.0` Gradle projects build successfully with their required Java versions.
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

- Public stable remains `1.22.8` until the actual release is published.
- Candidate version is `2.0.0`; expected tags are `v2.0.0-mc26.2`, `v2.0.0-mc26.1.2`, and `v2.0.0-mc1.21.11`.
- Expected primary artifacts are `village-quest-2.0.0-mc<target>.jar`, with matching sources jars.
- Release title: `Village Quest 2.0 - Roads Between Villages`.
- Keep GitHub release notes short and player-facing; use `RELEASE_NOTES_2.0.0.md` as the prepared source.
- Replace `Release date: pending` in all three changelogs with the real publication date immediately before the release commit.
- Do not commit, push, tag, or publish until the user explicitly requests that action.
- Do not publish while an entity skin remains marked unresolved in `THIRD_PARTY_ASSETS.md`.

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

- On explicit release instruction: first resolve the entity-skin provenance gate, insert the real release date, run the final three builds/resource validator, review the staged file list, commit any final release-only metadata, tag, push, and publish the three jars using the repository's local push rules.
- If the user later wants the deferred checks, run the short native `26.1.2` smoke pass and/or the no-admin-completion `The Empty Caravan` playthrough.
- For future caravan regressions, start from the completed `26.2` reference pass and the focused `1.21.11` target regression pass.
