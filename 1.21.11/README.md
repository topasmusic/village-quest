# Village Quest for Minecraft 1.21.11

`Village Quest` is a Fabric mod built around village progression. Take on daily and weekly work, build reputation, unlock story arcs and village projects, and later deal with the `Pilgrim` and the road beyond the village.

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.19.2+`
- Fabric API `0.141.3+1.21.11`
- Java `21`

## Installation

1. Install Fabric Loader for Minecraft `1.21.11`.
2. Put `village-quest-<version>-mc1.21.11.jar` into your `mods` folder.
3. Put the matching Fabric API jar into the same `mods` folder.
4. Start the game with Java `21`.

## Highlights

- Daily and weekly quests through the `Questmaster`
- A journal, quest tracker, and wallet with `Silvermark` and `Crown`
- Reputation across farming, crafting, animals, trade, and later roadside work
- Story arcs and permanent village projects
- The traveling `Pilgrim` trader with rotating wares
- `Shadows on the Trade Road`, a late `Questmaster` follow-up arc after `Watch Bell` plus `3` completed `Pilgrim` combat rumors
- Nighttime caravan defense encounters with `Caravan Merchant` survivors and elite `Traitor` bandits in the final convoy wave
- Story-linked `Wayfinder's Compass` modes for `Caravan Distress` and `Guild Convoy`
- Contextual villager dialogue for talk-based `Market Rounds`, `Market Road Troubles`, and `Shadows` objectives
- `Magic Shards`, special quests, and relic rewards
- Daily reset at `06:00 Europe/Berlin`
- Weekly reset on Monday at `06:00 Europe/Berlin`

## Documentation

Project docs are available in the local wiki:

- [Wiki Home](docs/wiki/README.md)
- [Getting Started](docs/wiki/getting-started.md)
- [Core Progression](docs/wiki/core-progression.md)
- [Stories and Village Projects](docs/wiki/stories-and-projects.md)
- [Relics, Wayfinder, and Magic Shards](docs/wiki/relics-and-shards.md)
- [Pilgrim and Roadside Watch](docs/wiki/pilgrim-and-roadside-watch.md)
- [Quest Reference](docs/wiki/quest-reference.md)
- [Commands and Admin Tools](docs/wiki/commands-and-admin.md)

## Player Commands

- `/vq questmaster`
- `/vq journal`
- `/vq wallet`
- `/vq reputation`
- `/vq daily accept`

Wallet rate:
- `10 Silvermarks = 1 Crown`

The `Pilgrim` is a timed trader with wallet-based purchases, rotating wares, and a natural respawn cooldown after leaving.

## Admin Commands

These are intended for server admins, pack makers, and testing only:

- `/vq admin resetdaily [player]`
- `/vq admin nextdaily [player]`
- `/vq admin completedaily [player]`
- `/vq admin resetweekly [player]`
- `/vq admin nextweekly [player]`
- `/vq admin completeweekly [player]`
- `/vq admin reset complete`
- `/vq admin story shadows unlock [player]`
- `/vq admin story shadows testrescue [player]`
- `/vq admin story shadows testfinal [player]`
- `/vq admin pilgrim spawn [player]`
- `/vq admin pilgrim despawn`
- `/vq admin wallet show [player]`
- `/vq admin wallet add <player> <amount> [silvermark|crown]`
- `/vq admin wallet remove <player> <amount> [silvermark|crown]`
- `/vq admin wallet set <player> <amount> [silvermark|crown]`

`/vq admin reset complete` resets the saved Village Quest server data for every player, including wallet, reputation, daily/weekly progress, story and project state, special quests, pilgrim contracts, and cooldowns. It also closes active Village Quest UIs and trades, clears runtime quest-session caches, and despawns spawned Questmasters and Pilgrims. It does not wipe world blocks or player inventories.

## License

This project is licensed under the `MIT` License. See [LICENSE](LICENSE) for details.
