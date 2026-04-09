# Core Progression

## Design Structure

`Village Quest` is split into a village-core lane and a later outer-road lane.

Village core:
- `Daily`
- `Weekly`
- `Story`
- `Special`
- `Reputation`
- `Village Projects`

Outer-road side lane:
- `Pilgrim`
- `Roadside Watch`
- dangerous rumor contracts

## Main NPC Roles

### Questmaster

The `Questmaster` is the center of village progression.

He handles:
- daily quests
- weekly quests
- story arcs
- special commissions
- village trust and long-term progress

### Pilgrim

The `Pilgrim` is an outsider.

He handles:
- rotating shop offers
- premium decor wares such as bundles, plaques, reliquaries, and custom-head props
- the one-time `Roadmarks for the Compass` contract
- late dangerous road-work rumors after `Watch Bell`

## Quest Categories

### Daily

Daily quests are:
- random
- village-themed
- drawn from the non-combat daily pool
- reset each day

Visible normal daily pool:
- `The Queen's Gift`
- `Fresh Finery for Your Companions!`
- `Bakehouse Help`
- `Kitchen Supplies`
- `Fuel for the Workshop`
- `A Favor for the Smithy`
- `Wool for the Weaver`
- `Meal from the River`
- `Autumn Harvest`
- `Smelting for the Smithy`
- `New Life in the Stable`
- `Business in the Village`
- `Market Rounds`

Combat dailies still exist in code, but they are not part of the normal Questmaster daily rotation anymore.

### Weekly

Weekly quests are:
- random
- village-themed
- drawn from the non-combat weekly pool
- fixed for the current week once rolled

Visible normal weekly pool:
- `Harvest for the Village`
- `Bakehouse Stock`
- `Smith's Week`
- `Stall and Pasture`
- `Market Week`

Legacy combat weeklies still exist in code, but they are not part of the normal Questmaster weekly rotation anymore.

### Story

The visible core story chain is:

1. `The Failing Harvest`
2. `The Silent Forge`
3. `Market Road Troubles`
4. `Restless Pens`

The story tab now shows only:
- the active story arc
- or the next available one

It no longer dumps the whole story archive at once.

After a full `4`-chapter story arc is completed, the next story is delayed by a real `3 hour` cooldown and the `Story` tab shows that timer directly.

### Special

Special commissions are relic quests and hidden trials.

Current visible relic path:
- `Apiarist's Smoker`
- `Wayfinder's Compass`
- `Merchant's Seal`
- `Shepherd's Flute`

Separate hidden shard path:
- `Whispers of the Shards`

## Reputation Tracks

Current tracks:

- `Farming`
- `Crafting`
- `Animals`
- `Village Trade`
- `Roadside Watch`

The first four are village-core lanes.

`Roadside Watch` is the late side lane tied to:
- `Watch Bell`
- dangerous pilgrim rumors

## Village Projects

Projects are long-term village improvements, not just flat meta bonuses.

Current project roster:
- `Village Ledger`
- `Apiary Charter`
- `Forge Charter`
- `Market Charter`
- `Pasture Charter`
- `Watch Bell`

## Cancel Rules

### Daily cancel

- allowed
- available in the journal
- available in the Questmaster UI

### Weekly cancel

- allowed
- available in the journal
- available in the Questmaster UI
- does not reroll a new weekly for the same week
- only clears accepted state and current weekly progress

### Story, Special, Pilgrim cancel

These do not currently expose a generic cancel system.

That is intentional:
- `Story` is sequential long-form village progression
- `Special` contains staged relic state
- `Pilgrim` rumors now follow a tight `one rumor per day` roadside rhythm

## Random vs Fixed

- `Daily` quests are random from the non-combat daily pool.
- `Weekly` quests are random from the non-combat weekly pool.
- `Pilgrim` dangerous roadside rumors are random from the unlocked rumor pool after `Watch Bell`.
- `Roadmarks for the Compass` is not random; it is a one-time special override.
