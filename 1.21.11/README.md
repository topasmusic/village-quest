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
- Level-scaled experience rewards that advance the same number of bars at vanilla level `10` or `200+`
- A unified dark-oak, parchment, brass, and teal interface across the `Questmaster`, `Pilgrim`, Journal, Caravan Ledger, and minimap, including one shared soft ambient/lower-right panel shadow
- The compact modular `Questmaster` dashboard separates Daily, Weekly, Story, and Special work into icon tabs, with a dedicated quest list and a scrollable detail card for descriptions, objectives, rewards, and actions
- A compact five-tab Journal with collapsible progress cards, a quest tracker, and a wallet with `Silvermark` and `Crown`
- Reputation across farming, crafting, animals, trade, and later roadside work
- Story arcs and permanent village projects
- Existing saves keep their progression and receive missing `Caravan Ledger` or `Roadwarden Horn` unlock items once on login when their old progress already qualifies
- `The Empty Caravan`, a six-chapter late story with investigation, a moral choice, and a bait-caravan defense
- A permanent `Caravan Yard` project with up to five persistent village-to-village trade routes
- An earlier one-route preview through the `Market Charter`, followed by the five-rank `Village Trade Guild`
- Daily freight contracts, six route specializations, and six permanent route investments
- Length-aware route income, daily network limits, and an offline trade-office escrow
- Persistent route surveys with up to 48 waypoints, so real player-built detours guide the simulation, map, and visible caravans
- Visible traveling merchant caravans with varied medieval outfits that favor player-built roads without permanently loading chunks
- Caravan recovery that avoids hazardous ledges, regroups scattered merchants, and safely returns persistently blocked groups to background simulation
- A smooth, cached terrain-backed `Caravan Ledger` map with left-mouse dragging, village nodes, surveyed route lines, moving caravans, player position, hover details, security, road quality, earnings, emergencies, and route removal
- A real-time terrain minimap for villages, routes, the player, caravans, and incidents, toggled by the configurable `,` key or `/vq routes minimap`
- Route registration accepts vanilla and CTOV generated villages only while at least one living normal villager remains, rejecting abandoned and zombie villages
- Eight recurring route events ranging from broken wheels and washed-out bridges to missing couriers and false distress calls
- Farming quest progress supports normal crop breaking and compatible right-click-and-replant harvest flows
- The traveling `Pilgrim` trader with rotating wares
- `Shadows on the Trade Road`, a late `Questmaster` follow-up arc after `Watch Bell` plus `3` completed `Pilgrim` combat rumors
- Nighttime caravan defense encounters with `Caravan Merchant` survivors and elite `Traitor` bandits in the final convoy wave
- Story-linked `Wayfinder's Compass` modes for `Caravan Distress` and `Guild Convoy`
- Contextual villager dialogue for talk-based `Market Rounds`, `Market Road Troubles`, and `Shadows` objectives
- `Magic Shards`, special quests, and relic rewards
- Capped post-200 reputation Mastery and the functional `Roadwarden Horn` monster-hunting relic
- Daily reset at `06:00 Europe/Berlin`
- Weekly reset on Monday at `06:00 Europe/Berlin`

## Documentation

Project docs are available in the local wiki:

- [Wiki Home](docs/wiki/README.md)
- [Getting Started](docs/wiki/getting-started.md)
- [Core Progression](docs/wiki/core-progression.md)
- [Stories and Village Projects](docs/wiki/stories-and-projects.md)
- [Trade Routes and Caravans](docs/wiki/trade-routes-and-caravans.md)
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
- `/vq daily reroll`
- `/vq routes`
- `/vq routes minimap`
- `/vq routes guild`
- `/vq routes contracts`
- `/vq routes contracts accept <1-3> <route>`
- `/vq routes contracts supply`
- `/vq routes specialize <route> <general|provisions|forge|livestock|courier|guarded>`
- `/vq routes upgrade <route> <wheels|lanterns|covers|escorts|insurance|office>`
- `/vq routes register`
- `/vq routes remove <1-5>`
- `/vq routes survey start <1-5>`
- `/vq routes survey mark`
- `/vq routes survey finish`
- `/vq routes survey cancel`

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
- `/vq admin routes testsetup [player]`
- `/vq admin routes testevent <1-5> <clear|broken_wheel|injured_pack_animal|washed_out_bridge|false_distress|hungry_travelers|road_toll|missing_courier|storm_camp>`
- `/vq admin routes reset [player]`
- `/vq admin pilgrim spawn [player]`
- `/vq admin pilgrim despawn`
- `/vq admin wallet show [player]`
- `/vq admin wallet add <player> <amount> [silvermark|crown]`
- `/vq admin wallet remove <player> <amount> [silvermark|crown]`
- `/vq admin wallet set <player> <amount> [silvermark|crown]`

`/vq admin reset complete` resets the saved Village Quest server data for every player, including wallet, reputation, daily/weekly progress, story and project state, special quests, pilgrim contracts, cooldowns, quest parties, invites, shared sessions, and reconnect-grace state. It also closes active Village Quest UIs and trades, clears runtime quest-session caches, and despawns spawned Questmasters, Pilgrims, caravan merchants, couriers, traitors, and active road-defense mobs. It does not wipe world blocks or player inventories.

## License

Village Quest uses a mixed-license model beginning with `2.0.0`:

- functional source code is licensed under `LGPL-3.0-only`;
- original Village Quest assets and creative content are All Rights Reserved, with limited permission to install and use official unmodified releases;
- third-party and provenance-sensitive files keep their own terms and are listed in [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); and
- releases already published under MIT remain MIT-licensed under the notice preserved in [LICENSE-MIT](LICENSE-MIT).

See [LICENSE](LICENSE), [COPYING](COPYING), and [COPYING.LESSER](COPYING.LESSER) for the complete terms. The unresolved legacy entity skins listed in `THIRD_PARTY_ASSETS.md` are excluded from Village Quest ownership and licensing claims; replacement or source clearance remains a priority.
