# Commands and Admin Tools

All commands are available as `/villagequest ...` and the short alias `/vq ...`.

In Village Quest 2.5, messages about features that still need a project, rank or story unlock use gray text. This includes the Journal's Map and Prosperity actions and locked services or Shop offers.

## Player Commands

### Core Commands

- `/vq questmaster`
  Summon the `Questmaster` near you.

- `/vq journal`
  Open or close the journal.

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
  Request a Guild Corner in the generated Overworld village around you when automatic placement was skipped. The complete site must be loaded and safe; existing buildings, block entities and protected terrain are not overwritten. Already generated or player-removed corners are not recreated. In Village Quest 2.5 on both maintained lines, both bundled presets pass Minecraft's current template validation.

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
  Replace today's unaccepted daily quest once. Three combined post-200 Mastery levels grant a second reroll.

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

- `/vq party share story accept`
  Accept the currently pending shared story-chapter join offer for a running party story.

- `/vq party share story decline`
  Decline the currently pending shared story-chapter join offer.

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

### UI Layout Tests

Village Quest 2.5: `/vq admin uitest` starts a permanent, admin-only guided UI tour. The former individual `uitest` subcommands have been replaced.

Click **Next** to move through all 43 steps, **Back** to revisit a screen, and **Finish** or **Exit** to end the tour. Esc also exits. A separate footer shows the current screen and state. Tabs, lists, maps and local selection can still be inspected; quest, purchase, delivery, travel, rename and other server actions are suppressed during previews.

The tour uses the actual UI classes at their normal responsive size with fixed sample data, without spawning NPCs or modifying progression, inventory, currency or tutorial preferences. It covers Journal pages, available/active/locked Quest Board layouts, Trust and aid tabs, Pilgrim goods/contracts, Prosperity sections, three Notice Board bond tiers, every former Notice Journey preview, Regional Dispatches, Caravan travel/cargo/incident/paused states, owner/guest Wayshrines, the route map and Guild Path.

Tour goods use production shop prices. The Pilgrim example is the actual Hedgerow contract with its production reward, and Daily entries show their own reward tier. The former fictional “32 wheat for 3 Crowns” example was not a playable contract and is no longer shown. Dispatch samples use the real eight-item shipment and current reward formula.

Caravan Master, Regional Dispatch and village journey pages now use compact authored layouts with about 30% less area in both gameplay and the tour. Journey primary actions use teal in the common footer; mutually exclusive story choices have equal buttons above the centered navigation row. The standalone Guild Path uses the transparent Atlas frame; the Journal's Guild Atlas layout is unchanged.

Prosperity tour pages include all five working local tabs (Upgrades, Commissions, Services, Collection and Statistics); each tour step starts on its matching section. Teal primary actions now show warm hover feedback. Wayshrine destination lists show four taller rows and scroll when more destinations are available.

Check EN/DE/ES labels, long descriptions, full names, selection accents, scrollbars and footer spacing at different GUI scales. Real gameplay and multiplayer party actions need separate testing outside the tour.

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

## Useful Testing Patterns

### Force a new daily

```mcfunction
/vq admin resetdaily
/vq admin nextdaily
/vq questmaster
```

### Force a new weekly

```mcfunction
/vq admin resetweekly
/vq admin nextweekly
/vq questmaster
```

### Reset the full Village Quest server state

```mcfunction
/vq admin reset complete
```

### Unlock all visible story-core projects quickly

```mcfunction
/vq admin project unlock @s apiary_charter
/vq admin project unlock @s forge_charter
/vq admin project unlock @s market_charter
/vq admin project unlock @s pasture_charter
/vq admin project unlock @s watch_bell
```

### Spawn the Pilgrim with rumor access

```mcfunction
/vq admin pilgrim rumor unlock
/vq admin pilgrim spawn
```

### Prepare the late trade-road story for testing

```mcfunction
/vq admin story shadows unlock
/vq admin story shadows testrescue
```

Use `/vq admin story shadows testfinal` when you specifically want the chapter `6` convoy-defense setup.

### Test the complete caravan batch

```mcfunction
/vq admin routes testsetup
```

Then:

1. Inspect all five surveyed route lines, moving markers, security states, road quality values, and event summaries.
2. Approach a marker and verify that three named merchants materialize and start traveling.
3. Resolve route 1 by carrying `8` planks and `2` iron ingots and interacting with its caravan.
4. Trigger route 3 by interacting with the false-distress caravan, then defeat its traitors.
5. Build suitable lit road surfaces near a route and allow several samples for quality to react.
6. Run `/vq questmaster` to start the prepared `The Empty Caravan` story path.

Clean up only the route test data with:

```mcfunction
/vq admin routes reset
```

### Test The Shrines Between Roads

```mcfunction
/vq admin shrines testsetup
```

This creates the five-route test network, clears every seeded route incident, marks the Caravan Ledger as Lens-upgraded, and supplies the Wayfarer's Sigil, three Guild Wayshrines, three Guild Notice Posts, Emberglass Lanterns, Guild Milestones, and the optional Courier's Satchel. Sneak-use the Ledger in real connected villages, complete requests through the Notice Board UI, then bind one shrine at the Homestead and further shrines in any recorded village. Known villages are intentionally available immediately but use the expensive two-charge/ten-minute tier. Sneak-use the Sigil to inspect shrine bindings, milestones, and route distance. Once bound, the active network opens without holding a Sigil; a second player can use it directly with the guest price multiplier.

## Notes for Testers

- `Story` and `Special` start visible but greyed out.
- `Story` unlocks after the first normal daily completion.
- after a full `4`-chapter story arc, the next story appears only after a real `1 hour` cooldown and the `Story` tab shows a live timer during that pause
- `Special` unlocks after the first real reputation gain.
- `Weekly` cancel now exists and does not reroll a new weekly for the same week.

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
