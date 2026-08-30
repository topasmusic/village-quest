# Village Quest

`Village Quest 2.2.0 - The Shrines Between Roads` is released for Minecraft `26.2`. The older `26.1.2` and `1.21.11` lines remain on `2.1.1 - Homesteads & Wayfinding` and receive only deliberately scoped maintenance fixes.

This repository currently contains three version folders of `Village Quest`.

Current stable releases:

- `2.2.0 - The Shrines Between Roads` for Minecraft `26.2`;
- `2.1.1 - Homesteads & Wayfinding` for Minecraft `26.1.2` and `1.21.11`.

Version `2.2.0` adds village bonds and rotating requests, the owner-bound Wayshrine network, Guild Archive recovery, the journal Guild Atlas, new shrine blocks and relics, and the optional Guild Courier's Satchel. It is a `26.2`-only content release; the two older Minecraft lines remain on their latest stable maintenance build.

Release highlights:

- `26.2` adds the six-chapter `The Shrines Between Roads` endgame, persistent village identities and bonds, multi-day Guild Notice Board deliveries, Wayshrines, the Guild Atlas, Guild Archive recovery, new route incidents, and dedicated shrine assets.
- The `26.1.2` and `1.21.11` releases retain the complete `2.1.1` feature-parity baseline and remain downloadable maintenance lines.

Village Quest `2.1.0` retains the complete `Roads Between Villages` feature set and expands it with `Prosperity & Prestige`.

The published `2.2.0` build passed the full Java 25 Gradle test/build gate and the resource validator with `2213` matching English, German, and Spanish localization keys. The maintained `2.1.1` three-line baseline previously passed its target-specific Gradle builds and shared `1898`-key resource gate.

Local development artifacts use `build_channel=unreleased` plus a positive `unreleased_revision`, so their embedded version and filenames carry a distinct suffix such as `x.y.z-unreleased.1`. Increase the revision for every newly handed-off source state, but keep it unchanged for repeat builds of identical source. The latest published Stable runtime and sources JARs are kept beside the latest numbered Unreleased pair instead of being overwritten or reconstructed from development source.

- `26.2/` is the active Minecraft `26.2` Mojang-mapped reference work line.
- `26.1.2/` is the released Minecraft `26.1.2` Mojang-mapped maintenance port.
- `1.21.11/` is the released Minecraft `1.21.11` Yarn maintenance port.

Each folder is a self-contained Gradle project. Build and run the folder you actually want to work on.
Port behavior deliberately between lines; do not copy code blindly because mappings, APIs, Java targets, and client hooks differ.

## Version support

`2.1.1 - Homesteads & Wayfinding` is the final completed feature-parity release for Minecraft `26.2`, `26.1.2`, and `1.21.11`. Minecraft `26.2` is now the single active content-development line. The older builds remain downloadable and may receive separately tested maintenance updates for confirmed bugs, save safety, severe exploits, or meaningful performance improvements, but they do not receive new content, interface redesigns, or balance backports.

When Village Quest adopts a later stable Minecraft target, that version replaces `26.2` as the single active line instead of adding another permanently maintained branch. See [VERSION_SUPPORT.md](VERSION_SUPPORT.md) for the complete policy.

## Licensing

Beginning with `2.0.0`, all three lines use the same mixed-license package: functional code is `LGPL-3.0-only`, while original Village Quest assets and creative content remain All Rights Reserved with limited permission to install and use official unmodified releases. Earlier published MIT versions remain MIT-licensed. The repository-level [LICENSE](LICENSE), [COPYING](COPYING), [COPYING.LESSER](COPYING.LESSER), [LICENSE-MIT](LICENSE-MIT), [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) are mirrored into each self-contained version folder, and its build embeds them in both runtime and sources JARs.

The legacy NPC and caravan skins with unknown provenance are recorded by exact filename and are expressly excluded from Village Quest ownership and licensing claims. Their replacement or source clearance remains a priority after the maintainer-authorized `2.0.0` publication.
