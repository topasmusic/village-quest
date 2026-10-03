# Village Quest

`Village Quest 2.5.0 - Roads of Concord` was withdrawn for Minecraft `26.3` and `26.2` on 2026-10-04. The current stable release for both targets is `2.4.2`. The older `26.1.2` and `1.21.11` lines remain on `2.1.1 - Homesteads & Wayfinding` and receive only deliberately scoped maintenance fixes.

This repository currently contains four version folders of `Village Quest`.

Current stable releases:

- `2.4.2` for Minecraft `26.3` and `26.2`;
- `2.1.1 - Homesteads & Wayfinding` for Minecraft `26.1.2` and `1.21.11`.

The withdrawn `2.5.0` development source adds persistent caravan crews and a pack mule, Regional Dispatches, five Afterstories, Guild Convoys, village abandonment and recovery, and personal Guild/local trust with reparation. It retains the existing Living Village Network, quests, stories, permanent village bonds and save data.

Development source highlights (2.5.0 is withdrawn):

- `26.3` is the active Minecraft line with the complete 2.5.0 content and guided Guild Notice Post journey.
- `26.2` provides the same 2.5.0 content on the previous Minecraft line.
- The `26.1.2` and `1.21.11` releases retain the complete `2.1.1` feature-parity baseline and remain downloadable maintenance lines.

Village Quest `2.1.0` retains the complete `Roads Between Villages` feature set and expands it with `Prosperity & Prestige`.

- `26.3/` is the active Minecraft `26.3` Mojang-mapped work line.
- `26.2/` is the previous Minecraft `26.2` Mojang-mapped maintenance line.
- `26.1.2/` is the released Minecraft `26.1.2` Mojang-mapped maintenance port.
- `1.21.11/` is the released Minecraft `1.21.11` Yarn maintenance port.

Each folder is a self-contained Gradle project. Build and run the folder you actually want to work on.
Port behavior deliberately between lines; do not copy code blindly because mappings, APIs, Java targets, and client hooks differ.

## Documentation

- [Minecraft 26.3 wiki](26.3/docs/wiki/README.md)
- [Minecraft 26.2 wiki](26.2/docs/wiki/README.md)
- [Minecraft 26.1.2 wiki](26.1.2/docs/wiki/README.md)
- [Minecraft 1.21.11 wiki](1.21.11/docs/wiki/README.md)

The current Stable release is 2.4.2 on 26.3 and 26.2. Both wikis also document the withdrawn 2.5.0 development systems; these are not part of 2.4.2.

## Version support

`2.1.1 - Homesteads & Wayfinding` remains the completed shared feature-parity baseline. Minecraft `26.3` is the single active content-development line; Minecraft `26.2` has Stable 2.4.2 and retains the authorized 2.5.0 port as development source. Older builds remain downloadable and may receive separately tested maintenance updates for confirmed bugs, save safety, severe exploits, or meaningful performance improvements.

When Village Quest adopts a later stable Minecraft target, that version replaces `26.3` as the single active line instead of adding another permanently maintained branch. See [VERSION_SUPPORT.md](VERSION_SUPPORT.md) for the complete policy.

## Licensing

Beginning with `2.0.0`, all four lines use the same mixed-license package: functional code is `LGPL-3.0-only`, while original Village Quest assets and creative content remain All Rights Reserved with limited permission to install and use official unmodified releases. Earlier published MIT versions remain MIT-licensed. The repository-level [LICENSE](LICENSE), [COPYING](COPYING), [COPYING.LESSER](COPYING.LESSER), [LICENSE-MIT](LICENSE-MIT), [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) are mirrored into each self-contained version folder, and its build embeds them in both runtime and sources JARs.

Asset attribution is version-specific: see the matching line's `THIRD_PARTY_ASSETS.md` and `LICENSE`. The 2.5 development source uses 23 TopasMusic-owned entity skins; older published releases retain their original notices and rights.
