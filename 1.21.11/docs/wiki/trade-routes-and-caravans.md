# Trade Routes and Caravans

This page describes the `2.0.0 - Roads Between Villages` trade-route and Trade-Guild system shared by all three maintained lines. The `26.2` implementation remains the native-client visual/gameplay reference; this line carries the deliberate Yarn/API port and its collision-bound spawn fix.

## Unlocking the Network

Completing `Market Road Troubles` now unlocks the `Market Charter`, the ledger map, and one provisional route. This gives builders a useful preview while the wider road story is still in progress. Provisional routes:

When an existing save already has the `Market Charter` or `Caravan Yard`, the first login after updating grants a missing `Caravan Ledger` automatically. A persistent claim marker and an inventory check make this a one-time compatibility backfill rather than a recurring item source.

- have a capacity of one route
- use smaller two-merchant physical caravans
- can earn up to `8 Silvermarks` per Minecraft day
- do not roll recurring route incidents

Complete the six chapters of `The Empty Caravan` to upgrade that preview into the full network. Its finale unlocks:

- the permanent `Caravan Yard` village project
- the `Caravan Ledger` special item
- registration of villages for a personal trade network
- up to `5` persistent trade routes

The route network is player-owned. Story and route events remain solo content and are not shared through quest parties.

## Registering Villages

Stand inside or close to a generated village, then either:

- sneak-use the `Caravan Ledger`
- run `/vq routes register`

The first registered village becomes the home hub. Further registrations create routes from the hub until all five route slots are occupied. Duplicate villages are ignored.

Normal use of the ledger, or `/vq routes`, opens the route map.

## The Route Map

The parchment-style map provides a management overview rather than copying the terrain map. It displays:

- registered village nodes and connecting route lines
- a moving caravan marker for each active route
- current travel direction and whether the physical caravan is nearby
- security state: `Unknown`, `Dangerous`, `Secured`, or `Flourishing`
- road quality and accumulated earnings
- current route emergency and its objective
- pause/resume, survey, cancel, and remove controls for each route
- every installed bend in a surveyed route
- current trade-guild rank and today's network income in the header
- each route's specialization and installed upgrades in its tooltip

The map updates while open. Closing it does not stop route simulation. Removing a route requires two clicks on the map within the generous `30 second` confirmation window to reduce accidental deletion; `/vq routes remove <1-5>` is the direct command equivalent. Later routes are renumbered into the open slot, and the removed destination can be registered again later.

## Surveying a Real Road

Every route starts as a direct hub-to-village connection, but it can store up to `48` intermediate waypoints. The home hub and destination village are implicit endpoints and do not need to be marked.

Recommended workflow:

1. Open the ledger, select a route, and press `Survey`, or run `/vq routes survey start <1-5>`.
2. Walk the road from the home village toward the destination.
3. At each meaningful bend, bridge, pass, or junction, sneak-use the `Caravan Ledger`. `/vq routes survey mark` is equivalent.
4. Open the ledger normally and press `Install`, or run `/vq routes survey finish`.

Points must be at least `4` blocks apart. Marking every block is unnecessary: use enough anchors to describe the actual shape of the road. While a draft is open, its caravan is paused and the previous installed path remains safe. `Cancel` or `/vq routes survey cancel` discards the draft and restores the previous pause state.

Installing a survey persists the polyline across restarts and resets its learned road quality to the neutral starting value so the new corridor is measured honestly. The same stored geometry drives:

- travel distance and arrival timing
- the line and moving marker on the ledger map
- abstract caravan and route-event positions
- visible caravan navigation targets
- road and lighting quality sampling

A long but well-built detour therefore works as a real route. Travel time and payout both use the full surveyed length, so a sensible detour is recognized and compensated without turning an artificially enormous route into unlimited income.

## Visible Caravans

Each active route represents a named group of three caravan merchants. The route continues to progress while its chunks are unloaded, without permanently loading them.

When a player comes near the simulated route position:

- the merchants materialize as real NPCs
- they walk toward the next point on the route
- navigation favors nearby road-like blocks
- they return safely to the abstract simulation when no player is close enough to observe them

This makes roads between villages visible and useful without leaving hundreds of permanent entities across the world.

## Building Better Roads

The route periodically samples the area around the caravan. Useful surfaces include:

- dirt paths
- gravel
- cobblestone
- stone bricks
- wooden planks and slabs

Lighting also helps. A continuous, lit road raises route quality over time. Higher quality means:

- faster journeys
- better delivery earnings
- a clearer sense that the player's infrastructure changed the world

The system intentionally scores what exists in the world; it does not place or remove road blocks by itself.

### How the Installed Line and Nearby Blocks Work Together

The installed waypoint polyline is the authoritative route. While a physical group is visible, its next navigation target can still snap to a road-like surface up to `8` blocks from the current surveyed segment. Road quality is sampled in a `9 x 9` area around the caravan's current abstract position on that same line.

This keeps villagers from walking rigidly through the exact center of every marked coordinate while preventing them from jumping to an unrelated village path far away. If a large detour is not surveyed, it is not part of the route; after it is surveyed, the map, virtual caravan, visible merchants, events, and quality checks all recognize it.

## Recurring Route Events

Only the full Caravan Yard network rolls recurring incidents. A player network can have at most one active incident at a time, and its first incident is a guaranteed tutorial. Later incident chance falls with road quality, flourishing security, lantern crews, weather covers, and escorts. Merely starting an incident no longer marks the route dangerous.

Routes can stop for one of eight situations:

| Event | Core response |
|---|---|
| `Broken Wheel` | bring planks and iron to the caravan |
| `Injured Pack Animal` | bring hay and wheat |
| `Washed-out Bridge` | bring planks for repairs |
| `False Distress` | investigate and defeat the ambushers |
| `Hungry Travelers` | deliver bread |
| `Road Toll` | pay from the Village Quest wallet |
| `Missing Courier` | find and speak to the courier |
| `Storm Camp` | remain close while the caravan weathers the storm |

The `Wayfinder's Compass` automatically selects the live route-event target when an emergency begins. Successful rescues improve route security; ignored events eventually expire and keep the road less reliable.

## Earnings and Progression

Deliveries add route earnings. The amount depends on:

- the current security state
- road quality
- the complete installed path length

Ordinary delivery income is capped across the entire network at `8 Silvermarks` per Minecraft day for the provisional network and `60 Silvermarks` for the full Caravan Yard. When the owner is offline, earnings enter a capped trade-office escrow instead of silently filling the wallet. Opening or using the ledger collects it.

This is deliberately a long-term infrastructure loop. A route begins uncertain, becomes safer through play, and can eventually flourish if the player maintains it.

## Village Trade Guild

`/vq routes guild` shows the five-rank guild progression. Its score comes from registered routes, sampled road quality, resolved incidents, and completed freight contracts. Rank unlocks stronger contracts and later route investments; it does not provide an infinitely scaling raw-stat bonus.

`/vq routes contracts` shows up to three deterministic daily offers. The workflow is:

1. `/vq routes contracts accept <offer 1-3> <route 1-5>`
2. Carry the listed cargo and run `/vq routes contracts supply`.
3. Keep the assigned route active. Its next arrival within three real-world reset days completes the order.

Contract payout increases modestly for long installed roads. A matching specialization adds `25%`; the final `Trade Office` upgrade adds another `25%`. Freight completion and its separate payout still occur when ordinary route income has already reached the network's daily cap.

Routes can specialize as `general`, `provisions`, `forge`, `livestock`, `courier`, or `guarded` through `/vq routes specialize <route> <type>`. The first choice is free; later refits cost `15 Silvermarks`.

Permanent upgrades are purchased with `/vq routes upgrade <route> <upgrade>`:

| Upgrade | Cost | Guild rank | Effect |
|---|---:|---:|---|
| `wheels` | 25 SM | 1 | `8%` faster travel |
| `lanterns` | 30 SM | 2 | fewer incidents |
| `covers` | 35 SM | 2 | fewer weather/road delays overall |
| `escorts` | 45 SM | 3 | substantially fewer incidents |
| `insurance` | 50 SM | 4 | a failed incident no longer removes a past success |
| `office` | 60 SM | 5 | `25%` higher freight-contract payout |

## Roadwarden Horn

Reaching `200` Monster Hunting reputation awards the functional `Roadwarden Horn`. Its first use per real day posts one road-watch charge, which prevents the next non-tutorial route incident. Further uses point the Wayfinder toward a current incident or report that the roads are quiet.

## In-game Core Test

Run:

```mcfunction
/vq admin routes testsetup
```

The command prepares the current player with:

- the `Caravan Yard`, `Caravan Ledger`, and a `Wayfinder's Compass` if needed
- five surveyed test routes with different shapes, qualities, and security states
- all five specializations, several visible upgrade states, rank-five guild progress, and `300 Silvermarks` for investment testing
- a `Broken Wheel` event on route 1
- a `False Distress` event on route 3
- the prerequisites needed to start `The Empty Caravan` at the Questmaster
- an opened route map

Suggested test pass:

1. Inspect all five routes, their waypoint lines, and their moving markers on the map.
2. Walk toward a caravan marker and confirm the three merchants materialize and travel.
3. Build path, gravel, stone-brick, or plank road segments within the route corridor and watch road quality change over subsequent samples.
4. Bring `8` planks and `2` iron ingots to the broken-wheel caravan, then interact with a merchant.
5. Investigate the false distress call, interact with its caravan, and defeat the spawned traitors.
6. Use `/vq questmaster` to begin the newly unlocked story test path.
7. Start a survey on a route, mark at least two bends with sneak-use, install it, and confirm the saved line changes on the map.
8. Remove one route through the map's two-click confirmation and confirm the later routes shift into the open slot.
9. Run `/vq routes guild`, inspect the route tooltip specializations/upgrades, and buy an unowned upgrade.
10. Run `/vq routes contracts`, accept a listed offer, provide its cargo, run `contracts supply`, and wait for the assigned route arrival.

Clean up the generated route state with:

```mcfunction
/vq admin routes reset
```

The ordinary complete data reset also removes route runtime entities, but it is broader and should only be used when a full mod reset is intended.
