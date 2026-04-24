# Pilgrim and Roadside Watch

## Pilgrim Role

The `Pilgrim` is not a second questmaster.

The pilgrim exists for:
- rotating road-trader shop offers
- the one-time `Wayfinder` roadmark contract
- late dangerous roadside work after the village is strong enough to spare hands for it

## Pilgrim Arrival and Presence

- when the pilgrim spawns, he tries to path toward the target player for the first `15` seconds
- after that he falls back to normal wandering behavior
- a natural pilgrim stays for about `20` real minutes
- after he leaves, no new natural pilgrim appears for `1` full in-game day
- by night he carries a torch unless he is actively defending himself

## Shop

The pilgrim uses the wallet system.

The current shop mix includes:

- travel and utility bundles such as `Provisions Satchel`, `Road Camp Kit`, and `Village Ledger Desk`
- premium trophy wares such as the plaque series and the `Watch Bell Reliquary`
- decorative custom heads such as barrels, shelves, buckets, furnaces, and plushies

Current shop rules:

- all multi-item bundles now share the same price as the `Provisions Satchel`
- plaque and reliquary wares cost `3 Crowns 5 Silvermarks`
- decorative custom heads cost `5 Crowns`
- each pilgrim can roll at most `1` custom-head decor offer at a time
- plaque and reliquary wares appear only after their matching village-project progress is complete
- `Skeleton Plushie`, `Zombie Plushie`, and `Creeper Plushie` appear only after their matching pilgrim combat contract has been completed once

## Rumor Lane Rules

### Roadmarks override

`Roadmarks for the Compass` is a one-time special contract.

If it is available:
- the pilgrim shows only this contract
- it replaces the normal combat rumor for that day

### Watch Bell combat lane

After `Watch Bell` is unlocked:
- the pilgrim offers exactly `1` dangerous combat rumor per day
- this is a random rumor from the unlocked roadside pool

After `3` completed `Pilgrim` combat rumors:

- the `Questmaster` reveals the locked late story entry `Shadows on the Trade Road`
- that story then takes the road-defense theme back into the `Questmaster` arc flow as a bigger follow-up

## Rumor Lane Colors

The rumor UI now color-codes contract titles:

- overworld roadside work: green
- nether roadside work: red
- end roadside work: purple
- `Roadmarks for the Compass`: blue-green

## One-Time Wayfinder Contract

### `Roadmarks for the Compass`

Type:
- `Pilgrim` special rumor

Objective:
- inscribe the `Wayfinder's Compass` at:
  - `Village`
  - `Pillager Outpost`
  - `Woodland Mansion`
  - `Swamp Hut`

Reward:
- full structure-reading mode set on the compass

## Dangerous Roadside Rumors

### Overworld rumors

#### `Lanterns for the Verge`

- kill `6` skeletons
- lane: overworld
- theme: relight a dark hill-road verge

#### `Smoke Beyond the Milestone`

- kill `3` creepers
- lane: overworld
- theme: make the milestone camp safe again

#### `Tracks in the Dark`

- kill `8` zombies
- lane: overworld
- theme: clear the lower roadside after moonrise

#### `Fangs by the Hedgerow`

- kill `6` spiders
- lane: overworld
- theme: clear the field-road hedgerow

### Nether rumors

#### `Ash on the Pass`

- kill `4` blazes
- kill `3` wither skeletons
- lane: nether
- theme: reopen a dangerous ash pass

#### `Smoke over Blackstone`

- kill `5` magma cubes
- kill `2` ghasts
- lane: nether
- theme: make a blackstone route passable again

### End rumor

#### `Stillness beyond the Gate`

- kill `6` endermen
- kill `3` shulkers
- lane: end
- theme: make the far gate-road reachable instead of feared

## Reward Structure

Rewards are intentionally tiered by danger:

- overworld rumors:
  - lighter money
  - lighter levels
  - lighter `Roadside Watch`

- nether rumors:
  - clearly higher money
  - clearly higher levels
  - clearly higher `Roadside Watch`

- end rumor:
  - highest reward tier in the roadside pool

## Roadside Watch

`Roadside Watch` is now framed as a late side lane, not a fifth main village-career lane.

It becomes relevant through:
- `Watch Bell`
- pilgrim roadside rumors
- reputation rewards tied to dangerous road work
- and later the unlocked `Shadows on the Trade Road` convoy-defense story arc

## Story-Linked Caravan Defense

The late `Shadows on the Trade Road` arc builds directly on the pilgrim rumor lane:

- early chapters gather rumors in villages and then calibrate the `Wayfinder's Compass`
- rescue chapters spawn living caravan survivors in the world at night
- regular mobs focus the caravan first, so the encounter remains a defense check instead of a simple player duel
- the final convoy fight adds elite `Traitor` bandits as the last wave
