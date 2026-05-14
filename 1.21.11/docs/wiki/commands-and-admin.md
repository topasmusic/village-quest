# Commands and Admin Tools

All commands are available as `/villagequest ...` and the short alias `/vq ...`.

## Player Commands

### Core Commands

- `/vq questmaster`
  Summon the `Questmaster` near you.

- `/vq journal`
  Open or close the journal.

- `/vq wallet`
  Show your wallet balance.

- `/vq reputation`
  Show your current reputation.

### Quest Commands

- `/vq daily accept`
  Accept the currently pending daily-related offer.

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
- `Shadows on the Trade Road`, relic/special quests, and compass-item quest lines remain solo by design

### Quest Tracker

- `/vq questtracker`
  Toggle the permanent quest tracker.

- `/vq questtracker on`
  Force the tracker on.

- `/vq questtracker off`
  Force the tracker off.

## Admin Commands

These are intended for testing, packmaking, and server administration.

### Daily

- `/vq admin resetdaily [player]`
- `/vq admin nextdaily [player]`
- `/vq admin completedaily [player]`

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
- pilgrim natural spawn cooldown state
- active Village Quest journals, trackers, questmaster sessions, and pilgrim trades
- spawned Questmasters and Pilgrims plus runtime quest-session caches
- late-road runtime entities such as caravan merchants, couriers, spawned hostiles, and traitors

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

## Notes for Testers

- `Story` and `Special` start visible but greyed out.
- `Story` unlocks after the first normal daily completion.
- after a full `4`-chapter story arc, the next story appears only after a real `1 hour` cooldown and the `Story` tab shows a live timer during that pause
- `Special` unlocks after the first real reputation gain.
- `Weekly` cancel now exists and does not reroll a new weekly for the same week.
