# Next Session Notes

## Latest Shipped State

Published on `2026-04-24` as `Village Quest 1.22.0` on both maintained lines:

- `v1.22.0-mc1.21.11`
- `v1.22.0-mc26.1.2`

This shipped batch includes:

- Fabric Loader `0.19.2` on both maintained lines
- Fabric API `0.146.0+26.1.2` on `26.1.2`
- the late `Questmaster` story arc `Shadows on the Trade Road` on both lines
- the new global reset command `/vq admin reset complete`
- late-road admin test helpers under `/vq admin story shadows ...`
- contextual villager dialogue for talk-based objectives in `Market Rounds`, `Market Road Troubles`, and `Shadows`
- the accepted `Wayfinder's Compass` art pass: chunkier pixel outer ring, readable original inner dial
- `Questmaster` and `Pilgrim` screens closing again through the active inventory keybind
- Wolkensprung fully removed from code, resources, scripts, and templates

## Version-Line Differences

`26.1.2`:

- active default line
- Java `25`
- Fabric Loader `0.19.2`
- Fabric API `0.146.0+26.1.2`
- official Mojang-name environment
- documented command roots are only `/vq ...` and `/villagequest ...`

`1.21.11`:

- legacy/backport line
- Java `21`
- Fabric Loader `0.19.2`
- Fabric API `0.141.3+1.21.11`
- Yarn `1.21.11+build.4`
- documented command roots are only `/vq ...` and `/villagequest ...`

## Workflow Rules

- Git operations happen at the root repo:
  - `C:\Users\me\Desktop\Topas Mods\Village Quest`
- If the user asks for parity, a backport, or release maintenance, re-read:
  - `CHANGELOG.md`
  - `README.md`
  - `docs/wiki/`
  - `NEXT_SESSION.md`
  - the matching `MEMORY.md`
- if the task touches the late road-defense story, also re-read:
  - `WATCH_BELL_EXPANSION_PLAN.md`
- Do not blindly copy files between `26.1.2` and `1.21.11`; port behavior deliberately against that line's APIs and mappings.
- `26.1.2` is Mojang-named and `1.21.11` is Yarn-mapped; treat that as a real implementation difference.
- Do not launch `runClient` yourself unless the user explicitly asks.
- When handing off tests, always include the correct version-specific `runClient` command.
- GitHub release bodies should keep the existing style:
  - first line repeats the release title once
  - then use short flat bullets
  - no prose paragraphs
- Important PowerShell release-note lesson:
  - avoid backticks/code formatting inside `gh release ... --notes` text
  - plain text is safer there

## Useful Test Reset

- The onboarding state is client-side and persisted in:
  - `run/config/village-quest-client.properties`
- Delete that file in the relevant version folder if the first-use journal hints need to be replayed locally.

## Next Sensible Work

- New feature work should start from the shipped `1.22.0` baseline.
- Default to `26.1.2` for fresh work; only touch `1.21.11` when the user explicitly wants legacy work or parity.
- If the next request is about `Shadows on the Trade Road`, expect follow-up tuning, polish, or bugfixes rather than a greenfield implementation.
- If the next task touches the new global reset command, keep documenting clearly that it is a Village Quest data reset, not a world or inventory wipe.
