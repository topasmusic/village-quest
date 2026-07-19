# Village Quest

This repository currently contains three active version folders of `Village Quest`.

Current stable release: `2.0.0 - Roads Between Villages` on all three maintained lines.

Village Quest `2.0.0` is published for Minecraft `1.21.11`, `26.1.2`, and `26.2`. It contains `The Empty Caravan`, the `Caravan Yard`, five persistent surveyed trade routes, visible traveling caravans, the cached terrain map and configurable minimap, inhabited vanilla/CTOV village validation, route-owned caravan outfits, pause/removal cleanup, the unified Journal/Questmaster/Pilgrim interface, compatible right-click crop-harvest tracking, the five-rank Village Trade Guild, freight contracts, route specializations and investments, length-aware capped economics, Mastery, reward/shop retuning, the Roadwarden Horn, and one-time old-save backfills for missing unlock items.

Automated build and resource gates pass across all three lines with `1701` matching localization keys. Native gameplay/visual QA is anchored to `26.2`, with a focused `1.21.11` regression pass covering the target-specific caravan-spawn fix. The maintainer explicitly deferred the native `26.1.2` smoke test and a full no-admin-completion playthrough of `The Empty Caravan`.

- `26.2/` is the active Minecraft `26.2` Mojang-mapped reference work line.
- `26.1.2/` is the maintained Minecraft `26.1.2` Mojang-mapped port.
- `1.21.11/` is the maintained legacy Yarn port.

Each folder is a self-contained Gradle project. Build and run the folder you actually want to work on.
Port behavior deliberately between lines; do not copy code blindly because mappings, APIs, Java targets, and client hooks differ.

## Licensing

Beginning with `2.0.0`, all three lines use the same mixed-license package: functional code is `LGPL-3.0-only`, while original Village Quest assets and creative content remain All Rights Reserved with limited permission to install and use official unmodified releases. Earlier published MIT versions remain MIT-licensed. The repository-level [LICENSE](LICENSE), [COPYING](COPYING), [COPYING.LESSER](COPYING.LESSER), [LICENSE-MIT](LICENSE-MIT), [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) are mirrored into each self-contained version folder, and its build embeds them in both runtime and sources JARs.

The legacy NPC and caravan skins with unknown provenance are recorded by exact filename and are expressly excluded from Village Quest ownership and licensing claims. Their replacement or source clearance remains a priority after the maintainer-authorized `2.0.0` publication.
