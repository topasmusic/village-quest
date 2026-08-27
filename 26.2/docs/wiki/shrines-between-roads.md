# The Shrines Between Roads

`The Shrines Between Roads` is the late trade-network story after `The Empty Caravan`. It becomes available once the Caravan Yard is open, two village routes are installed, and three guild freight contracts have arrived successfully. The Questmaster's `Guild Path` page previews its future tools, blocks, abilities, and exact unlock requirements before the player reaches them.

## Story progression

1. `Stones That Remember` supplies the Cartographer's Lens. Stand inside two connected, inhabited route villages and use it to record their identities. After the second study, the Lens is permanently fitted into the Caravan Ledger instead of remaining a separate inventory tool.
2. `The Broken Heartstone` uses the Ledger's inset Lens to mark a randomly chosen distant region on a named trail map. Once the player comes within `96` blocks, the quest searches the loaded area for dry, nearly level wilderness away from villages, network anchors, old inhabited chunks, known player terrain edits, block entities, and construction traces. It then sinks the authored mossy shrine ruin one block into the ground exactly once. The owner must mine the central Guild Milestone to recover the Cracked Shrine Core; it has wool-like mining time, retains its stone sound, drops no decorative Milestone, and cannot be harvested by another survival player. The temporary map is removed on recovery. Abandoning and accepting the trail again reissues the same unresolved marked site instead of rerolling it. Normal quest text deliberately shows no coordinates: the map is the treasure hunt. The Wayfarer's Sigil is not granted until chapter four and is not required here.
3. `Three Hands of the Guild` requires consultation with a Cartographer, Toolsmith, and Cleric, followed by `16 Amethyst Shards`, `8 Gold Ingots`, `16 Lapis Lazuli`, and the cracked core.
4. `The First Flame` supplies the Wayfarer's Sigil. Craft a dormant Guild Wayshrine from the restored core, one Amethyst Shard, two Gold Ingots, and five Stone Bricks, then bind it at the Homestead with the Sigil. Its dark crystal is the dormant state; binding wakes and illuminates that same crystal.
5. `A Chain of Welcome` asks for two Trusted connected villages and three bound shrines in total.
6. `The Last Relay` requires one new completed freight contract and one new successful route incident. It permanently unlocks the Wayshrine Network project and awards the optional Guild Courier's Satchel; attunement is represented by the awakened shrine network rather than another inventory item.

## Village identities and requests

The Lens assigns a persistent identity from the settlement's villagers and biome: Granary, Forge, Pasture, Apiary, or Archive. After its two introductory studies, sneak-use the upgraded Caravan Ledger in a connected village to read and record the same information. A connected village begins as `Known`. Place a Guild Notice Post there and use it to open its dedicated request board:

- the first request is available immediately, then each village accepts one delivery per configured daily reset;
- two completed requests make the village `Trusted`;
- eight completed requests make it `Allied`;
- every village identity rotates through four different requests without repeating the request it just completed;
- hand-in removes the exact required goods atomically and pays a difficulty-scaled Silvermark reward.

There are twenty requests in total, four for each of the five village identities. Their quantities are intended as substantial deliveries rather than quick inventory checks. Villages progress independently, so several settlements can each accept their own delivery on the same day. The request interface shows the required goods, carried progress, reward, daily availability, current bond, request count, next threshold, and next perk without relying on chat output. The Caravan Ledger's `Village Bonds` page shows every recorded identity, current request, bond, coordinates, and request count.

## Wayshrines

A Wayshrine is a real two-block 3D structure. Hold the Wayfarer's Sigil and use an unbound shrine at the Homestead or inside any recorded connected village, including a `Known` village. The owner's registered Sigil is required only to bind, awaken, and inspect a shrine. Once two shrines are active, normal use opens the destination map with empty hands or any ordinary item.

The weaker village endpoint determines a journey's terms. `Known` villages cost twice the normal distance price, consume two stored charges when charge payment is selected, and apply a ten-minute cooldown. `Trusted` villages use the normal price, one charge, and five minutes; `Allied` villages use the normal price, one charge, and four minutes. The Homestead counts as an `Allied` endpoint, so it never lowers terms earned at the destination village. Other players may use the same physical network without owning a Sigil; their existing guest multiplier is applied after the bond tariff, and they may contribute to or consume the shared charge reserve. Only the registered owner can rename or break a bound shrine in survival.

The destination board uses the same ten direct zoom steps, world-bound coverage, panning, and percentage labels as the Caravan Ledger's main living map. Clicking a destination card recenters the terrain map on that shrine. Its tooltip shows the exact fare, charge consumption, bond tier, and resulting cooldown. The server rejects travel while the player is taking damage, while any route incident is active, when the destination is missing or inactive, or when no safe two-block arrival space exists beside it. Shrine admin fixtures alone bypass route incidents so a seeded test event cannot invalidate the travel test. Wayshrines do not move caravans, complete freight, load remote chunks permanently, or replace surveyed trade routes.

## Guild Path

The Questmaster's `Special` tab includes a `Guild Path` entry. It opens an authored painted landscape rather than a list: drag across Homestead Bay, Surveyor Ridge, Starreach Isle, the guild workshops, Broken Heartstone ruins, Wayshrine Plateau, Guild Village, and Relay Harbor. The map stays at its authored 100% scale, while `Current` returns to the first authoritatively reachable milestone. The Ledger, Compass, Starreach Ring, Seal, Flute, Smoker, Lens, Sigil, Wayshrine, Notice Board, and Courier's Satchel remain clickable landmarks even while locked. Hover labels identify them; clicking a landmark temporarily opens its localized ability and exact requirement, and the card clears as soon as the pointer moves again. Completed and current states come from authoritative quest data rather than a separate battle-pass currency. The painted layer extends beneath the journal frame and title plaque, while the underlying artwork contains no baked text, item art, locks, or progress state, so localization and responsive scaling remain native UI elements.

The active heart is a small floating Magic-Shard cluster. Its rotation, bob, and irregular cyan/violet emissive pulse are entirely client-side; the inactive crystal is dim and static. It adds no server ticker, chunk load, network traffic, or renderer dependency.

## Guild Archive and lost tools

Earned unique guild tools are registered to the player by owner UUID and a server-side serial generation. If one is lost, its Questmaster `Special` tab gains a `Guild Archive` entry. Select the entry twice within ten seconds to confirm the replacement. Advancing the serial makes all older copies inert, including copies later recovered from unloaded chunks or compatible backpack/storage mods.

- the first replacement for each unique tool is free;
- later essential-tool replacements cost `4 Silvermarks`;
- later powerful-relic replacements cost `8 Silvermarks`;
- each tool record can be used once per Minecraft day;
- a recovered Courier's Satchel is always empty;
- Ledger Lens installation and other upgrades remain in server progression rather than being lost with the physical stack.

The Archive also restores the temporary Lens or Cracked Core for free only while its active story chapter still needs it. Before the first shrine is bound, one lost Restored Shrine Core can be restored for free. Later cores are recommissioned for `4 Amethyst Shards`, `2 Gold Ingots`, `8 Lapis Lazuli`, and `1 Chiseled Stone Brick` so additional Wayshrines remain renewable without adding another permanent inventory tool.

## New blocks and tools

All Village Quest crafting recipes share a dedicated tab in the vanilla recipe book. A recipe is discovered once the complete required material set is present in the player's inventory; after discovery it remains in the book permanently and uses vanilla filtering and automatic grid placement.

- `Guild Wayshrine`: bound fast-travel endpoint with active and inactive model states.
- `Guild Notice Post`: opens a localized request interface and fulfils the current local delivery.
- `Emberglass Lantern`: decorative guild road lighting.
- `Guild Milestone`: 3D route marker read with the Wayfarer's Sigil.
- `Cartographer's Lens`: a temporary story tool that records two villages, then becomes a permanent Caravan Ledger upgrade.
- `Wayfarer's Sigil`: binds and activates Wayshrines; sneak-use it to inspect shrine bindings, milestones, and distance to the nearest surveyed route.
- `Guild Courier's Satchel`: one dedicated cargo slot for the active freight contract. Use it to pack matching inventory goods; sneak-use to unload. Supplying a contract consumes its cargo first.

## Testing

Administrators can prepare a route network and receive the shrine blocks, tools, Sigil, and satchel with:

```text
/vq admin shrines testsetup [player]
```

This setup does not skip every story chapter; it supplies a controlled environment for construction, UI, request, and travel testing. Unlike the general route fixture, it clears all seeded incidents so they cannot block Wayshrine travel.

For non-persistent interface previews without changing quest or world state:

```text
/vq admin uitest noticeboard [known|trusted|allied]
/vq admin uitest wayshrine [owner|guest]
```
