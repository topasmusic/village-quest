# Quest Reference

This page is a compact reference for the current visible quest pools and story chapters.

## Current Reward Rules

- Easy, standard, and hard Daily wallet rewards start at `3`, `6`, and `12 Silvermarks`, then follow the stored light/normal/heavy profile.
- Quest experience is paid in bounded level bars instead of a fixed raw-XP amount. A `+3` reward advances the same three bars at vanilla level `10` and level `200`; vanilla determines the larger raw-XP cost at the higher level.
- Normal Daily difficulties grant `1.5`, `3`, and `4.5` bars. Normal Weeklies grant `5.25`, `6`, or `6.75`; Pilgrim combat tiers grant `3`, `4.5`, or `6`; Story rewards scale with their chapter weight and cap at `7.5` bars.
- Light and heavy repeatable profiles still lower or raise the matching bar reward. Fractional payouts are rounded to quarter bars, and the unlocked Forge Charter adds a separate `+0.75` bar to Crafting rewards.
- `/vq daily reroll` replaces an unaccepted Daily once per reset day; three combined reputation Mastery levels raise that to two.
- `Fresh Finery for Your Companions!` requires a tuned sequence of successful collar recolors instead of one interaction.
- Each normal Pilgrim combat roll presents an Overworld-safe contract plus a second alternative, preventing Nether/End-only dead days.
- Story projects apply their new reward bonus consistently to the chapter that completes the project.

## Normal Daily Pool

These are the daily quests currently rolled by the normal `Questmaster` daily system:

### Farming / Food

- `The Queen's Gift`
- `Bakehouse Help`
- `Kitchen Supplies`
- `Meal from the River`
- `Autumn Harvest`

Ripe wheat, carrot, and potato objectives accept both normal block harvesting and compatible right-click-and-replant harvesting. Village Quest verifies that the mature plant was actually reset or replaced before granting progress, so an ordinary right-click or unsuccessful bone-meal use does not count. The same tracking applies to `Harvest for the Village` and `The Failing Harvest: Thin Fields`.

### Crafting

- `Fuel for the Workshop`
- `A Favor for the Smithy`
- `Smelting for the Smithy`

### Animals

- `Fresh Finery for Your Companions!`
- `Wool for the Weaver`
- `New Life in the Stable`

`Fresh Finery for Your Companions!` uses the same light/normal/heavy target tuning as other repeatables; every counted step must be a real successful wolf or cat collar recolor.

### Village / Trade

- `Business in the Village`
- `Market Rounds`

## Legacy Combat Dailies

These still exist in code, but they are not part of the normal visible daily pool:

- `Zombie Cull`
- `Skeleton Patrol`
- `Spider Sweep`
- `Creeper Watch`

## Normal Weekly Pool

These are the weekly quests currently rolled by the normal `Questmaster` weekly system:

- `Harvest for the Village`
- `Bakehouse Stock`
- `Smith's Week`
- `Stall and Pasture`
- `Market Week`

## Legacy Combat Weeklies

These still exist in code, but they are not part of the normal visible weekly pool:

- `Night Watch`
- `Road Warden`

## Visible Story Chapters

### `The Failing Harvest`

1. `Thin Fields`
2. `Quiet Hives`
3. `Bread For The Square`
4. `Market Relief`

### `The Silent Forge`

1. `Cold Hearth`
2. `The Bellows Breathe Again`
3. `Tools For The Hall`
4. `The Master's Edge`

### `Market Road Troubles`

1. `Shuttered Stalls`
2. `Ledger And Notices`
3. `Goods Must Flow`
4. `Market Day Returns`

### `Restless Pens`

1. `Empty Troughs`
2. `Wool Before Weather`
3. `New Pastures`
4. `The Shepherd's Call`

## Late Story Follow-Up

This appears only after `Watch Bell` plus `3` completed `Pilgrim` combat rumors:

### `Shadows on the Trade Road`

1. `Whispers Between Bells`
2. `A Needle for the Night Road`
3. `First Signal`
4. `Holding the Verge`
5. `A Letter for the Guild`
6. `Bell Over the Trade Road`

### `The Empty Caravan`

This solo follow-up appears after `Shadows on the Trade Road` is complete:

1. `The Cart That Returned Alone`
2. `Three Broken Signs`
3. `Ink Beneath Wax`
4. `Names Behind the Seal`
5. `A Caravan Made of Bait`
6. `Roads Between Villages`

Its finale unlocks the permanent `Caravan Yard` project and `Caravan Ledger` trade-route system.

## Current Pilgrim Rumor Pool

### One-time special rumor

- `Roadmarks for the Compass`

### Dangerous overworld rumors

- `Lanterns for the Verge`
- `Smoke Beyond the Milestone`
- `Tracks in the Dark`
- `Fangs by the Hedgerow`

### Dangerous nether rumors

- `Ash on the Pass`
- `Smoke over Blackstone`

### Dangerous end rumor

- `Stillness beyond the Gate`

## Randomization Summary

- `Daily`: random from the normal non-combat daily pool
- `Weekly`: random from the normal non-combat weekly pool
- `Pilgrim` dangerous rumor: random `1` per day after `Watch Bell`
- `Roadmarks for the Compass`: fixed one-time special override, not part of the random rotation
