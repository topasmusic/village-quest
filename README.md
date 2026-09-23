# Village Quest

`Village Quest 2.4.1 - The Guild Comes to Town` is released for Minecraft `26.3` and `26.2`. The older `26.1.2` and `1.21.11` lines remain on `2.1.1 - Homesteads & Wayfinding` and receive only deliberately scoped maintenance fixes.

This repository currently contains four version folders of `Village Quest`.

Current stable releases:

- `2.4.1 - The Guild Comes to Town` for Minecraft `26.3` and `26.2`;
- `2.1.1 - Homesteads & Wayfinding` for Minecraft `26.1.2` and `1.21.11`.

Version `2.4.1` includes the first-day choice, personal Village Contacts, Guild Corners, five local stories, ten identity-pair commissions, shared projects, a personal Chronicle, and `The Bells of Concord`. Guild Notice Posts now guide players through village introductions and stories in an interface. It retains the Living Village Network and existing save data.

Release highlights:

- `26.3` is the active Minecraft line with the complete 2.4.1 content and guided Guild Notice Post journey.
- `26.2` provides the same 2.4.1 content on the previous Minecraft line.
- The `26.1.2` and `1.21.11` releases retain the complete `2.1.1` feature-parity baseline and remain downloadable maintenance lines.

Village Quest `2.1.0` retains the complete `Roads Between Villages` feature set and expands it with `Prosperity & Prestige`.

- `26.3/` is the active Minecraft `26.3` Mojang-mapped work line.
- `26.2/` is the previous Minecraft `26.2` Mojang-mapped maintenance line.
- `26.1.2/` is the released Minecraft `26.1.2` Mojang-mapped maintenance port.
- `1.21.11/` is the released Minecraft `1.21.11` Yarn maintenance port.

Each folder is a self-contained Gradle project. Build and run the folder you actually want to work on.
Port behavior deliberately between lines; do not copy code blindly because mappings, APIs, Java targets, and client hooks differ.

## Version support

`2.1.1 - Homesteads & Wayfinding` remains the completed shared feature-parity baseline. Minecraft `26.3` is the single active content-development line; Minecraft `26.2` carries the same 2.4.1 content as a previous maintenance line. Older builds remain downloadable and may receive separately tested maintenance updates for confirmed bugs, save safety, severe exploits, or meaningful performance improvements.

When Village Quest adopts a later stable Minecraft target, that version replaces `26.3` as the single active line instead of adding another permanently maintained branch. See [VERSION_SUPPORT.md](VERSION_SUPPORT.md) for the complete policy.

## Licensing

Beginning with `2.0.0`, all four lines use the same mixed-license package: functional code is `LGPL-3.0-only`, while original Village Quest assets and creative content remain All Rights Reserved with limited permission to install and use official unmodified releases. Earlier published MIT versions remain MIT-licensed. The repository-level [LICENSE](LICENSE), [COPYING](COPYING), [COPYING.LESSER](COPYING.LESSER), [LICENSE-MIT](LICENSE-MIT), [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) are mirrored into each self-contained version folder, and its build embeds them in both runtime and sources JARs.

The legacy NPC and caravan skins with unknown provenance are recorded by exact filename and are expressly excluded from Village Quest ownership and licensing claims. Their replacement or source clearance remains a priority after the maintainer-authorized `2.0.0` publication.
