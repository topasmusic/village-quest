# Trade Routes and Caravans

Normal entity interactions, including boarding minecarts outside the Overworld, do not trigger route-survey warnings. Explicit route actions remain restricted to the Overworld.

Use the Caravan Ledger to connect real villages, survey roads and manage your personal trade network in Minecraft 26.3.

## Unlocking the Network

Completing `Market Road Troubles` now unlocks the `Market Charter`, the ledger map, and one provisional route. This gives builders a useful preview while the wider road story is still in progress. Provisional routes:

When an existing save already has the `Market Charter` or `Caravan Yard`, the first login after updating grants a missing `Caravan Ledger` automatically. A persistent claim marker and an inventory check make this a one-time compatibility backfill rather than a recurring item source.

- have a capacity of one route
- use smaller two-merchant physical caravans
- share the network-wide passive income limit described below
- do not roll recurring route incidents
- may already use a deliberately confirmed player base as their home node

Complete the six chapters of `The Empty Caravan` to upgrade that preview into the full network. Its finale unlocks:

- the permanent `Caravan Yard` village project
- the `Caravan Ledger` special item
- registration of villages for a personal trade network
- up to `5` persistent trade routes

The route network is player-owned. Story and route events remain solo content and are not shared through quest parties.

## Registering Villages

Stand inside a generated inhabited village **or a player-built settlement founded
through a confirmed Council Seat**, then sneak-use the Caravan Ledger or run
`/vq routes register`. Normal route unlocks and capacity limits still apply.
The first registered village becomes the home hub; later villages become its
route destinations. Duplicate registrations are ignored.

For a player-built village, follow **A Place at the Table**, confirm the furnished
council and choose **Found village** to give it a name. Registration alone does
not make an empty settlement inhabited: add real residents and satisfy the normal
active-village checks. Use the ledger and Guild Notice Post inside the confirmed
building area. Arbitrary groups of villagers or unregistered coordinates do not
become a Village Quest settlement automatically. See [Buildings](kingdom-buildings.md).

Generated villages use their actual structure footprint plus a small margin.
CTOV works through the standard `#minecraft:village` structure tag and is optional.
Empty/zombie or abandoned destinations cannot operate as ordinary active partners;
uncertain unloaded population evidence is not accepted as proof. If your position
cannot be identified, move within the village's confirmed area and check again.

After confirming a Homestead, a single registration at a qualifying destination
creates its route; the Homestead's two-step confirmation is not repeated.
Normal ledger use or `/vq routes` opens the map.

The Caravan Master view uses an open parchment layout without a second inner window, with a centered progress badge in the header. It shows the current route and journey above a progress bar, arrival and road conditions in two columns, cargo, any incident and personal Guild standing. Hover the Master's name for their remark, or a shortened label for its full details. These hints wrap into compact boxes that stay on screen. **View route** opens the existing route overview when available. The route and close actions now sit inside the parchment footer, matching the Journal and Quest Board controls.

### Using a Player Base as the Home Node

The `Market Charter` no longer forces the provisional first node to be inside a generated village. As soon as it grants the Caravan Ledger, stand on solid, dry Overworld ground at your base and run `/vq routes yard`, or sneak-use the ledger where no generated or founded village can be registered. Repeat the action within `30 seconds` to confirm the exact place. The map marks it as a distinct `Homestead Trade Post` rather than pretending that the base is a village. No second unlock quest is required; the later `Caravan Yard` still matters because it expands the network from one route to five and enables incidents.

A Homestead changes only the home node. Destinations must be qualifying inhabited generated villages or properly founded player-built settlements. Arbitrary coordinates and test markers do not qualify. Remove installed routes before relocating the home, so existing journeys cannot silently change their origin.

## The Route Map

The Ledger shows explored terrain, your position, villages, caravans, incidents,
road quality and earnings. Unknown terrain remains parchment; the map observes
loaded terrain and previously cached tiles without loading distant chunks.
It does not predict structures or the world seed.

Drag to pan, scroll or use +/− to zoom, and use Focus to return to your position.
Click the route selector to cycle routes; hover markers for names and details.
Pause, survey, rename or remove a route through its controls. Removal requires
two clicks within 30 seconds; later routes move into the vacant slot.
Renames allow 24 clean characters. Pausing removes the visible group while
retaining its saved journey. Closing the map does not stop simulation.

Five route colours/liveries stay consistent on the map, minimap and caravan.
See [Configuration](configuration.md) for map quality and cache settings.

## Live Corner Minimap

Press `,` or use `/vq routes minimap` after route access is unlocked. Rebind it
in Minecraft Controls under Village Quest. The cyan star is your live position;
other markers show villages, caravans and incidents. The minimap hides while
the full map is open and restores your saved preference quietly on joining.
It is an Overworld surface-navigation aid, not a cave or entity radar.

## Surveying a Real Road

Every route starts as a direct hub-to-village connection, but it can store up to `48` intermediate waypoints. New surveys record X, Y, and Z so tunnels, bridges, underpasses, switchbacks, and paths sharing the same X/Z at different heights stay distinct. Legacy X/Z routes keep their historical surface behavior and are upgraded only when the player deliberately installs a new survey. The home hub and destination village are implicit endpoints and do not need to be marked.

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

Persistent named crews and an optional pack mule make journeys visible near
players. They move along the installed route and return to background simulation
when unobserved. Their saved identities survive unloading and ferry travel.
Ordinary navigation stalls use safe recovery; a marked incident instead needs
its stated objective. No road or building is constructed automatically.

Owners choose FULL, REDUCED or MAP_ONLY in settings. Reduced shows one trader;
Map Only retains journeys, economy and map markers without physical traders or
new physical incidents. [Social reputation](social-reputation.md) explains crew
vulnerability, personal attack consequences and safe freight collection after loss.

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

The surveyed line defines the journey. Mark major bends and detours rather than
expecting traders to adopt an unrelated distant road automatically.

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

Ordinary arrival income now earns one `Silvermark` of budget per four minutes of online simulation time, up to `15 SM` per online hour for the entire network. One or five routes share the same budget, including a provisional route. At most one hour of unspent credit is banked, and arrivals are also limited to `15 SM` in each saved online-hour window. Sleeping, world-day changes, reconnecting and offline catch-up do not replenish it. Offline arrivals generate no new passive currency. Existing trade-office escrow remains collectible when opening or using the ledger.

This is deliberately a long-term infrastructure loop. A route begins uncertain, becomes safer through play, and can eventually flourish if the player maintains it.

## Village Trade Guild

`/vq routes guild` shows the five-rank guild progression. Its score comes from registered routes, sampled road quality, resolved incidents, and completed freight contracts. Rank unlocks stronger contracts and later route investments; it does not provide an infinitely scaling raw-stat bonus.

`/vq routes contracts` shows up to three deterministic daily offers. Recorded destination needs are prioritized, and each matching offer lists the route numbers whose village currently needs that item. A different valid route remains general supply rather than becoming unusable. The workflow is:

1. `/vq routes contracts accept <offer 1-3> <route 1-5>`
2. Carry the listed cargo and run `/vq routes contracts supply`.
3. Keep the assigned route active. Its next arrival within three real-world reset days completes the order.

New contract payouts use cargo value plus a transport fee, with up to `3 SM` for installed-road distance, `2 SM` for a matching specialization, `2 SM` for a Trade Office and `2 SM` for a dangerous route. Bonuses apply to transport, not the entire cargo value. The accepted quote is saved; existing accepted contracts without a quote keep their original reward formula. Freight completion and its separate payout still occur when the passive budget is empty, including offline arrivals.

Freight and Regional Dispatch share a limit of `3` new commissions per player per real daily reset across all routes. The slot is reserved on acceptance; cancellation and expiration do not restore it. Existing accepted work still completes normally. Regional Dispatch remains an eight-item, two-leg shipment; rewards vary with material and combined installed-road distance instead of always paying `12 SM`. Direct village requests keep their existing one-per-village daily reset and use the same material reference plus a local delivery fee. Wheat, bread and hay preserve their ingredient ratios.

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
| `office` | 60 SM | 5 | `2 SM` extra transport payment on new freight contracts |

## Roadwarden Horn

Reaching `200` Monster Hunting reputation awards the functional `Roadwarden Horn`. Its first use per real day posts one road-watch charge, which prevents the next non-tutorial route incident. Further uses point the Wayfinder toward a current incident or report that the roads are quiet.

## Prosperity Services and Liveries

The `2.1.0` economy adds route-targeted Road Patrol, Survey Report, and Emergency Recall services. Forge Prosperity discounts permanent route-upgrade prices, while Road Watch Prosperity reduces recurring incident chance.

Five collectible liveries can be assigned independently to installed routes. Every merchant in that caravan, the full-map route, and the minimap route use the selected color. See [Prosperity and the Village Economy](./prosperity-and-economy.md) for prices and unlock rules.

## Caravan violence and reparation in2.5

Crews and the pack mule now have persistent lives and are physically vulnerable by default. Confirmed personal attacks trigger warnings, measured trust losses and temporary work restrictions; guards defend only against proven attackers within the route leash. A real death interrupts the affected trip, keeps freight in safe saved claims and replaces the lost person only at a later safe departure. Cleanup/ferries/unload preserve living identities. Full rules and the reachable aid path are in [Social reputation](social-reputation.md). Caravan purchase limits now use the configured real Daily reset, with personal standing limits2/4/5/6 and case/ostracism0.

## Dispatch board

The Trust desk can open Regional Dispatch from a nearby Guild Notice Post in
one of your own active connected villages. It explains the missing network,
route or location prerequisites instead of equating neutral standing with an
unlocked service. At least two suitable active village routes are needed.

Regional Dispatch moves cargo source → hub → destination on actual caravan
arrivals. Keep the required routes active and inspect the board for delivery
status. [Living Village Network](living-village-network.md) explains local needs.

## Map and Wayshrine screens

Wayshrine cards show destinations and alternative coin/charge costs. Select a
payment method before travel; this does not recharge the shrine. Normal balance,
charge and cooldown requirements apply. [Wayshrine guide](shrines-between-roads.md).

## Living Kingdoms 2.6

Real direct village routes also support [visiting market traders](kingdom-market.md)
and [supply/friendship agreements](kingdom-diplomacy.md). A contract never constructs
a route or grants duplicate freight rewards. Successfully supplied completed
freight can leave personal project evidence at its canonical participating home
and destination; unrelated, empty and unfinished trips do not.
