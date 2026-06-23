# Legacy Mini Blocks Audit

Date: 2026-03-16
Repo: `village-quest-template-1.21.10`

## Summary

There are two separate topics:

1. `mini_blocks:*` warnings come from the local `run` environment, not from the `village-quest` source code.
2. The mod itself still contains old `village-quest` mini/deco item ids, but they are currently kept only as legacy compatibility registrations.

## External `mini_blocks` content

The untranslated tag warnings reference the `mini_blocks` namespace.

Evidence:

- `run/logs/latest.log` shows the warning entries for `mini_blocks:*`.
- The local test environment contains `run/resourcepacks/mini-blocks`.
- The current test world contains `run/saves/New World/datapacks/mini-blocks`.

Conclusion:

- `mini_blocks` is not an active namespace implemented by this mod.
- It is local pack/datapack content in the `run` folder.

## Legacy `village-quest` mini/deco item ids

`src/main/java/de/quest/registry/ModItems.java` registers these ids through `registerLegacyItem(...)`:

- `cloud_cape`
- `dekoblock_acacia_log`
- `dekoblock_beacon`
- `dekoblock_honigfass`
- `dekoblock_honigwabe`
- `honig_eimer`
- `honig_fass`
- `honigfass`
- `mini_amethyst_block`
- `mini_honey_block`
- `mini_honey_head`
- `mini_stone`
- `questgemaelde_hund`
- `test_honig`

Purpose today:

- Keep old worlds and statistics files loadable.
- Provide matching names/models for old item ids if they still exist in saves or stats.

## Active gameplay usage today

Current gameplay does not use those legacy ids as quest rewards.

Active reward logic uses:

- custom player heads created in `VillageQuest.createHoneyBarrelHead()`
- custom player heads created in `VillageQuest.createAltRewardHead()`
- normal painting reward creation in `DailyQuestService`

This means:

- `mini_honey_head`, `mini_honey_block`, `mini_stone`, `dekoblock_*`, `honig_fass`, etc. are not part of the current quest reward path
- they are compatibility leftovers, not active feature content

## Resource state

The repo still ships language entries and item model json files for the legacy ids.

That is consistent with the compatibility approach:

- old ids can still resolve to a visible name/model
- old saves/stats do not break on load

## Cleanup recommendation

Safe now:

1. Treat `run/resourcepacks/mini-blocks` and `run/saves/New World/datapacks/mini-blocks` as local test-world baggage, not as core mod content.
2. Leave the legacy `village-quest` item ids in code for now.
3. Do not build new quest rewards on top of those leftovers unless we intentionally bring the feature back.

Not safe without a migration decision:

1. Removing the legacy registrations from `ModItems.java`
2. Deleting the matching language/model files from the shipped mod

Those removals can reintroduce world/stat loading errors for older data.

## Suggested next decision

Choose one path:

1. Keep the legacy ids as compatibility-only and ignore them for new content.
2. Reintroduce mini/deco rewards as a real feature with clean item definitions and reward logic.
3. Plan a proper deprecation/migration, then remove the old ids in a later major version.
