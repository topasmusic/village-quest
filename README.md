# Village Quest

`Village Quest 2.3.1 - Living Village Network Reliability Hotfix` is released for Minecraft `26.2`. The older `26.1.2` and `1.21.11` lines remain on `2.1.1 - Homesteads & Wayfinding` and receive only deliberately scoped maintenance fixes.

This repository currently contains three version folders of `Village Quest`.

Current stable releases:

- `2.3.1 - Living Village Network Reliability Hotfix` for Minecraft `26.2`;
- `2.1.1 - Homesteads & Wayfinding` for Minecraft `26.1.2` and `1.21.11`.

Version `2.3.1` retains the complete 2.3.0 Living Village Network feature set and adds targeted reliability fixes for consumed shared turn-ins, historical village identity beyond eight records, and Notice Board payload bounds. It also contains the complete save-compatible `2.2.1` quality and balance pass. It is a `26.2`-only hotfix; the two older Minecraft lines remain on their latest stable maintenance build.

Release highlights:

- `26.2` connects permanent village bonds to visible local conditions, identity-specific needs, three-choice Notice Board deliveries, freight and route consequences, renewable Wayshrine energy, guild projects, prestige, and a redesigned Journal and Guild Atlas.
- The `26.1.2` and `1.21.11` releases retain the complete `2.1.1` feature-parity baseline and remain downloadable maintenance lines.

Village Quest `2.1.0` retains the complete `Roads Between Villages` feature set and expands it with `Prosperity & Prestige`.

The published `2.3.1` build passes all `73` Java 25 tests in `24` suites and the resource validator with `2336` matching English, German, and Spanish localization keys. The maintained `2.1.1` baseline also passes its shared `1898`-key resource gate.

Local development artifacts use `build_channel=unreleased` plus a positive `unreleased_revision`, so their embedded version and filenames carry a distinct suffix such as `x.y.z-unreleased.1`. Increase the revision for every newly handed-off source state, but keep it unchanged for repeat builds of identical source. The latest published Stable runtime and sources JARs are kept beside the latest numbered Unreleased pair instead of being overwritten or reconstructed from development source.

- `26.2/` is the active Minecraft `26.2` Mojang-mapped reference work line.
- `26.1.2/` is the released Minecraft `26.1.2` Mojang-mapped maintenance port.
- `1.21.11/` is the released Minecraft `1.21.11` Yarn maintenance port.

Each folder is a self-contained Gradle project. Build and run the folder you actually want to work on.
Port behavior deliberately between lines; do not copy code blindly because mappings, APIs, Java targets, and client hooks differ.

## Version support

`2.1.1 - Homesteads & Wayfinding` remains the completed shared feature-parity baseline, while Minecraft `26.2` now carries the `2.3.1` hotfix for the 2.3 Living Village Network line and remains the single active content-development line. The older builds remain downloadable and may receive separately tested maintenance updates for confirmed bugs, save safety, severe exploits, or meaningful performance improvements, but they do not receive new content, interface redesigns, or balance backports.

When Village Quest adopts a later stable Minecraft target, that version replaces `26.2` as the single active line instead of adding another permanently maintained branch. See [VERSION_SUPPORT.md](VERSION_SUPPORT.md) for the complete policy.

## Licensing

Beginning with `2.0.0`, all three lines use the same mixed-license package: functional code is `LGPL-3.0-only`, while original Village Quest assets and creative content remain All Rights Reserved with limited permission to install and use official unmodified releases. Earlier published MIT versions remain MIT-licensed. The repository-level [LICENSE](LICENSE), [COPYING](COPYING), [COPYING.LESSER](COPYING.LESSER), [LICENSE-MIT](LICENSE-MIT), [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) are mirrored into each self-contained version folder, and its build embeds them in both runtime and sources JARs.

The legacy NPC and caravan skins with unknown provenance are recorded by exact filename and are expressly excluded from Village Quest ownership and licensing claims. Their replacement or source clearance remains a priority after the maintainer-authorized `2.0.0` publication.
