# Village Quest

Development after the public `2.1.1` release continues only on Minecraft `26.2`. `2.1.1 - Homesteads & Wayfinding` is available with matching features on all three supported Minecraft targets; the two older lines now return to maintenance status.

This repository currently contains three version folders of `Village Quest`.

Current stable release: `2.1.1 - Homesteads & Wayfinding` for Minecraft `26.2`, `26.1.2`, and `1.21.11`.

Release highlights:

- All three targets share permanent village investment ranks, Pilgrim commissions, paid services, prestige collection rewards, selectable route liveries, an economy ledger, responsive boards, clearer staged quests, accurate crop and ore yields, stronger hand-in validation, progress sounds, and polished Journal navigation.
- `26.2` is now the active content-development line. The `26.1.2` and `1.21.11` releases remain available as the final planned feature-parity ports.

Village Quest `2.1.0` retains the complete `Roads Between Villages` feature set and expands it with `Prosperity & Prestige`.

The published three-line baseline passed automated build and resource gates with `1701` matching localization keys. Native gameplay/visual QA is anchored to `26.2`, with a focused `1.21.11` regression pass covering the target-specific caravan-spawn fix. The maintainer explicitly deferred the native `26.1.2` smoke test and a full no-admin-completion playthrough of `The Empty Caravan`.

All three release lines share the same `2.1.0` localization and asset set with `1841` matching keys.

Local development artifacts use `build_channel=unreleased` plus a positive `unreleased_revision`, so their embedded version and filenames carry a distinct suffix such as `2.1.0-unreleased.1`. Increase the revision for every newly handed-off source state, but keep it unchanged for repeat builds of identical source. The last published Stable runtime and sources JARs are kept beside the latest numbered Unreleased pair in each line's `build/libs` directory instead of being overwritten or reconstructed from development source.

- `26.2/` is the active Minecraft `26.2` Mojang-mapped reference work line.
- `26.1.2/` is the released Minecraft `26.1.2` Mojang-mapped maintenance port.
- `1.21.11/` is the released Minecraft `1.21.11` Yarn maintenance port.

Each folder is a self-contained Gradle project. Build and run the folder you actually want to work on.
Port behavior deliberately between lines; do not copy code blindly because mappings, APIs, Java targets, and client hooks differ.

## Version support

`2.1.0 - Prosperity & Prestige` is the final planned feature-parity release for Minecraft `26.2`, `26.1.2`, and `1.21.11`. Minecraft `26.2` is now the single active content-development line. The older builds remain downloadable and may receive discretionary fixes for crashes, persistent-data corruption, or severe exploits, but they do not receive guaranteed content, interface, compatibility, or balance backports.

When Village Quest adopts a later stable Minecraft target, that version replaces `26.2` as the single active line instead of adding another permanently maintained branch. See [VERSION_SUPPORT.md](VERSION_SUPPORT.md) for the complete policy.

## Licensing

Beginning with `2.0.0`, all three lines use the same mixed-license package: functional code is `LGPL-3.0-only`, while original Village Quest assets and creative content remain All Rights Reserved with limited permission to install and use official unmodified releases. Earlier published MIT versions remain MIT-licensed. The repository-level [LICENSE](LICENSE), [COPYING](COPYING), [COPYING.LESSER](COPYING.LESSER), [LICENSE-MIT](LICENSE-MIT), [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) are mirrored into each self-contained version folder, and its build embeds them in both runtime and sources JARs.

The legacy NPC and caravan skins with unknown provenance are recorded by exact filename and are expressly excluded from Village Quest ownership and licensing claims. Their replacement or source clearance remains a priority after the maintainer-authorized `2.0.0` publication.
