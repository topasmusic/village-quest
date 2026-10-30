# Commands and Admin Tools

All commands are available as `/villagequest ...` and the short alias `/vq ...`.

In Village Quest 2.5, messages about features that still need a project, rank or story unlock use gray text. This includes the Journal's Map and Prosperity actions and locked services or Shop offers.

## Player Commands

### Personal restart

Back up a real save before testing a personal restart or updating an existing
world. In the Progression Overhaul build, a living Survival player can use
`/vq reset`, read the warning and run `/vq reset confirm`
within two minutes. A direct `/vq reset confirm` first shows the warning; repeat it to confirm. Each confirmation works once, on the same
server and for its requesting player. There is no other-player or console form;
operator permission does not bypass these rules.

This resets your quests in both story slots, personal reputation, village links,
routes and VQ money, and leaves your party and guild. It removes VQ items from
your inventory and Ender Chest, including nested portable storage, cursor and
crafting inputs. Ordinary Minecraft items are kept. Before removal, all cursor
and crafting contents must fit in saved inventory slots, and ordinary contents
inside removed VQ bags must have room. Close other containers first.

Placed world blocks and buildings, shared village rights, other players,
in-progress shared contributions and earned shared-project claims remain. If you are the last guild member, finish any active shared project before restarting. The reset is blocked before item removal while that project is unfinished.
Collect other earned reward choices/item deliveries and settle supplied freight
or market transfers before restarting. Existing reward limits, first rewards
and transaction records are preserved. Repeating already paid one-time stories does not pay their
money, experience or materials again; progression unlocks can be earned again.
The short introductory Daily is paid normally after a restart when a new Daily
allowance is free, and consumes that allowance. Resetting never clears a paid cycle.
Personal tools may be issued at a new owner generation, invalidating your old
issues stored elsewhere. Anonymous old transferable items cannot be traced back
to their original player.

The reset records and reads back native player and world saves at its phase
boundaries. Login resolves an interrupted reset before normal quest actions.
If records are unknown or inconsistent, access stays locked with the claims
preserved. Ask the server operator to inspect records/backups; do not force a
second reset by deleting the records. Actual migration, multiplayer and
power-loss behavior still require manual testing.

### Core Commands

- `/vq questmaster`
  Summon the `Questmaster` near you.

- `/vq journal`
  Open or close the journal. The Journal opens directly; settings are optional.

- `/vq settings`
  Open journal settings directly. World options require current world-owner/operator permission on the server; personal settings are available to every player.

- `/vq prosperity`
  Open the Prosperity, commissions, services, collection, and economy-ledger board after a matching project or trade route has unlocked access.

- `/vq wallet`
  Show your wallet balance.

- `/vq reputation`
  Show your current reputation.

- `/vq routes`
  Open the `Caravan Ledger` route map after the `Market Charter` is unlocked.

- `/vq routes minimap`
  Toggle the compact live trade-network minimap. The configurable default key is `,` under the `Village Quest` controls category.

- `/vq routes yard`
  Confirm the current safe Overworld position as a player-built Homestead Trade Post after the Market Charter has granted route access. Requires no installed routes and a second confirmation within `30 seconds`; the later Caravan Yard is not required.

- `/vq diagnose`
  Print a read-only reset, route, caravan, stuck-group, and orphan-entity report. Operators can use `/vq diagnose <player>` for another player.

- `/vq town retrofit`
  Request a Guild Corner in the village around you. If the decorative preset cannot fit, a compact Notice Post is placed on safe nearby ground. Stand beside your cleared area and retry: the local search checks densely around you, including forest villages. Necessary natural foliage/tree blocks may be cleared; buildings and containers stay protected. Failed old reservations and missing or deliberately removed generated boards can recover through this explicit command. The required area must be loaded; a compact board occupies three blocks of width and two of height, with a clear approach.

- `/vq routes guild`
  Show trade-guild rank, score, daily income, escrow, route specialization, road length, quality, and solved incidents.

- `/vq routes approach <route 1-5> <careful|bold>`
  Choose the route's visible incident plan. Careful lowers frequency, reward, and failure strain and adds protection; Bold raises opportunity, reward, and repairable risk.

- `/vq routes contracts`
  Show the current freight contract or up to three daily guild offers.

- `/vq routes contracts accept <1-3> <route 1-5>`
  Assign one board offer to a registered route.

- `/vq routes contracts supply`
  Load the active contract from the player's inventory. The assigned route's next arrival completes it.

- `/vq routes specialize <route> <general|provisions|forge|livestock|courier|guarded>`
  Choose a freight identity. The first choice is free; later refits cost `15 Silvermarks`.

- `/vq routes upgrade <route> <wheels|lanterns|covers|escorts|insurance|office>`
  Buy a permanent route investment, subject to guild rank and wallet cost.

- `/vq routes register`
  Register the generated, inhabited village around the player as a route node. A generated village with no living normal villagers is rejected.

- `/vq routes remove <1-5>`
  Remove one registered route. Later routes are shifted down into the open slot.

- `/vq routes survey start <1-5>`
  Start a persistent waypoint draft for the selected route and temporarily pause its caravan.

- `/vq routes survey mark`
  Mark the player's current Overworld position as the next bend or anchor. Sneak-using the `Caravan Ledger` does the same while a survey is active.

- `/vq routes survey finish`
  Install the draft so the map, simulation, quality sampling, event targets, and visible caravan use it.

- `/vq routes survey cancel`
  Discard the draft and restore the previous route and pause state.

- `/vq network`
  Show recorded villages, Adventure Profile, network renown, rank, honor title, and specialization.

- `/vq network specialize <steward|courier|wayfarer>`
  Preview a permanent network specialization after rank 2 without applying it.

- `/vq network specialize <steward|courier|wayfarer> confirm`
  Confirm and permanently apply the named specialization. It can only be chosen once.

### Optional Living-Network Guild

- `/vq guild create <name>`
- `/vq guild invite <player>`
- `/vq guild accept`
- `/vq guild status`
- `/vq guild promote <player>`
- `/vq guild transfer <player>`
- `/vq guild kick <player>`
- `/vq guild project <common_reserve|waystation|archive_exchange>`
- `/vq guild leave`

The shared guild stores only membership, roles, renown, and its selected network project. Personal story rewards, Archive ownership, and unique tools remain personal. Leaders promote Stewards, transfer leadership, or remove members; Leaders and Stewards may invite and choose a project after guild rank 2. A Leader with other members must transfer leadership or remove those members before leaving, so an offline member can never permanently trap guild management.

`invite`, `promote`, `transfer`, and `kick` resolve stored player profiles as well as online players. After a player has joined the server at least once, these actions can therefore target that cached name while the player is offline.

### Quest Commands

- `/vq daily accept`
  Accept the currently pending daily-related offer.

- `/vq daily reroll`
  Replace today's unaccepted daily selection once. Three combined post-200 Mastery levels grant a second reroll. In 2.6.3, the Quest Master's three choices refresh together and old choice buttons expire; ordinary reopening or cancellation keeps the same choices.

Village Quest 2.6.3 offers three normal Daily choices every cycle after the easier introduction. Choose one in the Quest Master window. Its saved target profile and progress remain active through calendar changes until completion or cancellation. Normal and shard-bonus Dailies share one active slot. Explicit personal selection keeps the chosen quest type; use the separate party invitation to join a running group quest.

### Quest Party

- `/vq party show`
  Show your current quest party state.

- `/vq party invite <player>`
  Invite an online player into your quest party.

- `/vq party accept`
  Accept your currently pending quest party invite.

- `/vq party decline`
  Decline your currently pending quest party invite.

- `/vq party leave`
  Leave your current quest party.

- `/vq party disband`
  Disband your current quest party if you are the leader.

- `/vq party share daily accept`
  Accept the currently pending shared daily join offer for a running party quest.

- `/vq party share daily decline`
  Decline the currently pending shared daily join offer.

- `/vq party share weekly accept`
  Accept the currently pending shared weekly join offer for a running party quest.

- `/vq party share weekly decline`
  Decline the currently pending shared weekly join offer.

- `/vq party share story accept [core|kingdom]`
  Accept one pending story-chapter offer. Choose `core` or `kingdom` when both are open; the chapter's chat buttons select the correct slot automatically.

- `/vq party share story decline [core|kingdom]`
  Decline only the selected story offer. With two open offers, the general command leaves both unchanged and asks you to select a slot.

- `/vq party share pilgrim accept`
  Accept the currently pending shared pilgrim-contract join offer for a running party contract.

- `/vq party share pilgrim decline`
  Decline the currently pending shared pilgrim-contract join offer.

Quest-party notes:

- parties work on dedicated multiplayer servers and integrated worlds opened to LAN; mod-hosted sessions such as Essential are also detected when another player has joined
- the `Questmaster` party drawer is available to both host and guests for shareable quests; private singleplayer worlds keep it hidden
- saved parties load when the world becomes multiplayer and remain stored when the shared session ends
- co-op scope currently covers shareable `Daily`, `Weekly`, core `Story`, and normal `Pilgrim` combat-contract flows
- `Shadows on the Trade Road`, `The Empty Caravan`, persistent route events, relic/special quests, and compass-item quest lines remain solo by design

### Quest Tracker

- `/vq questtracker`
  Toggle the permanent quest tracker.

- `/vq questtracker on`
  Force the tracker on.

- `/vq questtracker off`

The default `.` key toggles the same saved tracker state and can be rebound under `Options -> Controls -> Key Binds -> Village Quest`.
  Force the tracker off.

## Admin Commands

These are intended for testing, packmaking, and server administration.

### Daily

- `/vq admin resetdaily [player]`
- `/vq admin nextdaily [player]`
- `/vq admin completedaily [player]`

`resetdaily` also clears that player's per-village Guild Notice Board delivery locks, so the current requests can be handed in again during focused admin testing.

### Weekly

- `/vq admin resetweekly [player]`
- `/vq admin nextweekly [player]`
- `/vq admin completeweekly [player]`

### Global Reset

- `/vq admin reset complete`

This command clears the saved Village Quest state for every player at once:

- wallet and reputation
- daily, weekly, story, special, and pilgrim progress
- unlocked village projects
- registered villages, persistent trade routes, route events, quality, and earnings
- living-village supply, energy, prestige, specialization, and optional shared guild state
- pilgrim natural spawn cooldown state
- quest parties, pending invites, shared sessions, and reconnect-grace state
- active Village Quest journals, trackers, questmaster sessions, and pilgrim trades
- spawned Questmasters and Pilgrims plus runtime quest-session caches
- late-road runtime entities such as caravan merchants, route couriers, bait caravans, spawned hostiles, and traitors

It is a Village Quest data reset, not a world or inventory wipe.

### Story

- `/vq admin story show [player]`
- `/vq admin story reset [player]`
- `/vq admin story complete [player]`
- `/vq admin story shadows unlock [player]`
- `/vq admin story shadows testrescue [player]`
- `/vq admin story shadows testfinal [player]`

`/vq admin story shadows unlock` prepares the late story for testing by ensuring:

- `Watch Bell`
- `3` completed combat rumor flags
- story discovery for `Shadows on the Trade Road`
- a carried `Wayfinder's Compass`
- unlocked compass structure modes if they were still missing

`/vq admin story shadows testrescue` jumps the player into the regular caravan-defense test state, chooses a prevalidated dry encounter site roughly `320–500` blocks away, and advances the Overworld clock to the required night. The complete merchant formation must fit before the compass target is committed. On Peaceful the merchants and escort remain present while hostile waves stay at zero; if the final spawn still fails, the encounter relocates or reports its reschedule instead of failing silently.

`/vq admin story shadows testfinal` jumps the player into the final large-convoy defense test state and arms the convoy for the current day/night cycle instead of the normal two-night story wait.

`The Empty Caravan` can be prepared together with the new route network through `/vq admin routes testsetup [player]` below.

### Village Projects

- `/vq admin project show [player]`
- `/vq admin project unlock <player> <project>`
- `/vq admin project lock <player> <project>`

Current unlockable project ids:

- `apiary_charter`
- `forge_charter`
- `market_charter`
- `pasture_charter`
- `watch_bell`
- `caravan_yard`
- `wayshrine_network`

### Trade Routes

- `/vq admin routes testsetup [player]`
- `/vq admin routes testevent <1-5> <clear|broken_wheel|injured_pack_animal|washed_out_bridge|false_distress|hungry_travelers|road_toll|missing_courier|storm_camp>`
- `/vq admin routes reset [player]`

`testsetup` is the one-command core test for the Village Quest 2.0 caravan systems. It unlocks the `Caravan Yard`, gives the ledger and a Wayfinder if needed, completes the older story prerequisites, creates five surveyed routes with different security and quality values, adds all route specializations, multiple upgrade states, rank-five guild progress and `300 Silvermarks`, starts two event scenarios, prepares `The Empty Caravan` to be offered by the Questmaster, and opens the route map.

`testevent` replaces the selected route's current incident with one exact event, or clears it. This is the focused QA path for material delivery, courier, ambush, and timed proximity behavior without waiting for a random midpoint roll.

`reset` clears the selected player's registered villages, route progress, route events, earnings, and materialized route NPCs. It does not remove world blocks or revoke unrelated progression.

### Prosperity and Economy

- `/vq admin economy testsetup [player]`

This focused `2.1.0` fixture unlocks the five matching project branches, prepares the five-route test network, sets the wallet to `500 Crowns`, and opens `Prosperity & Prestige`. It is intended for rank-price, discount, commission, service, livery, collection, localization, and persistence QA.

### Interface preview

Operators can use `/vq admin uitest` for the interface tour. The first twelve pages show the new Friends NPC windows: Simon, Lutte and Marius quests, herb exchange, coastal-chart actions, raid preparation, completed dialogue and Raphael lore. These isolated previews cannot accept quests, consume items or grant rewards. Its sample screens
do not grant quest progress or execute gameplay actions. Escape exits.

### Pilgrim

- `/vq admin pilgrim spawn [player]`
- `/vq admin pilgrim despawn`
- `/vq admin pilgrim rumor unlock [player]`
- `/vq admin pilgrim rumor lock [player]`

### Wallet

- `/vq admin wallet show [player]`
- `/vq admin wallet add <player> <amount> [silvermark|crown]`
- `/vq admin wallet remove <player> <amount> [silvermark|crown]`
- `/vq admin wallet set <player> <amount> [silvermark|crown]`

### Reputation

- `/vq admin reputation show [player]`
- `/vq admin reputation add <player> <track> <amount>`
- `/vq admin reputation set <player> <track> <amount>`

Tracks:

- `farming`
- `crafting`
- `animals`
- `trade`
- `monster_hunting`

Note:
- the player-facing flavor now calls the last lane `Roadside Watch`
- the underlying admin track id still uses `monster_hunting`

### Shard Cache

- `/vq admin shardcache [player]`
- `/vq admin shardcachetp [player]`

## Social reputation admin tools (2.5)

Operator permission is required. These affect personal NPC trust, not player-guild prestige or skill expertise.

- `/vq admin reputation inspect <player>` — read values, case identity, probation and schema mutability.
- `/vq admin reputation set guild <player> <value>` — clamp−100..100, record an administrative history entry.
- `/vq admin reputation set village <player> <dimension> <x> <z> <value>` — use the saved physical village anchor; does not discover a village or create a route.
- `/vq admin reputation pardon <player>` — close the personal case/reconciliation, raise affected values to at least−10 and apply two-deed probation. No revival, money or freight reward.
- `/vq admin reputation uitest <neutral|reliable|respected|blocked|probation|major>` — open a synthetic personal view without saved state or inventory changes. Preview action buttons are inactive.

For actual aid, interact with a valid own/affected board or existing Questmaster within8 blocks. A remote Journal view cannot consume materials. Unknown/future schema data cannot be silently overwritten by set/pardon.

Village Quest 2.5 adds [chosen Vanilla reward packages and saved collection](vanilla-rewards.md), including `/vq rewards claim`. Existing accepted contracts and prepaid orders retain their original conditions.

## Reward collection after testing

The temporary `/vq admin rewardtest` command and all its subcommands were removed before the 2.5 release for Minecraft26.3 and26.2. Use the Questmaster or `/vq rewards claim` for normal earned rewards. Existing saved rewards, inventories, wallets and completion receipts are retained. The permanent `/vq admin uitest` visual tour remains available.

## Living Kingdoms 2.6

These commands are available on Minecraft 26.3. Ordinary player commands
still require actual story, location, incident and village permissions. Commands
never create earned tools, mandates or completed services.

| Command | Requirements and effect |
|---|---|
| `/vq artifacts target` | Select/reselect the artifact scene's village at one fresh local permitted council. Use a destination on a real direct route for the scene. Earned chapters stay; the current scene needs new actions. |
| `/vq artifacts reserve` | Choose the finale's reserve charter at the selected permitted council. After completion only revises the charter/epilogue, with no repeated reward. |
| `/vq artifacts road` | The equivalent open-road charter choice, with the same entry and no-repeat-reward rules. |
| `/vq aid help <incident>` | Eligible owner at a real rescuable loaded ambush, using its offered UUID: record explicit willingness to help; actual rescue remains required. |
| `/vq aid refuse <incident>` | Same real incident/eligibility: record explicit refusal. Silence and elapsed time are neutral. |
| `/vq aid repair` | Affected player beside the disappointed leader at a freshly confirmed council: begin apology/freight repair; new matching delivery is still required. |

These commands need no OP status, but all server-side story/actor/mandate checks
still apply. See [Stories and tools](kingdom-stories-and-tools.md) and
[Diplomacy and crises](kingdom-diplomacy.md).

### Current capacity limits

Market UI admits at most **32 simultaneous sessions**, each expiring after 1,200
server ticks (about 60 seconds at normal tick rate). Place searches return at most
**32 candidates**; assign suitable places and recheck when rearranging. Trade
always uses one displayed lot. Reopen an expired market to request a fresh session.

### Quest tracker side

- `/vq questtracker left`: move to the left and save locally.
- `/vq questtracker right`: move to the right and save locally.

The configured top/bottom anchor is retained; tracking remains enabled or disabled as before. `/villagequest` accepts the same subcommands.

## NPC skin gallery — 2.6

Operators can run `/vq skinpreview` in a clear open area. It checks an already
loaded empty strip above/beside the player, builds a smooth-stone platform and
places all 42 owned human skin variants in one east–west row. The player is moved
onto the viewing walkway. Preview bodies have no AI, gravity, damage, trade or
quest progress; they remain still after unfreezing. The whole server world is
frozen with Vanilla `tick freeze` after successful creation.

- `/tick unfreeze` resumes world ticks (operator permission required).
- `/vq skinpreview clear` removes only your loaded preview figures within 128 blocks.
  The platform remains and may be dismantled manually.

The gallery does not create real village staff, generate village records or grant
story evidence. It does not overwrite terrain, structures or occupied space and
does not load chunks. If the strip is blocked or unloaded, nothing is placed.
Use a test world; real villagers, players and all other world ticks are frozen too.

Placement uses actual Vanilla/CTOV house and jobsite footprints: the Notice Post stays within 10 horizontal blocks of a real building; distant roads and the combined village bounds do not count. Automatic placement keeps the entire footprint at least 8 horizontal blocks from every nearby player and waits for a safe loaded site if necessary. Generated posts drop no item, so repeated retrofit cannot farm them; crafted posts keep their ordinary drops, including crafted replacements at an old generated site. Recovery restores the compact board rather than replenishing decorative materials.
