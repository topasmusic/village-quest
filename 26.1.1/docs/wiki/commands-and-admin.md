# Commands and Admin Tools

## Player Commands

### Core Commands

- `/questmaster`
  Summon the `Questmaster` near you.

- `/journal`
  Open or close the journal.

- `/wallet`
  Show your wallet balance.

- `/reputation`
  Show your current reputation.

### Quest Commands

- `/dailyquest accept`
  Accept the currently pending daily-related offer.

- `/questtracker`
  Toggle the permanent quest tracker.

- `/questtracker on`
  Force the tracker on.

- `/questtracker off`
  Force the tracker off.

## Admin Commands

These are intended for testing, packmaking, and server administration.

### Daily

- `/questadmin resetdaily [player]`
- `/questadmin nextdaily [player]`
- `/questadmin completedaily [player]`

### Weekly

- `/questadmin resetweekly [player]`
- `/questadmin nextweekly [player]`
- `/questadmin completeweekly [player]`

### Story

- `/questadmin story show [player]`
- `/questadmin story reset [player]`
- `/questadmin story complete [player]`

### Village Projects

- `/questadmin project show [player]`
- `/questadmin project unlock <player> <project>`
- `/questadmin project lock <player> <project>`

Current unlockable project ids:

- `apiary_charter`
- `forge_charter`
- `market_charter`
- `pasture_charter`
- `watch_bell`

### Pilgrim

- `/questadmin pilgrim spawn [player]`
- `/questadmin pilgrim despawn`
- `/questadmin pilgrim rumor unlock [player]`
- `/questadmin pilgrim rumor lock [player]`

### Wallet

- `/questadmin wallet show [player]`
- `/questadmin wallet add <player> <amount> [silvermark|crown]`
- `/questadmin wallet remove <player> <amount> [silvermark|crown]`
- `/questadmin wallet set <player> <amount> [silvermark|crown]`

### Reputation

- `/questadmin reputation show [player]`
- `/questadmin reputation add <player> <track> <amount>`
- `/questadmin reputation set <player> <track> <amount>`

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

- `/questadmin shardcache [player]`
- `/questadmin shardcachetp [player]`

## Useful Testing Patterns

### Force a new daily

```mcfunction
/questadmin resetdaily
/questadmin nextdaily
/questmaster
```

### Force a new weekly

```mcfunction
/questadmin resetweekly
/questadmin nextweekly
/questmaster
```

### Unlock all visible story-core projects quickly

```mcfunction
/questadmin project unlock @s apiary_charter
/questadmin project unlock @s forge_charter
/questadmin project unlock @s market_charter
/questadmin project unlock @s pasture_charter
/questadmin project unlock @s watch_bell
```

### Spawn the Pilgrim with rumor access

```mcfunction
/questadmin pilgrim rumor unlock
/questadmin pilgrim spawn
```

## Notes for Testers

- `Story` and `Special` start visible but greyed out.
- `Story` unlocks after the first normal daily completion.
- `Special` unlocks after the first real reputation gain.
- `Weekly` cancel now exists and does not reroll a new weekly for the same week.
