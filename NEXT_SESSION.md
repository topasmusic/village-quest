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

## Latest Public Beta

Published on `2026-05-13` as `Village Quest 1.22.1-beta.3` on both maintained lines:

- `v1.22.1-beta.3-mc1.21.11`
- `v1.22.1-beta.3-mc26.1.2`

This public beta adds:

- multiplayer quest parties on dedicated servers
- shared `Daily`, `Weekly`, core `Story`, and normal `Pilgrim` combat-contract progress
- explicit chat offers for joining already running party quests
- restart-persistent party and shared-session state
- repeatable `light`/`normal`/`heavy` target profiles with matching reward scaling
- the `Questmaster` shortcut on every journal page

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
- Current legacy `1.21.11` multiplayer quest-party work is dedicated-server only; the Questmaster party UI is intentionally hidden in singleplayer/integrated worlds.
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

- New feature work should start from the shipped `1.22.0` baseline plus the current public multiplayer beta on both lines.
- Default to `26.1.2` for fresh work; only touch `1.21.11` when the user explicitly wants legacy work or parity.
- If the next request is about `Shadows on the Trade Road`, expect follow-up tuning, polish, or bugfixes rather than a greenfield implementation.
- If the next task touches the new global reset command, keep documenting clearly that it is a Village Quest data reset, not a world or inventory wipe.
- If the next task continues the multiplayer quest-party batch, re-read both `26.1.2/MEMORY.md` and `1.21.11/MEMORY.md` first and preserve the dedicated-server-only rule.

## Current Beta State

- both maintained lines now carry the public multiplayer quest-party beta batch as `1.22.1-beta.3`
- runtime quest parties with `/vq party ...` commands and clickable chat invites
- `Questmaster` party drawer UI on shareable `Daily`, `Weekly`, and core `Story` entries
- pooled objective progress and pooled turn-in inventory across shareable `Daily`, `Weekly`, core `Story`, and normal `Pilgrim` combat-contract flows
- explicit chat offers for joining already running shared dailies, weeklies, story chapters, and pilgrim contracts
- `10 minute` disconnect grace before offline members are removed
- restart persistence for party membership, shared session state, pending offers, and disconnect grace
- repeatable `Daily`, `Weekly`, and `Pilgrim` target profiles now roll and persist as `light`/`normal`/`heavy`
- repeatable `Daily`, `Weekly`, and `Pilgrim` rewards now scale against those same profiles instead of staying fully static
- non-`Shadows` story arcs now use authored irregular target numbers instead of obvious stack or half-stack values
- the journal `Questmaster` shortcut is visible on every journal page on both lines
- current open risk is test coverage, not missing core plumbing
