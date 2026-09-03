# Trade Routes and Caravans

This page describes the `2.0.0 - Roads Between Villages` trade-route and Trade-Guild system now shared by all three maintained lines. This `26.2` implementation remains the native-client visual and gameplay reference.

## Unlocking the Network

Completing `Market Road Troubles` now unlocks the `Market Charter`, the ledger map, and one provisional route. This gives builders a useful preview while the wider road story is still in progress. Provisional routes:

When an existing save already has the `Market Charter` or `Caravan Yard`, the first login after updating grants a missing `Caravan Ledger` automatically. A persistent claim marker and an inventory check make this a one-time compatibility backfill rather than a recurring item source.

- have a capacity of one route
- use smaller two-merchant physical caravans
- can earn up to `8 Silvermarks` per Minecraft day
- do not roll recurring route incidents
- may already use a deliberately confirmed player base as their home node

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

After a personal Homestead has been confirmed, one sneak-use at the destination is enough to register a route; it does not need the Homestead's two-step confirmation. Village detection covers the complete horizontal structure footprint plus a small edge margin, including paths and gaps between individual buildings. If the ledger instead offers to confirm or move the Homestead, Minecraft did not report a supported generated village structure at that position. Move farther into the settlement and try again; a modded settlement also needs to publish its structures through the standard `#minecraft:village` tag.

Registration checks both sides of the destination: the player must be inside a real generated village structure, and that village must still contain at least one living normal villager. Naturally abandoned/zombie villages and villages whose population has died are rejected instead of becoming misleading trade nodes. The admin test network deliberately bypasses this rule because its destinations are synthetic QA fixtures.

`ChoiceTheorem's Overhauled Village` (`CTOV`) is supported without becoming a required dependency. CTOV publishes its small, medium, and large settlements through the standard `#minecraft:village` structure tag that Village Quest already consumes. The population scan follows the detected structure's complete bounding footprint plus a small edge margin, so a villager placed in the outer district of a large CTOV settlement still proves that village is inhabited. Empty and zombie CTOV variants remain ineligible.

Normal use of the ledger, or `/vq routes`, opens the route map.

### Using a Player Base as the Home Node

The `Market Charter` no longer forces the provisional first node to be inside a generated village. As soon as it grants the Caravan Ledger, stand on solid, dry Overworld ground at your base and run `/vq routes yard`, or sneak-use the ledger while no generated village can be registered. Repeat the action within `30 seconds` to confirm the exact place. The map marks it as a distinct `Homestead Trade Post` rather than pretending that the base is a village. No second unlock quest is required; the later `Caravan Yard` still matters because it expands the network from one route to five and enables incidents.

This deliberately changes only the home node. Every route destination must still be a real generated, inhabited vanilla or CTOV village. Zombie villages, abandoned villages, arbitrary coordinates, and test markers cannot become trade partners. A network with installed routes must remove them before relocating its home, preventing existing paths and caravans from silently pointing at a different origin.

## The Route Map

The Caravan Ledger now combines its management overview with a real top-down view of the world. It samples only chunks the client already has loaded and never requests or force-loads terrain. Terrain samples are aligned to stable world coordinates, so panning the viewport no longer subtly rerolls colors or decorations. Explored tiles persist per world/server, dimension, and quality level under `.minecraft/village-quest/map-cache`; fresh loaded terrain safely updates old samples. It displays:

- registered village nodes and connecting route lines
- a moving caravan marker for each active route
- the player's current position
- current travel direction and whether the physical caravan is nearby
- security state: `Unknown`, `Dangerous`, `Secured`, or `Flourishing`
- road quality and accumulated earnings
- current route emergency and its objective
- pause/resume, survey, cancel, and remove controls for each route
- every installed bend in a surveyed route
- ocean ferry legs as dashed blue lanes, with a dedicated boat marker and remaining crossing time
- current trade-guild rank and today's network income in the header
- each route's specialization and installed upgrades in its tooltip

Hovering a village shows its name and coordinates. Hovering a caravan shows its route, current coordinates, physical-nearby/background-simulation state, and incident. Player, village, caravan, and incident markers use separate shapes and priorities; if the player overlaps the Caravan Yard, the tooltip combines both locations instead of hiding one of them.

The map updates while open. Closing it does not stop route simulation. Removing a route requires two clicks on the map within the generous `30 second` confirmation window to reduce accidental deletion; `/vq routes remove <1-5>` is the direct command equivalent. Materialized traders and incident attackers are discarded as part of that removal, including tagged orphans when an unloaded chunk is encountered later, so repeated route replacement cannot accumulate old caravans. Later routes are renumbered into the open slot, cleanly rematerialize when observed, and the removed destination can be registered again later.

Each route row has a small running or paused indicator with a focused tooltip. Pausing is a true physical stop: that route's currently materialized merchants and incident attackers despawn immediately, while its persistent route data remains available for a later resume. The paused route is swept again on every service tick, and global cleanup keeps only entities that belong to the current runtime group, so stale mapped duplicates cannot accumulate after repeated materialization. A fresh five-route client run verified that pausing all five leaves no loaded caravan merchants. Long route hover details wrap into multiple readable lines instead of extending beyond the screen. The three navigation tabs are grouped clear of the upper-left brass corner, while the Route Office guild/day summary sits on the lower right below its divider.

Use the ledger's `Rename` action or `/vq routes rename <1-5> <name>` to give a destination a persistent player-defined label such as `Home`, `Harbor`, or `North Market`. Names are limited to `24` clean characters, survive save/reload, move with their route when an earlier slot is removed, and appear on map nodes and route details.

The five route identities are deliberately consistent across the world and both maps: route `1` is burgundy, route `2` forest green, route `3` blue, route `4` ochre, and route `5` violet. Every merchant in one physical caravan wears that route's outfit, while the full-map line, minimap line, caravan marker, and management accent use the matching color.

Hold the left mouse button over the terrain and drag to move the map. The mouse wheel and brass `+`/`-` controls keep their existing zoom behavior, and the focus control returns to the player's position without discarding the selected zoom. The extended zoom-out range can display several thousand blocks at once, so a remote destination can remain visible alongside a lone Homestead before the route is registered. Terrain is baked into one cached client texture rather than rebuilt from thousands of rectangles every frame. While the mouse is held, the existing texture moves immediately with the route and entity overlays; the newly exposed terrain is sampled once when the button is released. This keeps navigation responsive without loading additional chunks.

## Live Corner Minimap

Press `,` to toggle a compact real-time view. The key lives under `Options -> Controls -> Key Binds -> Village Quest` and can be rebound like any vanilla control. `/vq routes minimap` provides the same toggle for players who prefer commands. The tracker uses `.` in that same category.

The minimap uses the same terrain renderer and installed route geometry in a navigation-focused form:

- recognizable loaded/previously surveyed terrain, including water, forests, clearings, and built surfaces
- home and registered village nodes
- the player's cyan position marker
- moving caravan markers, including whether a physical group is currently close enough to see
- incident emphasis for a route that needs help
- a compact route/nearby-caravan count

The cyan compass-star is always your live position, including before the network has a registered home node. On narrow HUD layouts the footer prioritizes the current X/Z coordinates over route statistics, so the navigation reference remains readable.

It updates once per second without pausing the game and automatically stays out of the way while the full ledger map is open. It is a surface-navigation aid, not a cave or entity radar: its job is to make the world, player, villages, and caravans readable in relation to one another while respecting normal client chunk loading.

## Surveying a Real Road

Every route starts as a direct hub-to-village connection, but it can store up to `48` intermediate waypoints. The home hub and destination village are implicit endpoints and do not need to be marked.

Recommended workflow:

1. Open the ledger, select a route, and press `Survey`, or run `/vq routes survey start <1-5>`.
2. Walk the road from the home village toward the destination.
3. At each meaningful bend, bridge, pass, or junction, sneak-use the `Caravan Ledger`. `/vq routes survey mark` is equivalent.
4. Open the ledger normally and press `Install`, or run `/vq routes survey finish`.

Points must be at least `4` blocks apart. Marking every block is unnecessary: use enough anchors to describe the actual shape of the road. While a draft is open, its caravan is paused and the previous installed path remains safe. `Cancel` or `/vq routes survey cancel` discards the draft and restores the previous pause state.

### Surveying an Ocean Ferry

Ferries are deliberately limited to biomes in Minecraft's `#minecraft:is_ocean` tag, including ocean variants such as deep and frozen oceans. A normal lake or river never becomes a ferry route. Walk around inland water or build a usable bridge and survey over that solid surface.

For an island route, mark a normal land waypoint on broad, safe ground at the departure shore, travel onto the ocean in a boat, mark one or more ocean waypoints, and mark another normal waypoint after reaching the destination shore. Installing the survey turns the last safe land point before each sea leg into its ferry dock and the connected ocean legs into a dashed blue sea lane. The map and minimap display the ferry boat at the caravan's virtual position and the full map shows the remaining crossing time.

Physical traders do not attempt Minecraft pathfinding across open water. If their group is already materialized and a player is observing the approach, persistent route progress pauses at the dry dock while the leader reaches it and every follower regroups nearby. Only then does the group transfer into the virtual crossing. If nobody is observing or the dock chunk is not loaded, the background simulation crosses immediately and never force-loads the shore. A blocked observed group keeps using the normal recovery rules; moving away allows an off-screen recovery or clean fallback instead of permanently freezing the route.

After boarding, the traders continue to advance in the existing unloaded-chunk simulation and can materialize again only after the route reaches an ordinary land segment. The boat is a navigational representation rather than a permanently spawned entity, avoiding abandoned boats, duplicate traders, and chunk-loading pressure. Route incidents are not rolled during a crossing. Hovering the boat at its dock reports that boarding is in progress.

Installing a survey persists the polyline across restarts and resets its learned road quality to the neutral starting value so the new corridor is measured honestly. The same stored geometry drives:

- travel distance and arrival timing
- the line and moving marker on the ledger map
- abstract caravan and route-event positions
- visible caravan navigation targets
- road and lighting quality sampling

A long but well-built detour therefore works as a real route. Travel time and payout both use the full surveyed length, so a sensible detour is recognized and compensated without turning an artificially enormous route into unlimited income.

## Visible Caravans

World owners can choose `FULL`, `REDUCED`, or `MAP_ONLY` in `config/village-quest/server.properties`. Full mode preserves the ordinary group, Reduced materializes one route trader, and Map Only keeps route movement, income, escrow, guild progress, and map markers without spawning traders. Map Only also suppresses new physical incidents so it never creates an objective the configured world cannot resolve.

Each active route represents a named group of three caravan merchants. The route continues to progress while its chunks are unloaded, without permanently loading them.

The three merchants choose from four stable medieval outfit variants derived from their UUIDs. Their appearance therefore stays consistent while a group is visible without requiring new entity data or save migration.

When a player comes near the simulated route position:

- the merchants materialize as real NPCs
- they walk toward the next point on the route
- navigation favors nearby road-like blocks
- they return safely to the abstract simulation when no player is close enough to observe them

Physical placement rejects fluids, leaves as footing, powder snow, magma, cactus, campfires, and narrow isolated ledges. Navigation also gives leaves and powder snow an explicit negative path cost. Followers keep a wider formation, separate themselves if an event stop compresses them into the same block, and regroup around the lead merchant if terrain splits the party. If the leader cannot make meaningful progress, the group searches forward and backward along the installed route for a stable recovery point; after repeated failures it dematerializes briefly while the persistent journey continues. A player therefore never has to dig an ordinary caravan out of a ravine to keep the economy alive.

An incident is different from a pathfinding stall: its lead merchant carries a visible warning and event name, and the player receives a concrete objective. Event groups deliberately camp near the incident until that objective is resolved or expires.

This makes roads between villages visible and useful without leaving hundreds of permanent entities across the world.

The older `Shadows on the Trade Road` rescue encounters use the same safety philosophy but remain combat encounters: they search a bounded site roughly `320–500` blocks from the player, require dry safe positions for the complete spaced merchant formation before committing the compass target, and pull a survivor back to a safe position if combat knocks them far below or outside the encounter area. A final spawn failure relocates the encounter nearby or visibly reschedules it. Peaceful still spawns the merchants and permits the full escort/completion path, but creates no hostile waves.

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

Each route also exposes a deliberate response plan through `/vq routes approach <route> <careful|bold>` and its Ledger detail. `careful` reduces frequency and reward, reduces destination-village strain by 40%, and grants an extra protected journey after an incident. `bold` increases incident opportunity and reward by 25%, with 25% more repairable destination strain on failure. Adventure Profile adds its own protection and tolerance. A story-required incident is never blocked by these normal safety rules.

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

Route incidents are personal solo content. Another player can see the physical group but cannot claim the owner's event reward or consume the resolution materials for them.

## Earnings and Progression

Deliveries add route earnings. The amount depends on:

- the current security state
- road quality
- the complete installed path length

Ordinary delivery income is capped across the entire network at `8 Silvermarks` per Minecraft day for the provisional network and `60 Silvermarks` for the full Caravan Yard. When the owner is offline, earnings enter a capped trade-office escrow instead of silently filling the wallet. Opening or using the ledger collects it.

This is deliberately a long-term infrastructure loop. A route begins uncertain, becomes safer through play, and can eventually flourish if the player maintains it.

## Village Trade Guild

`/vq routes guild` shows the five-rank guild progression. Its score comes from registered routes, sampled road quality, resolved incidents, and completed freight contracts. Rank unlocks stronger contracts and later route investments; it does not provide an infinitely scaling raw-stat bonus.

`/vq routes contracts` shows up to three deterministic daily offers. Recorded destination needs are prioritized, and each matching offer lists the route numbers whose village currently needs that item. A different valid route remains general supply rather than becoming unusable. The workflow is:

1. `/vq routes contracts accept <offer 1-3> <route 1-5>`
2. Carry the listed cargo and run `/vq routes contracts supply`.
3. Keep the assigned route active. Its next arrival within three real-world reset days completes the order.

Contract payout increases modestly for long installed roads. A matching specialization adds `25%`; the final `Trade Office` upgrade adds another `25%`. Freight completion and its separate payout still occur when ordinary route income has already reached the network's daily cap.

Every route arrival now also helps a recorded destination village and advances that village's Wayshrine-energy meter. Matching supplied freight has the stronger effect. Three energy steps generate a shared charge at a bound destination shrine, while failed incidents create bounded supply strain with a hard repair floor instead of destroying trust or story progress.

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

## Prosperity Services and Liveries

The `2.1.0` economy adds route-targeted Road Patrol, Survey Report, and Emergency Recall services. Forge Prosperity discounts permanent route-upgrade prices, while Road Watch Prosperity reduces recurring incident chance.

Five collectible liveries can be assigned independently to installed routes. Every merchant in that caravan, the full-map route, and the minimap route use the selected color. See [Prosperity and the Village Economy](./prosperity-and-economy.md) for prices and unlock rules.

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

1. Inspect all five routes, their waypoint lines, player marker, village/caravan hover details, and moving markers on the map.
2. Toggle the corner minimap once with `,` and once with `/vq routes minimap`; move between two parts of the network and confirm the cyan player marker and nearby count update.
3. Walk toward a caravan marker and confirm the three merchants materialize and travel.
4. Build path, gravel, stone-brick, or plank road segments within the route corridor and watch road quality change over subsequent samples.
5. Bring `8` planks and `2` iron ingots to the broken-wheel caravan, then interact with a merchant.
6. Investigate the false distress call, interact with its caravan, and defeat the three spawned traitors.
7. Force focused cases with `/vq admin routes testevent 1 <event>`; use `clear` to release the route again. Confirm `Missing courier` exposes a named courier and `Storm camp` resolves after staying near the group for `30 seconds`.
8. Obstruct an ordinary group temporarily and confirm it regroups or recovers instead of remaining permanently trapped; leaving the area must keep the abstract route advancing.
9. Use `/vq questmaster` to begin the newly unlocked story test path.
10. Start a survey on a route, mark at least two bends with sneak-use, install it, and confirm the saved line changes on the map.
11. Remove one route through the map's two-click confirmation and confirm the later routes shift into the open slot.
12. Run `/vq routes guild`, inspect the route tooltip specializations/upgrades, and buy an unowned upgrade.
13. Run `/vq routes contracts`, accept a listed offer, provide its cargo, run `contracts supply`, and wait for the assigned route arrival.

Clean up the generated route state with:

```mcfunction
/vq admin routes reset
```

The ordinary complete data reset also removes route runtime entities, but it is broader and should only be used when a full mod reset is intended.
