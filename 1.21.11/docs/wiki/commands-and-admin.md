# Commands and Admin Tools

All commands are available as `/villagequest ...` and the short alias `/vq ...`.

On this legacy line, the older direct roots such as `/questmaster`, `/journal`, `/wallet`, and `/reputation` still exist as compatibility aliases, but `/vq ...` and `/villagequest ...` are the documented forms.

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

### Story

- `/vq admin story show [player]`
- `/vq admin story reset [player]`
- `/vq admin story complete [player]`

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

## Notes for Testers

- `Story` and `Special` start visible but greyed out.
- `Story` unlocks after the first normal daily completion.
- after a full `4`-chapter story arc, the next story appears only after a real `3 hour` cooldown and the `Story` tab shows a live timer during that pause
- `Special` unlocks after the first real reputation gain.
- `Weekly` cancel now exists and does not reroll a new weekly for the same week.
