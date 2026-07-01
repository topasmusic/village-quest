# Next Session Notes

## Current Release State

As of `2026-07-01`, `Village Quest 1.22.7` is now shipped on all three maintained lines, with `26.2` as the active default work line.

Current stable tags:

- `v1.22.7-mc1.21.11`
- `v1.22.7-mc26.1.2`
- `v1.22.7-mc26.2`
- `v1.22.6-mc1.21.11`
- `v1.22.6-mc26.1.2`
- `v1.22.6-mc26.2`
- `v1.22.5-mc1.21.11`
- `v1.22.5-mc26.1.2`

The carried-forward modern baseline includes:

- Fabric Loader `0.19.3` on `26.2`
- Fabric API `0.153.0+26.2` on `26.2`
- the late `Questmaster` story arc `Shadows on the Trade Road`
- the global reset command `/vq admin reset complete`
- late-road admin test helpers under `/vq admin story shadows ...`
- contextual villager dialogue for talk-based objectives
- the accepted `Wayfinder's Compass` art pass
- `Questmaster` and `Pilgrim` inventory-key close support
- the shipped multiplayer quest-party feature set
- the `1.22.6` quest-tracking fixes for collar recolors, hive harvests, sheep shearing, and `The Failing Harvest` targets
- the `1.22.7` shard-bonus fix for action-based `Daily` quests like `Autumn Harvest`
- the larger `Questmaster` description hover preview on all three maintained lines

## Version-Line Differences

`26.2`:

- active default line
- Java `25`
- Minecraft `26.2`
- Fabric Loader `0.19.3`
- Fabric API `0.153.0+26.2`
- official Mojang-name environment
- documented command roots are only `/vq ...` and `/villagequest ...`

`26.1.2`:

- last shipped modern reference line
- Java `25`
- Minecraft `26.1.2`
- Fabric Loader `0.19.2`
- Fabric API `0.146.0+26.1.2`
- official Mojang-name environment

`1.21.11`:

- legacy/backport line
- Java `21`
- Fabric Loader `0.19.2`
- Fabric API `0.141.3+1.21.11`
- Yarn `1.21.11+build.4`

## Workflow Rules

- Git operations happen at the root repo:
  - `C:\Users\me\Desktop\Topas Mods\MC MODS\Topas Mods\Village Quest`
- If the user asks for parity, a backport, or release maintenance, re-read:
  - `CHANGELOG.md`
  - `README.md`
  - `docs/wiki/`
  - `NEXT_SESSION.md`
  - the matching `MEMORY.md`
- if the task touches the late road-defense story, also re-read:
  - `WATCH_BELL_EXPANSION_PLAN.md`
- Do not blindly copy files between `26.2` and `1.21.11`; port behavior deliberately against that line's APIs and mappings.
- `26.2` is Mojang-named and `1.21.11` is Yarn-mapped; treat that as a real implementation difference.
- Use `26.1.2` as the last shipped modern comparison point when the `26.2` port behaves differently.
- Do not launch `runClient` yourself unless the user explicitly asks.
- When handing off tests, always include the correct version-specific `runClient` command.
- Current party UI remains intentionally hidden on singleplayer or integrated worlds.

## Useful Test Reset

- The onboarding state is client-side and persisted in:
  - `run/config/village-quest-client.properties`
- Delete that file in the relevant version folder if the first-use journal hints need to be replayed locally.

## Verified State

- `1.21.11`, `26.1.2`, and `26.2` builds verified on `2026-07-01`
- copied local test world:
  - `26.2/run/saves/New World`

## Next Sensible Work

- validate the `26.2` port in-game against the copied world
- if the next request is about `Shadows on the Trade Road`, expect follow-up tuning, polish, or bugfixes rather than a greenfield implementation
- if the next task touches the new global reset command, keep documenting clearly that it is a Village Quest data reset, not a world or inventory wipe
- if the next task continues the multiplayer quest-party batch, re-read both `26.2/MEMORY.md` and `1.21.11/MEMORY.md` first and preserve the dedicated-server-only rule
