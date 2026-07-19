# Village Quest

This repository currently contains three active version folders of `Village Quest`.

Current stable release: `1.22.8` on all three maintained lines.

Current local release candidate: `2.0.0` is prepared on all three maintained lines. It contains `The Empty Caravan`, the `Caravan Yard`, five persistent surveyed trade routes, visible traveling caravans, the cached terrain map and configurable minimap, inhabited vanilla/CTOV village validation, route-owned caravan outfits, pause/removal cleanup, the unified Journal/Questmaster/Pilgrim interface, compatible right-click crop-harvest tracking, the five-rank Village Trade Guild, freight contracts, route specializations and investments, length-aware capped economics, Mastery, reward/shop retuning, the Roadwarden Horn, and one-time old-save backfills for missing unlock items. The source candidate is kept in version control but remains untagged and unpublished; `1.22.8` remains the current public stable release.

Automated release gates pass across all three lines with `1701` matching localization keys. Native gameplay/visual QA is anchored to `26.2`, with a focused `1.21.11` regression pass covering the target-specific caravan-spawn fix. The user explicitly deferred the native `26.1.2` smoke test and a full no-admin-completion playthrough of `The Empty Caravan`.

- `26.2/` is the active Minecraft `26.2` Mojang-mapped reference work line.
- `26.1.2/` is the maintained Minecraft `26.1.2` Mojang-mapped port.
- `1.21.11/` is the maintained legacy Yarn port.

Each folder is a self-contained Gradle project. Build and run the folder you actually want to work on.
Port behavior deliberately between lines; do not copy code blindly because mappings, APIs, Java targets, and client hooks differ.

## Licensing

Beginning with the prepared `2.0.0` candidate, all three lines use the same mixed-license package: functional code is `LGPL-3.0-only`, while original Village Quest assets and creative content remain All Rights Reserved with limited permission to install and use official unmodified releases. Earlier published MIT versions remain MIT-licensed. The repository-level [LICENSE](LICENSE), [COPYING](COPYING), [COPYING.LESSER](COPYING.LESSER), [LICENSE-MIT](LICENSE-MIT), [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) are mirrored into each self-contained version folder, and its build embeds them in both runtime and sources JARs.

The legacy NPC and caravan skins with unknown provenance are recorded by exact filename and remain the only unresolved asset-provenance release gate.
