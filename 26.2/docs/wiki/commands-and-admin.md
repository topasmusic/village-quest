# Commands and Admin Tools

All commands are available as `/villagequest ...` and the short alias `/vq ...`.

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

- `/vq routes guild`
  Show trade-guild rank, score, daily income, escrow, route specialization, road length, quality, and solved incidents.

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

- this surface is only active on dedicated multiplayer servers
- the `Questmaster` party drawer is intentionally hidden on singleplayer and integrated worlds
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

`/vq admin story shadows testrescue` jumps the player into the regular caravan-defense test state.

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

- `/vq admin uitest questmaster`
- `/vq admin uitest pilgrim`
- `/vq admin uitest wayshrine [owner|guest]`
- `/vq admin uitest noticeboard [known|trusted|allied]`

These admin-only commands open the real Questmaster dashboard, Pilgrim trader payload, or non-persistent Wayshrine and Guild Notice Board previews without requiring precise NPC or block interaction. They use current player progression and currency where applicable, making them suitable for checking translations, long descriptions, large wallet values, GUI scaling, and reusable component alignment in a real client. The Wayshrine preview defaults to `owner`, displays a compact five-shrine network with mixed Known/Trusted/Allied terms, charges, and ready travel controls, and offers a separate doubled-price `guest` view. Its synthetic indices are outside every real network, so travel and rename actions cannot alter game state. The Notice Board defaults to `known`; its three explicit states exercise incomplete and complete delivery progress plus every bond-path endpoint without changing quest or world state. For the Questmaster, inspect all four icon tabs, list scrolling, the selected quest detail card, locked/active/claimable states, simultaneous action buttons, reset timers, and the party overlay on a multiplayer client.

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
