# Village Quest

`Village Quest` is a Fabric mod for Minecraft `1.21.11` focused on village-side progression:

- daily village quests
- weekly village quests
- a player journal
- a summonable `Questmaster` NPC via `/questmaster`
- a digital wallet with `Silvermark` / `Crown`
- a first player reputation foundation across village activities
- reputation-based pilgrim unlocks and progression
- the traveling `Pilgrim` trader with custom UI and rotating offers
- rare `Magic Shards`, including a hidden special quest and artifact ring

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.18.4+`
- Fabric API `0.141.3+1.21.11`
- Java `21`

## Installation

1. Install Fabric Loader for Minecraft `1.21.11`.
2. Put the built `village-quest-<version>.jar` into your `mods` folder.
3. Put the matching Fabric API jar into the same `mods` folder.
4. Start the game with Java `21`.

## Main Features

### Daily and weekly quests

- village-themed daily quests
- heavier weekly quests that reset once per week
- action-based progress tracking
- rewards paid into the wallet instead of dropping loose coin items
- daily quests also build player reputation in farming, crafting, animals, and village trade
- weekly quests are offered through the same `Questmaster` board and now also appear in the journal and tracker
- pilgrim wares now unlock step by step through that reputation system
- the daily quest offer is now surfaced through the summonable `Questmaster` NPC instead of a world block
- daily reset is currently `06:00 Europe/Berlin`
- weekly reset is currently Monday `06:00 Europe/Berlin`
- `Magic Shards` can extend this system:
  - `1` shard can unlock one extra daily after the normal daily
  - `10` shards can unlock the hidden `Whispers of the Shards` special quest

### Secret shard quest

- secret quest title:
  - `Whispers of the Shards`
- reward:
  - `Starreach Ring`
- flow:
  - complete the magical trial
  - return to the `Questmaster`
  - receive a buried-cache treasure map
  - dig up the cache and recover the ring
- the ring works from the offhand and grants `+2` block reach while building

## Full Wiki

The repo now includes a full wiki for the current `1.20.0` baseline:

- [Wiki Home](docs/wiki/README.md)
- [Getting Started](docs/wiki/getting-started.md)
- [Core Progression](docs/wiki/core-progression.md)
- [Stories and Village Projects](docs/wiki/stories-and-projects.md)
- [Relics, Wayfinder, and Magic Shards](docs/wiki/relics-and-shards.md)
- [Pilgrim and Roadside Watch](docs/wiki/pilgrim-and-roadside-watch.md)
- [Quest Reference](docs/wiki/quest-reference.md)
- [Commands and Admin Tools](docs/wiki/commands-and-admin.md)

## Player Commands

- `/questmaster`
- `/journal`
- `/wallet`
- `/reputation`
- `/dailyquest accept`

### Wallet

- active wallet currencies:
  - `10 Silvermarks = 1 Crown`
- player command:
  - `/wallet`

### Pilgrim trader

- traveling trader with a custom parchment-and-wood UI
- wallet-based purchases
- rotating offer pool
- timed appearance with natural spawn checks and despawn timer

## License

This project is licensed under the `MIT` License. See [LICENSE](LICENSE) for details.
- natural respawn cooldown after leaving

## Admin Commands

These are intended for server admins, pack makers, and testing only:

- `/questadmin resetdaily [player]`
- `/questadmin nextdaily [player]`
- `/questadmin completedaily [player]`
- `/questadmin resetweekly [player]`
- `/questadmin nextweekly [player]`
- `/questadmin completeweekly [player]`
- `/questadmin pilgrim spawn [player]`
- `/questadmin pilgrim despawn`
- `/questadmin wallet show [player]`
- `/questadmin wallet add <player> <amount> [silvermark|crown]`
- `/questadmin wallet remove <player> <amount> [silvermark|crown]`
- `/questadmin wallet set <player> <amount> [silvermark|crown]`

## Release Notes

- `Village Quest` no longer bundles the standalone `Wolkensprung` runtime.
- Active player-facing economy is now `Silvermark` and `Crown`.
- Pilgrim custom paintings and wallet visuals are part of the intended release content.
- The mod metadata is release-cleaned and no longer points at the Fabric example template.

## Manual Release Checklist

See [RELEASE_CHECKLIST.md](/C:/Users/me/Desktop/Topas%20Mods/Village%20Quest/RELEASE_CHECKLIST.md) for the final in-game verification pass before shipping a jar.
